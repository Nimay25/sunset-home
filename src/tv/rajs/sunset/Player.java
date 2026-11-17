package tv.rajs.sunset;

import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The now-playing card: whatever Spotify (or another app) is playing on the TV, read from
 * Android's media sessions. Nothing goes to Spotify's servers; the TV's own app reports it.
 */
final class Player extends Glass.Panel {
    static final String SPOTIFY = "com.spotify.tv.android";
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    interface Host {
        float cq(float v);
        void playerFocus(String pkg);
        void playerShown(boolean shown);
    }

    private final Host host;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final MediaSessionManager sessions;
    private final ComponentName listener;
    private final ImageView art;
    private final TextView title, artist, elapsed, total;
    private final Icons wave, playIcon;
    private final Progress progress;
    final View prev, play, next;
    private MediaController controller;
    private ValueAnimator waving;
    private boolean shown, active;
    private String artKey;
    private final Random random = new Random();

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::pick;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata m) {
            render();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState s) {
            render();
        }

        @Override
        public void onSessionDestroyed() {
            ui.post(() -> {
                try {
                    pick(sessions.getActiveSessions(listener));
                } catch (SecurityException e) {
                    setController(null);
                }
            });
        }
    };
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (active && shown) ui.postDelayed(this, 500);
        }
    };

    Player(Context c, Host host) {
        super(c, host.cq(2.4f));
        this.host = host;
        sessions = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(c, NowPlaying.class);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = px(2.1f);
        col.setPadding(pad, pad, pad, px(1.6f));
        addView(col, new FrameLayout.LayoutParams(MATCH, WRAP));

        // Art, title, artist, waveform.
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(px(7.4f), px(7.4f));
        alp.rightMargin = px(1.8f);
        top.addView(art, alp);
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        title = text(2.35f, 0xFFFFFFFF, Typeface.create("sans-serif", Typeface.BOLD));
        artist = text(1.95f, 0xD9FFFFFF, Typeface.create("sans-serif", Typeface.NORMAL));
        // Long titles scroll slowly instead of being cut off.
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setHorizontalFadingEdgeEnabled(true);
        title.setFadingEdgeLength(px(1.5f));
        title.setSelected(true);
        words.addView(title);
        words.addView(artist);
        top.addView(words, new LinearLayout.LayoutParams(0, WRAP, 1f));
        wave = new Icons(c, Icons.WAVE);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(px(2.6f), px(2.6f));
        wlp.leftMargin = px(1f);
        top.addView(wave, wlp);
        col.addView(top);

        // 0:48 ─────●──────── 2:32
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        elapsed = text(1.3f, 0xD9FFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        total = text(1.3f, 0xD9FFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        elapsed.setFontFeatureSettings("tnum");
        total.setFontFeatureSettings("tnum");
        progress = new Progress(c);
        bar.addView(elapsed);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, px(1.9f), 1f);
        plp.leftMargin = px(1.2f);
        plp.rightMargin = px(1.2f);
        bar.addView(progress, plp);
        bar.addView(total);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(MATCH, WRAP);
        blp.topMargin = px(1.5f);
        col.addView(bar, blp);

        // ◀◀  (❚❚)  ▶▶
        LinearLayout controls = new LinearLayout(c);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        prev = button(Icons.PREV, 4.4f, 1.9f, false, () -> transport(-1));
        play = button(Icons.PLAY, 5.4f, 2f, true, () -> transport(0));
        playIcon = (Icons) ((ViewGroup) play).getChildAt(0);
        next = button(Icons.NEXT, 4.4f, 1.9f, false, () -> transport(1));
        LinearLayout.LayoutParams side = new LinearLayout.LayoutParams(px(4.4f), px(4.4f));
        LinearLayout.LayoutParams mid = new LinearLayout.LayoutParams(px(5.4f), px(5.4f));
        mid.leftMargin = px(2.4f);
        mid.rightMargin = px(2.4f);
        controls.addView(prev, side);
        controls.addView(play, mid);
        controls.addView(next, new LinearLayout.LayoutParams(px(4.4f), px(4.4f)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(MATCH, WRAP);
        clp.topMargin = px(1.1f);
        col.addView(controls, clp);

        setVisibility(View.GONE);
        setAlpha(0f);
    }

    private float cq(float v) {
        return host.cq(v);
    }

    private int px(float v) {
        return Math.round(cq(v));
    }

    private TextView text(float size, int color, Typeface face) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, cq(size));
        t.setTextColor(color);
        t.setTypeface(face);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setShadowLayer(cq(.5f), 0, 0, 0x40000000);
        return t;
    }

    /** A control: a glass circle (always for play, on focus for the others) with an icon. */
    private View button(int icon, float size, float iconSize, boolean round, Runnable action) {
        Glass.Panel b = new Glass.Panel(getContext(), cq(size) / 2f);
        b.setFocusable(true);
        b.tint = round ? 0x14FFFFFF : 0;
        if (!round) b.setAlpha(1f);
        Icons i = new Icons(getContext(), icon);
        b.addView(i, new FrameLayout.LayoutParams(px(iconSize), px(iconSize), Gravity.CENTER));
        b.setWillNotDraw(!round);
        b.setOnFocusChangeListener((v, has) -> {
            b.setWillNotDraw(!round && !has);
            b.setLit(has);
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(150).start();
            if (has && controller != null) host.playerFocus(controller.getPackageName());
        });
        b.setOnClickListener(v -> action.run());
        return b;
    }

    // ------------------------------------------------------------------ sessions

    /** Starts following media sessions; quietly shows nothing without notification access. */
    void resume() {
        active = true;
        try {
            sessions.addOnActiveSessionsChangedListener(changed, listener);
            pick(sessions.getActiveSessions(listener));
        } catch (SecurityException e) {
            setController(null);
        }
    }

    void pause() {
        active = false;
        ui.removeCallbacks(tick);
        try {
            sessions.removeOnActiveSessionsChangedListener(changed);
        } catch (RuntimeException ignored) {
        }
        stopWave();
    }

    /** Spotify first, even paused; any other app only while it is playing or paused. */
    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController m : list) {
                if (!hasTitle(m)) continue;
                if (SPOTIFY.equals(m.getPackageName())) {
                    best = m;
                    break;
                }
                int st = state(m);
                if (best == null && (st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_PAUSED)) best = m;
            }
        }
        setController(best);
    }

    private void setController(MediaController c) {
        if (controller != null && (c == null || !c.getSessionToken().equals(controller.getSessionToken()))) {
            controller.unregisterCallback(callback);
        }
        boolean fresh = c != null && (controller == null || !c.getSessionToken().equals(controller.getSessionToken()));
        controller = c;
        if (fresh) c.registerCallback(callback, ui);
        render();
    }

    private static boolean hasTitle(MediaController m) {
        MediaMetadata md = m.getMetadata();
        return md != null && !TextUtils.isEmpty(md.getString(MediaMetadata.METADATA_KEY_TITLE));
    }

    private static int state(MediaController m) {
        PlaybackState s = m.getPlaybackState();
        return s == null ? PlaybackState.STATE_NONE : s.getState();
    }

    private void render() {
        boolean show = controller != null && hasTitle(controller);
        if (show) {
            MediaMetadata md = controller.getMetadata();
            title.setText(md.getString(MediaMetadata.METADATA_KEY_TITLE));
            String a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (TextUtils.isEmpty(a)) a = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            artist.setText(a == null ? "" : a);
            loadArt(md);
            boolean playing = state(controller) == PlaybackState.STATE_PLAYING;
            playIcon.set(playing ? Icons.PAUSE : Icons.PLAY);
            if (playing && active) startWave();
            else stopWave();
            updateProgress();
        }
        if (show != shown) {
            shown = show;
            if (show) {
                setVisibility(View.VISIBLE);
                animate().alpha(1f).setDuration(300).start();
            } else {
                animate().alpha(0f).setDuration(200).withEndAction(() -> setVisibility(View.GONE)).start();
            }
            host.playerShown(show);
        }
        ui.removeCallbacks(tick);
        if (show && active) ui.post(tick);
    }

    boolean showing() {
        return shown;
    }

    private void transport(int which) {
        if (controller == null) return;
        MediaController.TransportControls t = controller.getTransportControls();
        if (which < 0) t.skipToPrevious();
        else if (which > 0) t.skipToNext();
        else if (state(controller) == PlaybackState.STATE_PLAYING) t.pause();
        else t.play();
    }

    private void updateProgress() {
        if (controller == null) return;
        MediaMetadata md = controller.getMetadata();
        PlaybackState s = controller.getPlaybackState();
        long dur = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long pos = 0;
        if (s != null && s.getPosition() >= 0) {
            pos = s.getPosition();
            if (s.getState() == PlaybackState.STATE_PLAYING) {
                pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
            }
        }
        if (dur > 0) pos = Math.min(pos, dur);
        elapsed.setText(time(pos));
        total.setText(dur > 0 ? time(dur) : "--:--");
        progress.set(dur > 0 ? pos / (float) dur : 0f);
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ art and wave

    private void loadArt(MediaMetadata md) {
        Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        String key = md.getString(MediaMetadata.METADATA_KEY_TITLE) + "|" + md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (b != null) {
            art.setImageBitmap(roundArt(b));
            artKey = key;
            return;
        }
        if (key.equals(artKey)) return;
        artKey = key;
        art.setImageDrawable(null);
        String uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if (uri == null) uri = md.getString(MediaMetadata.METADATA_KEY_ART_URI);
        if (uri == null) uri = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        if (uri == null) return;
        final String u = uri, want = key;
        new Thread(() -> {
            Bitmap got = null;
            try {
                if (u.startsWith("http")) {
                    HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    try (InputStream in = conn.getInputStream()) {
                        got = BitmapFactory.decodeStream(in);
                    }
                } else {
                    try (InputStream in = getContext().getContentResolver().openInputStream(Uri.parse(u))) {
                        got = BitmapFactory.decodeStream(in);
                    }
                }
            } catch (Exception ignored) {
            }
            final Bitmap result = got;
            ui.post(() -> {
                if (result != null && want.equals(artKey)) art.setImageBitmap(roundArt(result));
            });
        }, "art").start();
    }

    /** Album art cropped to a rounded square once per track (no live clipping each frame). */
    private Bitmap roundArt(Bitmap src) {
        int size = px(7.4f);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        android.graphics.BitmapShader sh = new android.graphics.BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float k = Math.max(size / (float) src.getWidth(), size / (float) src.getHeight());
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(k, k);
        m.postTranslate((size - src.getWidth() * k) / 2f, (size - src.getHeight() * k) / 2f);
        sh.setLocalMatrix(m);
        p.setShader(sh);
        c.drawRoundRect(new RectF(0, 0, size, size), cq(1.2f), cq(1.2f), p);
        return out;
    }

    private void startWave() {
        if (waving != null) return;
        waving = ValueAnimator.ofFloat(0f, 1f);
        waving.setDuration(180);
        waving.setRepeatCount(ValueAnimator.INFINITE);
        waving.setInterpolator(new LinearInterpolator());
        final float[] target = wave.levels.clone();
        waving.addUpdateListener(a -> {
            for (int i = 0; i < 5; i++) {
                if (Math.abs(wave.levels[i] - target[i]) < .04f) target[i] = .2f + random.nextFloat() * .8f;
                wave.levels[i] += (target[i] - wave.levels[i]) * .25f;
            }
            wave.invalidate();
        });
        waving.start();
    }

    private void stopWave() {
        if (waving != null) {
            waving.cancel();
            waving = null;
        }
        float[] rest = {.35f, .7f, 1f, .7f, .35f};
        System.arraycopy(rest, 0, wave.levels, 0, 5);
        wave.invalidate();
    }

    /** The bar: a bright fill, a dim track, and a glossy dark knob like a glass bead. */
    private final class Progress extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float f;

        Progress(Context c) {
            super(c);
        }

        void set(float v) {
            f = Math.max(0f, Math.min(1f, v));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cy = h / 2f, th = cq(.34f), knob = h * .46f;
            float x = knob + (w - 2 * knob) * f;
            paint.setShader(null);
            paint.setColor(0x40FFFFFF);
            r.set(0, cy - th / 2, w, cy + th / 2);
            c.drawRoundRect(r, th, th, paint);
            paint.setShader(new LinearGradient(0, 0, Math.max(1, x), 0, 0xB3FFFFFF, 0xF2FFFFFF, Shader.TileMode.CLAMP));
            r.set(0, cy - th / 2, x, cy + th / 2);
            c.drawRoundRect(r, th, th, paint);
            paint.setShader(new RadialGradient(x - knob * .35f, cy - knob * .4f, knob * 1.3f,
                    new int[] {0xFF6A6A6A, 0xFF1C1C1E, 0xFF0A0A0B}, new float[] {0f, .55f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(x, cy, knob, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(cq(.09f));
            paint.setColor(0x80FFFFFF);
            c.drawCircle(x, cy, knob, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
