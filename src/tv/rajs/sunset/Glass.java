package tv.rajs.sunset;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Liquid glass on Android 9, which has no blur effect for views: SkyView paints a small copy of
 * the sky, this class blurs it, and every glass panel draws its own piece of that blurred sky
 * slightly magnified (the lens), then a tint, a specular highlight, a bright rim and a faint
 * red/blue fringe at the edge.
 */
final class Glass {
    /** Size of the blurred backdrop. The screen is 1920x1080, so one pixel here is 8x8 there. */
    static final int W = 240, H = 135;
    static final Bitmap backdrop = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    private static final List<View> panels = new ArrayList<>();
    private static final int[] pixels = new int[W * H], scratch = new int[W * H];

    /** Blurs the freshly painted backdrop and tells every panel to redraw. */
    static void changed() {
        backdrop.getPixels(pixels, 0, W, 0, 0, W, H);
        for (int pass = 0; pass < 3; pass++) {
            box(pixels, scratch, W, H, 4, true);
            box(scratch, pixels, W, H, 4, false);
        }
        backdrop.setPixels(pixels, 0, W, 0, 0, W, H);
        for (View v : panels) v.invalidate();
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
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fringe = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix();
        private final RectF r = new RectF(), e = new RectF();
        private final int[] at = new int[2];
        private final BitmapShader shader = new BitmapShader(backdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float radius;
        int tint = 0x1FFFFFFF;
        boolean lit;

        Panel(Context c, float radius) {
            super(c);
            this.radius = radius;
            setWillNotDraw(false);
            glass.setShader(shader);
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
            lit = on;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), screenW = getResources().getDisplayMetrics().widthPixels;
            float rad = Math.min(radius, Math.min(w, h) / 2f), px = screenW / 1920f;
            getLocationOnScreen(at);
            r.set(0, 0, w, h);

            // The sky behind, blurred and magnified 6% around the panel's center.
            float k = screenW / W;
            m.setScale(k, k);
            m.postTranslate(-at[0], -at[1]);
            m.postScale(1.06f, 1.06f, w / 2f, h / 2f);
            shader.setLocalMatrix(m);
            c.drawRoundRect(r, rad, rad, glass);

            fill.setShader(null);
            fill.setColor(lit ? 0x40FFFFFF : tint);
            c.drawRoundRect(r, rad, rad, fill);

            // Specular light across the top half.
            fill.setShader(new LinearGradient(0, 0, 0, h, new int[] {lit ? 0x40FFFFFF : 0x2EFFFFFF, 0x00FFFFFF, 0x00FFFFFF},
                    new float[] {0f, .55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, fill);
            fill.setShader(null);

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
            rim.setShader(new LinearGradient(0, 0, w, h,
                    new int[] {lit ? 0xF2FFFFFF : 0xA6FFFFFF, 0x26FFFFFF, lit ? 0xCCFFFFFF : 0x66FFFFFF},
                    new float[] {0f, .5f, 1f}, Shader.TileMode.CLAMP));
            e.set(sw / 2, sw / 2, w - sw / 2, h - sw / 2);
            c.drawRoundRect(e, rad, rad, rim);
        }
    }

    private Glass() {}
}
