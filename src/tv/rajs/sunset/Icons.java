package tv.rajs.sunset;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small line icons drawn in code: media controls, the playing waveform and the weather. */
final class Icons extends View {
    static final int PLAY = 0, PAUSE = 1, PREV = 2, NEXT = 3, WAVE = 4,
            SUN = 10, MOON = 11, PARTLY_DAY = 12, PARTLY_NIGHT = 13, CLOUD = 14, FOG = 15,
            DRIZZLE = 16, RAIN = 17, SNOW = 18, STORM = 19;

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path p = new Path(), q = new Path();
    private final RectF r = new RectF();
    private int type;
    /** Bar heights for {@link #WAVE}, 0..1. */
    final float[] levels = {.35f, .7f, 1f, .7f, .35f};

    Icons(Context c, int type) {
        super(c);
        this.type = type;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(0xFFFFFFFF);
        fill.setColor(0xFFFFFFFF);
    }

    void set(int t) {
        if (type == t) return;
        type = t;
        invalidate();
    }

    void color(int c) {
        stroke.setColor(c);
        fill.setColor(c);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        c.save();
        c.translate((getWidth() - s) / 2f, (getHeight() - s) / 2f);
        stroke.setStrokeWidth(s * .075f);
        fill.setPathEffect(new CornerPathEffect(s * .07f));
        switch (type) {
            case PLAY: tri(c, s, .3f, .18f, .84f, .5f, .3f, .82f); break;
            case PAUSE:
                fill.setPathEffect(null);
                r.set(.24f * s, .18f * s, .41f * s, .82f * s);
                c.drawRoundRect(r, s * .07f, s * .07f, fill);
                r.set(.59f * s, .18f * s, .76f * s, .82f * s);
                c.drawRoundRect(r, s * .07f, s * .07f, fill);
                break;
            case PREV:
                tri(c, s, .5f, .24f, .1f, .5f, .5f, .76f);
                tri(c, s, .9f, .24f, .5f, .5f, .9f, .76f);
                break;
            case NEXT:
                tri(c, s, .1f, .24f, .5f, .5f, .1f, .76f);
                tri(c, s, .5f, .24f, .9f, .5f, .5f, .76f);
                break;
            case WAVE:
                stroke.setStrokeWidth(s * .1f);
                for (int i = 0; i < 5; i++) {
                    float x = s * (.14f + i * .18f), half = s * .42f * levels[i];
                    c.drawLine(x, s / 2f - half, x, s / 2f + half, stroke);
                }
                break;
            case SUN: sun(c, s, .5f, .5f, .17f, .29f, .41f); break;
            case MOON: moon(c, s, .5f, .5f, .3f); break;
            case PARTLY_DAY:
            case PARTLY_NIGHT:
                cloud(s, .1f, .08f);
                c.save();
                c.clipOutPath(grow(s));
                if (type == PARTLY_DAY) sun(c, s, .36f, .34f, .12f, .2f, .28f);
                else moon(c, s, .38f, .34f, .2f);
                c.restore();
                c.drawPath(p, stroke);
                break;
            case CLOUD: cloud(s, 0, 0); c.drawPath(p, stroke); break;
            case FOG:
                for (int i = 0; i < 3; i++) {
                    float y = s * (.32f + i * .18f), a = s * (i == 1 ? .14f : .22f), b = s * (i == 2 ? .7f : .86f);
                    c.drawLine(a, y, b, y, stroke);
                }
                break;
            case DRIZZLE:
            case RAIN:
            case SNOW:
            case STORM:
                cloud(s, 0, -.14f);
                c.drawPath(p, stroke);
                for (int i = 0; i < 3; i++) {
                    float x = s * (.32f + i * .18f), y = s * .76f;
                    if (type == SNOW) c.drawCircle(x, y + s * .04f, s * .045f, fill);
                    else if (type == DRIZZLE) c.drawLine(x, y, x - s * .02f, y + s * .06f, stroke);
                    else if (type == RAIN) c.drawLine(x, y - s * .02f, x - s * .07f, y + s * .14f, stroke);
                }
                if (type == STORM) {
                    q.reset();
                    q.moveTo(.56f * s, .6f * s);
                    q.lineTo(.4f * s, .8f * s);
                    q.lineTo(.52f * s, .8f * s);
                    q.lineTo(.44f * s, .97f * s);
                    q.lineTo(.64f * s, .73f * s);
                    q.lineTo(.52f * s, .73f * s);
                    q.close();
                    c.drawPath(q, fill);
                }
                break;
            default:
        }
        c.restore();
    }

    private void tri(Canvas c, float s, float x1, float y1, float x2, float y2, float x3, float y3) {
        q.reset();
        q.moveTo(x1 * s, y1 * s);
        q.lineTo(x2 * s, y2 * s);
        q.lineTo(x3 * s, y3 * s);
        q.close();
        c.drawPath(q, fill);
    }

    private void sun(Canvas c, float s, float cx, float cy, float core, float in, float out) {
        c.drawCircle(cx * s, cy * s, core * s, stroke);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            c.drawLine((cx + dx * in) * s, (cy + dy * in) * s, (cx + dx * out) * s, (cy + dy * out) * s, stroke);
        }
    }

    private void moon(Canvas c, float s, float cx, float cy, float rad) {
        q.reset();
        q.addCircle(cx * s, cy * s, rad * s, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle((cx + rad * .5f) * s, (cy - rad * .35f) * s, rad * .85f * s, Path.Direction.CW);
        q.op(cut, Path.Op.DIFFERENCE);
        c.drawPath(q, stroke);
    }

    /** A cloud outline in {@link #p}, shifted by (dx, dy) in units of the icon size. */
    private void cloud(float s, float dx, float dy) {
        p.reset();
        Path part = new Path();
        part.addCircle((.38f + dx) * s, (.58f + dy) * s, .15f * s, Path.Direction.CW);
        p.op(part, Path.Op.UNION);
        part.reset();
        part.addCircle((.57f + dx) * s, (.5f + dy) * s, .19f * s, Path.Direction.CW);
        p.op(part, Path.Op.UNION);
        part.reset();
        part.addCircle((.73f + dx) * s, (.61f + dy) * s, .12f * s, Path.Direction.CW);
        p.op(part, Path.Op.UNION);
        part.reset();
        r.set((.24f + dx) * s, (.58f + dy) * s, (.84f + dx) * s, (.73f + dy) * s);
        part.addRoundRect(r, .075f * s, .075f * s, Path.Direction.CW);
        p.op(part, Path.Op.UNION);
    }

    /** The cloud a little larger, so the sun behind it stops short of the outline. */
    private Path grow(float s) {
        Path g = new Path();
        Paint wide = new Paint(stroke);
        wide.setStrokeWidth(stroke.getStrokeWidth() * 3f);
        wide.getFillPath(p, g);
        g.op(p, Path.Op.UNION);
        return g;
    }
}
