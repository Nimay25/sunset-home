package tv.rajs.sunset;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LightingColorFilter;
import android.graphics.PorterDuff;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Liquid glass on Android 9, which has no blur effect for views. A worker thread paints a small
 * copy of the sky ({@link Scene}), blurs it, and hands it over; every glass panel draws its piece
 * of it slightly magnified (the lens), then a tint, a highlight, a rim and a red/blue fringe.
 *
 * Smoothness: the UI thread never paints or blurs. Three buffers rotate, so the worker never
 * writes into the one on screen or the one just replaced. Requests coalesce: when the sky changes
 * faster than the worker, it skips straight to the newest sky.
 */
final class Glass {
    /** Size of the blurred backdrop: one pixel here is 8x8 on a 1920x1080 screen. */
    static final int W = 240, H = 135;

    private static final Bitmap[] pool = {
        Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888),
        Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888),
        Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888),
    };
    private static volatile int shown = -1, before = -1;
    private static final List<Panel> panels = new ArrayList<>();
    private static final Handler ui = new Handler(Looper.getMainLooper());

    private static HandlerThread thread;
    private static Handler worker;
    private static Scene small, half;

    /** The sky without the sun, pre-painted at half size; SkyView slides it for the drift. */
    private static final Bitmap[] skyPool = new Bitmap[3];
    private static volatile int skyShown = -1, skyBefore = -1;
    private static final int[] baked = new int[6];
    private static boolean everBaked;
    static Runnable onSky;

    /** The newest baked sky, or null before the first one. */
    static Bitmap sky() {
        int s = skyShown;
        return s < 0 ? null : skyPool[s];
    }

    static int skySlot() {
        return skyShown;
    }
    private static final Object lock = new Object();
    private static final int[] wantPalette = new int[6];
    private static float wantDrift;
    private static boolean pending, queued;
    private static final int[] pixels = new int[W * H], scratch = new int[W * H];

    /** Starts the worker with a small copy of the scene. */
    static void start(Scene full) {
        if (thread != null) return;
        thread = new HandlerThread("glass", android.os.Process.THREAD_PRIORITY_DISPLAY);
        thread.start();
        worker = new Handler(thread.getLooper());
        worker.post(() -> {
            small = new Scene(full, W, H);
            half = new Scene(full, full.bakeWidth(), Math.round(full.bakeWidth() * 9f / 16f));
            for (int i = 0; i < skyPool.length; i++) {
                skyPool[i] = Bitmap.createBitmap(half.bakeWidth(), half.bakeHeight(), Bitmap.Config.ARGB_8888);
            }
        });
    }

    /** Asks for a backdrop of this sky; only the newest request is painted. */
    static void request(int[] palette, float drift) {
        synchronized (lock) {
            System.arraycopy(palette, 0, wantPalette, 0, 6);
            wantDrift = drift;
            pending = true;
            if (queued || worker == null) return;
            queued = true;
        }
        worker.post(Glass::paint);
    }

    private static void paint() {
        int[] p = new int[6];
        float drift;
        synchronized (lock) {
            queued = false;
            if (!pending || small == null) return;
            pending = false;
            System.arraycopy(wantPalette, 0, p, 0, 6);
            drift = wantDrift;
        }
        // Re-bake the sky only when its colors changed (not for drift alone: SkyView slides it).
        int skyDone = -1;
        boolean changed = !everBaked;
        for (int i = 0; i < 6 && !changed; i++) changed = baked[i] != p[i];
        if (changed && half != null) {
            skyDone = 0;
            while (skyDone == skyShown || skyDone == skyBefore) skyDone++;
            half.bakeSky(new Canvas(skyPool[skyDone]), p);
            System.arraycopy(p, 0, baked, 0, 6);
            everBaked = true;
        }

        int slot = 0;
        while (slot == shown || slot == before) slot++;
        Bitmap b = pool[slot];
        small.draw(new Canvas(b), W, H, p, drift);
        b.getPixels(pixels, 0, W, 0, 0, W, H);
        for (int pass = 0; pass < 3; pass++) {
            box(pixels, scratch, W, H, 4, true);
            box(scratch, pixels, W, H, 4, false);
        }
        b.setPixels(pixels, 0, W, 0, 0, W, H);
        final int done = slot, skyNew = skyDone;
        ui.post(() -> {
            if (skyNew >= 0) {
                skyBefore = skyShown;
                skyShown = skyNew;
                if (onSky != null) onSky.run();
            }
            before = shown;
            shown = done;
            for (Panel v : panels) v.invalidate();
        });
    }

    /** One box-blur pass along rows (horizontal) or columns. */
    private static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int len = horizontal ? w : h, lines = horizontal ? h : w, div = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int a = 0, rr = 0, gg = 0, bb = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[index(line, clamp(i, len), w, horizontal)];
                a += p >>> 24; rr += (p >> 16) & 255; gg += (p >> 8) & 255; bb += p & 255;
            }
            for (int i = 0; i < len; i++) {
                out[index(line, i, w, horizontal)] = (a / div) << 24 | (rr / div) << 16 | (gg / div) << 8 | bb / div;
                int add = in[index(line, clamp(i + r + 1, len), w, horizontal)];
                int sub = in[index(line, clamp(i - r, len), w, horizontal)];
                a += (add >>> 24) - (sub >>> 24);
                rr += ((add >> 16) & 255) - ((sub >> 16) & 255);
                gg += ((add >> 8) & 255) - ((sub >> 8) & 255);
                bb += (add & 255) - (sub & 255);
            }
        }
    }

    private static int index(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }

    private static int clamp(int i, int len) {
        return i < 0 ? 0 : (i >= len ? len - 1 : i);
    }

    /** A rounded glass panel that can hold other views. */
    static class Panel extends FrameLayout {
        private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fringe = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final BitmapShader[] shaders = new BitmapShader[pool.length];
        private final ComposeShader[] combined = new ComposeShader[pool.length];
        private LinearGradient sheen;
        private int filterFor = 1;
        private final Matrix m = new Matrix();
        private final RectF r = new RectF(), e = new RectF();
        private final int[] at = new int[2];
        private int cachedW = -1, cachedH = -1;
        private boolean cachedLit;
        float radius;
        int tint = 0x1FFFFFFF;
        boolean lit;

        Panel(Context c, float radius) {
            super(c);
            this.radius = radius;
            setWillNotDraw(false);
            rim.setStyle(Paint.Style.STROKE);
            fringe.setStyle(Paint.Style.STROKE);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Panel.this.radius);
                    o.setAlpha(.5f);
                }
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            panels.add(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            panels.remove(this);
            super.onDetachedFromWindow();
        }

        void setLit(boolean on) {
            if (lit == on) return;
            lit = on;
            invalidate();
        }

        /** Gradients depend only on the size and the lit state, so they are made once. */
        private void gradients(int w, int h) {
            if (w == cachedW && h == cachedH && lit == cachedLit) return;
            cachedW = w;
            cachedH = h;
            cachedLit = lit;
            sheen = new LinearGradient(0, 0, 0, h, new int[] {lit ? 0x40FFFFFF : 0x2EFFFFFF, 0, 0},
                    new float[] {0f, .55f, 1f}, Shader.TileMode.CLAMP);
            highlight.setShader(sheen);
            for (int i = 0; i < combined.length; i++) combined[i] = null;
            rim.setShader(new LinearGradient(0, 0, w, h,
                    new int[] {lit ? 0xF2FFFFFF : 0xA6FFFFFF, 0x26FFFFFF, lit ? 0xCCFFFFFF : 0x66FFFFFF},
                    new float[] {0f, .5f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float screenW = getResources().getDisplayMetrics().widthPixels;
            float rad = Math.min(radius, Math.min(w, h) / 2f), px = screenW / 1920f;
            gradients(w, h);
            r.set(0, 0, w, h);

            // ONE pass for the fill: the blurred sky behind (magnified 6% around the center, the
            // lens), with the top sheen composed on, and the tint applied as a color filter
            // (tint over x = x * (1 - a) + tint * a, exactly what a LightingColorFilter does).
            int color = lit ? 0x40FFFFFF : tint;
            if (color != filterFor) {
                filterFor = color;
                float a = (color >>> 24) / 255f;
                int keep = Math.round(255 * (1 - a));
                int mul = 0xFF000000 | keep << 16 | keep << 8 | keep;
                int add = Math.round(((color >> 16) & 255) * a) << 16 | Math.round(((color >> 8) & 255) * a) << 8
                        | Math.round((color & 255) * a);
                glass.setColorFilter(new LightingColorFilter(mul, add));
            }
            int slot = shown;
            if (slot >= 0) {
                if (shaders[slot] == null) {
                    shaders[slot] = new BitmapShader(pool[slot], Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                }
                if (combined[slot] == null) {
                    combined[slot] = new ComposeShader(shaders[slot], sheen, PorterDuff.Mode.SRC_OVER);
                }
                getLocationOnScreen(at);
                float k = screenW / W;
                m.setScale(k, k);
                m.postTranslate(-at[0], -at[1]);
                m.postScale(1.06f, 1.06f, w / 2f, h / 2f);
                shaders[slot].setLocalMatrix(m);
                glass.setShader(combined[slot]);
                c.drawRoundRect(r, rad, rad, glass);
            } else {
                fill.setColor(color);
                c.drawRoundRect(r, rad, rad, fill);
                c.drawRoundRect(r, rad, rad, highlight);
            }

            // Faint chromatic fringe: warm at the lower right, cool at the upper left.
            float fw = 2.2f * px;
            fringe.setStrokeWidth(fw);
            e.set(fw, fw, w - fw * .2f, h - fw * .2f);
            fringe.setColor(0x38FF6A2A);
            c.drawRoundRect(e, rad, rad, fringe);
            e.set(fw * .2f, fw * .2f, w - fw, h - fw);
            fringe.setColor(0x333D8BFF);
            c.drawRoundRect(e, rad, rad, fringe);

            // Bright rim, strongest at the top left like light catching the edge.
            float sw = (lit ? 3f : 1.6f) * px;
            rim.setStrokeWidth(sw);
            e.set(sw / 2, sw / 2, w - sw / 2, h - sw / 2);
            c.drawRoundRect(e, rad, rad, rim);
        }
    }

    private Glass() {}
}
