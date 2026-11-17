package tv.rajs.sunset;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

/**
 * The living background, built for a TV GPU at 60 fps (measured on the TV, see the README):
 *
 * - The sky with its clouds and glow is pre-painted on a worker thread (Glass) at half size,
 *   only when the colors change. Each frame the GPU just slides that seamless picture for the
 *   drifting clouds: one plain textured pass.
 * - The sun's haze is one gradient pass that stays put.
 * - The mountains are drawn sharp from two baked layers (Scene).
 *
 * The palette eases to each app's sky; the clouds cross the screen in 150 s. Everything stops
 * when an app opens.
 */
final class SkyView extends View implements Choreographer.FrameCallback {
    private static final long FADE_MS = 900;
    private static final float DRIFT_PER_MS = 1f / 150_000f;
    private static final long GLASS_EVERY_MS = 350;

    private final Scene scene;
    private final Paint skyPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final BitmapShader[] skyShaders = new BitmapShader[3];
    private final Matrix m = new Matrix();
    private final int[] from = new int[6], to = new int[6], now = new int[6];
    private long fadeStart = -1, lastFrame, lastGlass;
    private float drift;
    private boolean running, posted;
    /** Pixels on the left covered by the open drawer: not painted (its glass shows the blurred copy). */
    private int cut;

    void setCut(int px) {
        if (cut == px) return;
        cut = px;
        invalidate();
    }

    SkyView(Context c) {
        super(c);
        scene = new Scene(getResources());
        Glass.start(scene);
        Glass.onSky = this::invalidate;
        System.arraycopy(Skies.ALPENGLOW.c, 0, now, 0, 6);
        System.arraycopy(now, 0, to, 0, 6);
        Glass.request(now, 0f);
    }

    /** Eases the sky to {@code s}; instant when {@code animate} is false. */
    void show(Skies.Sky s, boolean animate) {
        boolean same = true;
        for (int i = 0; i < 6; i++) same &= to[i] == s.c[i];
        if (same) return;
        System.arraycopy(now, 0, from, 0, 6);
        System.arraycopy(s.c, 0, to, 0, 6);
        if (animate) {
            fadeStart = SystemClock.uptimeMillis();
        } else {
            fadeStart = -1;
            System.arraycopy(to, 0, now, 0, 6);
            Glass.request(now, drift);
            invalidate();
        }
        tick();
    }

    /** Animates while home is in front; stops when an app opens so it costs nothing then. */
    void setRunning(boolean on) {
        running = on;
        lastFrame = SystemClock.uptimeMillis();
        if (on) tick();
    }

    private void tick() {
        if (posted) return;
        posted = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        posted = false;
        long t = SystemClock.uptimeMillis();
        long dt = Math.min(100, t - lastFrame);
        lastFrame = t;
        if (running) drift += dt * DRIFT_PER_MS;
        boolean fading = fadeStart >= 0;
        if (fading) {
            float x = Math.min(1f, (t - fadeStart) / (float) FADE_MS);
            float e = 1f - (1f - x) * (1f - x) * (1f - x);   // ease out
            for (int i = 0; i < 6; i++) now[i] = mix(from[i], to[i], e);
            if (x >= 1f) fadeStart = -1;
        }
        invalidate();
        // The worker re-paints the sky on every step of a fade, and the glass a few times a second.
        if (fading || t - lastGlass >= GLASS_EVERY_MS) {
            lastGlass = t;
            Glass.request(now, drift);
        }
        if (running || fadeStart >= 0) tick();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (cut > 0) c.clipRect(cut, 0, w, h);
        Bitmap b = Glass.sky();
        int slot = Glass.skySlot();
        if (b != null) {
            if (skyShaders[slot] == null) {
                skyShaders[slot] = new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
            }
            float k = w / (float) b.getWidth();
            m.setScale(k, k);
            m.postTranslate((drift - (float) Math.floor(drift)) * w, 0);
            skyShaders[slot].setLocalMatrix(m);
            skyPaint.setShader(skyShaders[slot]);
            c.drawRect(0, 0, w, Scene.skyHeight(h), skyPaint);
        }
        scene.drawSun(c, w, h, now);
        scene.drawMountains(c, w, h, now);
    }

    private static int mix(int a, int b, float t) {
        int r = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            int x = (a >> sh) & 255, y = (b >> sh) & 255;
            r |= Math.round(x + (y - x) * t) << sh;
        }
        return r;
    }
}
