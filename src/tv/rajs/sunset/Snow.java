package tv.rajs.sunset;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Gentle snowfall in front of the mountains. Flakes come in depths: near ones are bigger, brighter
 * and faster, far ones small and slow, and all sway a little in a light breeze to the right (the
 * way the clouds drift). One soft dot texture drawn ~80 times, which the GPU batches.
 */
final class Snow extends View {
    private static final int COUNT = 80;
    private final Bitmap dot;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Random rnd = new Random(11);
    private final float[] x = new float[COUNT], y = new float[COUNT], size = new float[COUNT],
            speed = new float[COUNT], sway = new float[COUNT], phase = new float[COUNT], alpha = new float[COUNT];
    private long last;
    private boolean running, seeded;
    private int cut;

    void setCut(int px) {
        cut = px;
    }

    Snow(Context c) {
        super(c);
        dot = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(24, 24, 24, new int[] {0xFFFFFFFF, 0xCCFFFFFF, 0x00FFFFFF},
                new float[] {0f, .35f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(dot).drawCircle(24, 24, 24, p);
    }

    private void seed(int w, int h, int i, boolean anywhere) {
        float depth = rnd.nextFloat();                  // 0 far .. 1 near
        depth = depth * depth;                          // mostly far flakes
        float s = h / 1080f;
        size[i] = (3f + depth * 9f) * s;
        speed[i] = (22f + depth * 70f) * s;
        sway[i] = (6f + rnd.nextFloat() * 16f) * s;
        phase[i] = rnd.nextFloat() * 6.283f;
        alpha[i] = .28f + depth * .55f;
        x[i] = rnd.nextFloat() * w;
        y[i] = anywhere ? rnd.nextFloat() * h : -size[i] * 2f;
    }

    void setRunning(boolean on) {
        running = on;
        last = SystemClock.uptimeMillis();
        if (on) postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        if (!seeded) {
            for (int i = 0; i < COUNT; i++) seed(w, h, i, true);
            seeded = true;
        }
        long t = SystemClock.uptimeMillis();
        float dt = Math.min(100, t - last) / 1000f;
        last = t;
        float wind = 9f * h / 1080f, secs = t / 1000f;
        for (int i = 0; i < COUNT; i++) {
            if (running) {
                y[i] += speed[i] * dt;
                x[i] += wind * dt * (.5f + size[i] / (12f * h / 1080f));
                if (y[i] > h + size[i] * 2f) seed(w, h, i, false);
                if (x[i] > w + 20) x[i] -= w + 40;
            }
            float sx = x[i] + (float) Math.sin(secs * .7f + phase[i]) * sway[i];
            if (sx < cut) continue;
            float half = size[i] * 1.2f;
            r.set(sx - half, y[i] - half, sx + half, y[i] + half);
            paint.setAlpha(Math.round(255 * alpha[i]));
            c.drawBitmap(dot, null, r, paint);
        }
        if (running) postInvalidateOnAnimation();
    }
}
