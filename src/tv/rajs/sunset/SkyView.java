package tv.rajs.sunset;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The background: a painted sky, generated clouds blended over it, a glow on the clouds in the
 * app's color, the cut-out mountains on top, a tint on the peaks, and a shade for readability.
 * Every change is also painted small into {@link Glass#backdrop} for the glass panels.
 */
final class SkyView extends View {
    private static final long FADE_MS = 1100;

    /** The four layers at one size. */
    private static final class Layers {
        final Bitmap mountains, mask, clouds, glow;
        Layers(Bitmap mountains, Bitmap clouds, Bitmap glow) {
            this.mountains = mountains;
            this.mask = mountains.extractAlpha();
            this.clouds = clouds;
            this.glow = glow;
        }
    }

    private final Layers full, small;
    private final Canvas smallCanvas = new Canvas(Glass.backdrop);
    private final Paint sky = new Paint(Paint.DITHER_FLAG);
    private final Paint bitmap = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint cloud = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint glow = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint tintLayer = new Paint();
    private final RectF dst = new RectF();
    private final Matrix sun = new Matrix();
    private final ArgbEvaluator argb = new ArgbEvaluator();

    private final int[] from = new int[6], to = new int[6], now = new int[6];
    private ValueAnimator fade;

    SkyView(Context c) {
        super(c);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap mountains = BitmapFactory.decodeResource(getResources(), R.drawable.sky_mountains, o);
        Bitmap clouds = BitmapFactory.decodeResource(getResources(), R.drawable.sky_clouds, o);
        Bitmap g = BitmapFactory.decodeResource(getResources(), R.drawable.sky_glow, o);
        Bitmap glowMask = g.extractAlpha();
        full = new Layers(mountains, clouds, glowMask);
        small = new Layers(Bitmap.createScaledBitmap(mountains, Glass.W, Glass.H, true),
                Bitmap.createScaledBitmap(clouds, Glass.W, Glass.H, true),
                Bitmap.createScaledBitmap(g, Glass.W, Glass.H, true).extractAlpha());
        g.recycle();

        cloud.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
        cloud.setAlpha(225);
        glow.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        tintLayer.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
        tintLayer.setAlpha(102);

        System.arraycopy(Skies.ALPENGLOW.c, 0, now, 0, 6);
        System.arraycopy(now, 0, to, 0, 6);
        paintBackdrop();
    }

    /** Fades the sky to {@code s}; instant when {@code animate} is false. */
    void show(Skies.Sky s, boolean animate) {
        boolean same = true;
        for (int i = 0; i < 6; i++) same &= to[i] == s.c[i];
        if (same) return;
        if (fade != null) fade.cancel();
        System.arraycopy(now, 0, from, 0, 6);
        System.arraycopy(s.c, 0, to, 0, 6);
        if (!animate) {
            System.arraycopy(to, 0, now, 0, 6);
            invalidate();
            paintBackdrop();
            return;
        }
        fade = ValueAnimator.ofFloat(0f, 1f);
        fade.setDuration(FADE_MS);
        fade.setInterpolator(new DecelerateInterpolator(1.4f));
        fade.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            for (int i = 0; i < 6; i++) now[i] = (Integer) argb.evaluate(t, from[i], to[i]);
            invalidate();
            paintBackdrop();
        });
        fade.start();
    }

    private void paintBackdrop() {
        scene(smallCanvas, Glass.W, Glass.H, small);
        Glass.changed();
    }

    @Override
    protected void onDraw(Canvas cv) {
        scene(cv, getWidth(), getHeight(), full);
    }

    private void scene(Canvas cv, int w, int h, Layers l) {
        dst.set(0, 0, w, h);
        int top = now[0], upper = now[1], middle = now[2], horizon = now[3], hl = now[4], rim = now[5];

        sky.setShader(new LinearGradient(0, 0, 0, h, new int[] {top, upper, middle, horizon, horizon},
                new float[] {0f, .16f, .32f, .46f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(dst, sky);

        // The sun's glow just above the ridge: an ellipse, wider than tall.
        RadialGradient rg = new RadialGradient(0, 0, 1, new int[] {horizon, alpha(horizon, 0x88), alpha(horizon, 0)},
                new float[] {0f, .45f, 1f}, Shader.TileMode.CLAMP);
        sun.setScale(.42f * w, .2f * h);
        sun.postTranslate(.62f * w, .44f * h);
        rg.setLocalMatrix(sun);
        sky.setShader(rg);
        cv.drawRect(dst, sky);
        sky.setShader(null);

        cv.drawBitmap(l.clouds, null, dst, cloud);
        glow.setColor(hl);
        glow.setAlpha(190);
        cv.drawBitmap(l.glow, null, dst, glow);

        cv.drawBitmap(l.mountains, null, dst, bitmap);

        // Light on the peaks in the sky's color, through the mountains' own outline.
        int save = cv.saveLayer(dst, tintLayer);
        tint.setShader(new LinearGradient(0, 0, 0, h, new int[] {rim, rim, upper, upper},
                new float[] {0f, .3f, .8f, 1f}, Shader.TileMode.CLAMP));
        cv.drawBitmap(l.mask, null, dst, tint);
        cv.restoreToCount(save);

        sky.setShader(new LinearGradient(0, 0, 0, h, new int[] {0x40000000, 0, 0, 0x8C000000},
                new float[] {0f, .2f, .6f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(dst, sky);
        sky.setShader(null);
    }

    private static int alpha(int c, int a) {
        return (c & 0x00ffffff) | (a << 24);
    }
}
