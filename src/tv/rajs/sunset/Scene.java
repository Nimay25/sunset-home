package tv.rajs.sunset;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Draws the sunset with blends a TV GPU does in one pass each: no offscreen layers, no blends
 * that read the screen back. The tricks (see design/bake_layers.py):
 *
 * - Clouds over a sky are an overlay blend, and with the sky fixed per row that is linear in the
 *   cloud brightness: sky row color G1 + cloud * G2. So the sky is one gradient, the clouds are
 *   the cloud texture multiplied by a second gradient and added on top.
 * - The light on the peaks is linear in the tint color too: base + mult * tint, both baked.
 * - The cloud texture tiles left to right, so drifting is just a shifted shader.
 *
 * One Scene draws the TV at full size; a second one on a worker thread paints the small copy the
 * glass panels blur.
 */
final class Scene {
    private static final int ROWS = 33;
    private static final float SKY_BOTTOM = .55f, MOUNTAIN_TOP = .25f;

    private final Bitmap clouds, glow, base, mult;
    private final BitmapShader cloudTex, glowTex, multTex;
    private final Paint sky = new Paint(Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint front = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint light = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Matrix m = new Matrix(), sunM = new Matrix();
    private final RectF dst = new RectF();
    private final int[] g1 = new int[ROWS], g2 = new int[ROWS];
    private final float[] pos = new float[ROWS];

    /** Full size from resources. */
    Scene(Resources r) {
        this(load(r, R.drawable.sky_clouds), load(r, R.drawable.sky_glow),
                load(r, R.drawable.front_base), load(r, R.drawable.front_mult));
    }

    /** A smaller copy of another scene, for the glass backdrop. */
    Scene(Scene big, int w, int h) {
        this(Bitmap.createScaledBitmap(big.clouds, w, h, true), Bitmap.createScaledBitmap(big.glow, w, h, true),
                Bitmap.createScaledBitmap(big.base, w, h, true), Bitmap.createScaledBitmap(big.mult, w, h, true));
    }

    private Scene(Bitmap clouds, Bitmap glow, Bitmap base, Bitmap mult) {
        this.clouds = clouds;
        this.glow = glow;
        this.base = base;
        this.mult = mult;
        cloudTex = new BitmapShader(clouds, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
        glowTex = new BitmapShader(glow, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
        multTex = new BitmapShader(mult, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        light.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        for (int i = 0; i < ROWS; i++) pos[i] = i / (float) (ROWS - 1);
    }

    private static Bitmap load(Resources r, int id) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeResource(r, id, o);
    }

    private final int[] lastSky = new int[6], lastFront = new int[6];
    private final int[] one = new int[3], two = new int[3];
    private final Matrix fm = new Matrix();
    private int skyW = -1, skyH = -1, frontW = -1, frontH = -1;
    private ComposeShader lightShader;
    private final Rect srcFront = new Rect();

    private static boolean same(int[] a, int[] b) {
        for (int i = 0; i < 6; i++) if (a[i] != b[i]) return false;
        return true;
    }

    /**
     * The whole sky (rows, clouds, cloud glow, sun haze) is ONE nested shader, so the GPU paints it
     * in a single pass: over( screen( add(G1, cloud * G2), glow * highlight ), sun ). Rebuilt only
     * when the palette or the size changes.
     */
    private void buildSky(int w, int h, int[] p) {
        if (w == skyW && h == skyH && same(p, lastSky)) return;
        System.arraycopy(p, 0, lastSky, 0, 6);
        skyW = w;
        skyH = h;
        rows(p);
        Shader g1s = new LinearGradient(0, 0, 0, h, g1, pos, Shader.TileMode.CLAMP);
        Shader g2s = new LinearGradient(0, 0, 0, h, g2, pos, Shader.TileMode.CLAMP);
        Shader cl = new ComposeShader(cloudTex, g2s, PorterDuff.Mode.MULTIPLY);
        Shader withClouds = new ComposeShader(g1s, cl, PorterDuff.Mode.ADD);
        int hl = (p[4] & 0x00FFFFFF) | 0xBE000000;
        Shader glowColor = new ComposeShader(glowTex, new LinearGradient(0, 0, 0, 1, hl, hl, Shader.TileMode.CLAMP),
                PorterDuff.Mode.MULTIPLY);
        sky.setShader(new ComposeShader(withClouds, glowColor, PorterDuff.Mode.SCREEN));
    }

    private final Paint sun = new Paint(Paint.DITHER_FLAG);
    private final int[] lastSun = new int[6];
    private int sunW = -1, sunH = -1;

    /** The sun's haze above the ridge. It stays put while the clouds drift, so it is its own pass. */
    void drawSun(Canvas c, int w, int h, int[] p) {
        if (w != sunW || h != sunH || !same(p, lastSun)) {
            System.arraycopy(p, 0, lastSun, 0, 6);
            sunW = w;
            sunH = h;
            RadialGradient rg = new RadialGradient(0, 0, 1,
                    new int[] {alpha(p[3], 0xBA), alpha(p[3], 0x54), alpha(p[3], 0)},
                    new float[] {0f, .45f, 1f}, Shader.TileMode.CLAMP);
            sunM.setScale(.42f * w, .2f * h);
            sunM.postTranslate(.62f * w, .44f * h);
            rg.setLocalMatrix(sunM);
            sun.setShader(rg);
        }
        c.drawRect(.62f * w - .42f * w, .44f * h - .2f * h, .62f * w + .42f * w, Math.min(SKY_BOTTOM * h, .44f * h + .2f * h), sun);
    }

    /** Height of the sky part for a screen of height h. */
    static int skyHeight(int h) {
        return Math.round(SKY_BOTTOM * h);
    }

    /**
     * Paints the sky without the sun, at the cloud texture's own size (one screen width, seamless
     * left to right), into {@code c}. Painted on a worker whenever the colors change; the TV then
     * only slides this picture each frame.
     */
    void bakeSky(Canvas c, int[] p) {
        int w = clouds.getWidth(), h = clouds.getHeight();
        buildSky(w, h, p);
        m.reset();
        cloudTex.setLocalMatrix(m);
        glowTex.setLocalMatrix(m);
        c.drawRect(0, 0, w, skyHeight(h), sky);
    }

    int bakeWidth() {
        return clouds.getWidth();
    }

    int bakeHeight() {
        return skyHeight(clouds.getHeight());
    }

    private void buildFront(int w, int h, int[] p) {
        if (w == frontW && h == frontH && same(p, lastFront)) return;
        System.arraycopy(p, 0, lastFront, 0, 6);
        frontW = w;
        frontH = h;
        lightShader = new ComposeShader(multTex, new LinearGradient(0, 0, 0, mult.getHeight(),
                new int[] {p[5], p[5], p[1], p[1]}, new float[] {0f, .3f, .8f, 1f}, Shader.TileMode.CLAMP),
                PorterDuff.Mode.MULTIPLY);
        fm.setScale(w / (float) mult.getWidth(), h / (float) mult.getHeight());
        lightShader.setLocalMatrix(fm);
        light.setShader(lightShader);
    }

    /** Sky and mountains together at one size (the small glass backdrop). */
    void draw(Canvas c, int w, int h, int[] p, float drift) {
        drawSky(c, w, h, p, drift);
        drawSun(c, w, h, p);
        drawMountains(c, w, h, p);
    }

    /**
     * The sky for a screen of w x h, painted down to just below the ridge. The clouds slide by
     * {@code drift} screen widths; the gradients and the sun stay put.
     */
    void drawSky(Canvas c, int w, int h, int[] p, float drift) {
        buildSky(w, h, p);
        m.setScale(w / (float) clouds.getWidth(), h / (float) clouds.getHeight());
        m.postTranslate((drift - (float) Math.floor(drift)) * w, 0);
        cloudTex.setLocalMatrix(m);
        glowTex.setLocalMatrix(m);
        c.drawRect(0, 0, w, SKY_BOTTOM * h, sky);
    }

    /** The mountains below their highest peak: base, then the sky's light added on top. */
    void drawMountains(Canvas c, int w, int h, int[] p) {
        drawMountains(c, w, h, p, true);
    }

    void drawMountains(Canvas c, int w, int h, int[] p, boolean withLight) {
        buildFront(w, h, p);
        int top = Math.round(MOUNTAIN_TOP * base.getHeight());
        srcFront.set(0, top, base.getWidth(), base.getHeight());
        dst.set(0, MOUNTAIN_TOP * h, w, h);
        c.drawBitmap(base, srcFront, dst, front);
        if (withLight) c.drawRect(dst, light);
    }

    /** Per row: the overlay of a cloud on that row's sky color, split into G1 + cloud * G2. */
    private void rows(int[] p) {
        int[] stops = {p[0], p[1], p[2], p[3], p[3]};
        float[] at = {0f, .16f, .32f, .46f, 1f};
        for (int i = 0; i < ROWS; i++) {
            float y = pos[i];
            int k = 0;
            while (k < 3 && y > at[k + 1]) k++;
            float t = Math.min(1f, Math.max(0f, (y - at[k]) / (at[k + 1] - at[k])));
            float shade = 1f - (y < .2f ? .25f * (1f - y / .2f) : y > .6f ? .55f * (y - .6f) / .4f : 0f);
            int a = stops[k], b = stops[k + 1];
            for (int ch = 0; ch < 3; ch++) {
                int sh = 16 - ch * 8;
                float v = (((a >> sh) & 255) * (1f - t) + ((b >> sh) & 255) * t) / 255f;
                float pp = v < .5f ? 0f : 2f * v - 1f, qq = v < .5f ? 2f * v : 2f * (1f - v);
                one[ch] = Math.round(255f * clamp((.12f * v + .88f * pp) * shade));
                two[ch] = Math.round(255f * clamp(.88f * qq * shade));
            }
            g1[i] = 0xFF000000 | one[0] << 16 | one[1] << 8 | one[2];
            g2[i] = 0xFF000000 | two[0] << 16 | two[1] << 8 | two[2];
        }
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static int alpha(int c, int a) {
        return (c & 0x00ffffff) | (a << 24);
    }
}
