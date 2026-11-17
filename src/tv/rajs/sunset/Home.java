package tv.rajs.sunset;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sunset Home, layout 19 "Dock + Drawer" in liquid glass: the time and weather over a mountain
 * sunset, what's playing, a dock of favorites, and a drawer with every app and input. Whatever has
 * the focus repaints the sky with its own sunset.
 */
public final class Home extends Activity implements Player.Host {
    static boolean inFront;

    private static final int APP = 0, INPUT = 1, SETTINGS = 2, ALL = 3, INPUTS = 4;
    private static final String[] DOCK = {
        "com.amazon.firetv.youtube", "com.netflix.ninja", "com.hulu.plus", "com.hbo.hbonow",
        "com.disney.disneyplus", "com.spotify.tv.android",
    };
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    /** One thing that can be opened. */
    private static final class Entry {
        final int kind;
        final String id, label, glyph;
        final int color;
        final Skies.Sky sky;
        final Intent intent;
        final Drawable icon;

        Entry(int kind, String id, String label, String glyph, int color, Skies.Sky sky, Intent intent, Drawable icon) {
            this.kind = kind;
            this.id = id;
            this.label = label;
            this.glyph = glyph;
            this.color = color;
            this.sky = sky;
            this.intent = intent;
            this.icon = icon;
        }
    }

    private int width;
    private SkyView sky;
    private TextView clock, date, dockLabel;
    private Glass.Panel dock, drawer;
    private LinearLayout dockRow, list;
    private View allTile, firstApp, inputsPill, settingsPill, firstInputRow, lastDock;
    private Player player;
    private Weather weather;
    private boolean drawerOpen;
    private Skies.Sky focusedSky;
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.US);
    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
        }
    };

    @Override
    public float cq(float v) {
        return v * width / 100f;
    }

    private int px(float v) {
        return Math.round(cq(v));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        width = getResources().getDisplayMetrics().widthPixels;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        sky = new SkyView(this);
        root.addView(sky, new FrameLayout.LayoutParams(MATCH, MATCH));

        clock = text(9f, Color.WHITE, Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clock.setShadowLayer(cq(1.4f), 0, cq(.25f), 0x55000000);
        clock.setIncludeFontPadding(false);
        root.addView(clock, at(Gravity.TOP | Gravity.START, 4.6f, 2.6f, 0, 0));
        date = text(1.6f, 0xEBFFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        date.setShadowLayer(cq(.8f), 0, cq(.15f), 0x66000000);
        root.addView(date, at(Gravity.TOP | Gravity.START, 5.3f, 13.4f, 0, 0));
        weather = new Weather(this, this);
        root.addView(weather, at(Gravity.TOP | Gravity.START, 5f, 16.6f, 0, 0));

        List<Entry> apps = apps(), inputs = inputs();
        Entry settings = new Entry(SETTINGS, "settings", "Settings", "⚙", 0xff7d8590, Skies.BLUE_HOUR, null, null);
        Entry inputsEntry = new Entry(INPUTS, "inputs", "Inputs", "⇥", 0xff4fb3bf, Skies.TEAL_TANGERINE, null, null);
        Entry all = new Entry(ALL, "all", "All apps", "≡", 0, Skies.ALPENGLOW, null, null);

        // Top right: Inputs and Settings.
        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        inputsPill = pill(inputsEntry);
        settingsPill = pill(settings);
        pills.addView(inputsPill);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(WRAP, WRAP);
        gap.leftMargin = px(1f);
        pills.addView(settingsPill, gap);
        root.addView(pills, at(Gravity.TOP | Gravity.END, 0, 4f, 4f, 0));

        // Right: what's playing.
        player = new Player(this, this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(px(35f), WRAP, Gravity.TOP | Gravity.END);
        plp.setMargins(0, px(10.5f), px(4f), 0);
        root.addView(player, plp);

        // Bottom: the glass dock.
        dock = new Glass.Panel(this, cq(2.8f));
        dock.setElevation(cq(1.2f));
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setPadding(px(1.3f), px(1.3f), px(1.3f), px(1.3f));
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dock.addView(dockRow);
        allTile = dockTile(all);
        dockRow.addView(allTile, new LinearLayout.LayoutParams(px(6.4f), px(6.4f)));
        for (String pkg : DOCK) {
            for (Entry e : apps) {
                if (!e.id.equals(pkg)) continue;
                View t = dockTile(e);
                if (firstApp == null) firstApp = t;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(6.4f), px(6.4f));
                lp.leftMargin = px(1.4f);
                dockRow.addView(t, lp);
            }
        }
        if (firstApp == null) firstApp = allTile;
        lastDock = firstApp;
        root.addView(dock, at(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 5.4f));

        dockLabel = text(1.15f, Color.WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        dockLabel.setShadowLayer(cq(.6f), 0, 0, 0x99000000);
        root.addView(dockLabel, at(Gravity.BOTTOM | Gravity.START, 0, 0, 0, 2.2f));

        // Left: the glass drawer with everything.
        drawer = new Glass.Panel(this, 0);
        drawer.tint = 0x66050710;
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(1.6f), px(2.4f), px(1.6f), px(2f));
        TextView head = text(.95f, 0x99FFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.setText("ALL APPS");
        head.setLetterSpacing(.18f);
        head.setPadding(px(1.1f), 0, 0, px(.8f));
        list.addView(head);
        for (Entry e : apps) list.addView(row(e));
        if (!inputs.isEmpty()) {
            list.addView(separator());
            for (Entry e : inputs) {
                View r = row(e);
                if (firstInputRow == null) firstInputRow = r;
                list.addView(r);
            }
        }
        list.addView(separator());
        list.addView(row(settings));
        TextView credit = text(.72f, 0x73FFFFFF, Typeface.DEFAULT);
        credit.setText("Photo: Marmolada Sunset by Dmitry A. Mottl, CC BY-SA 3.0. Sky recolored. Weather: Open-Meteo.");
        credit.setPadding(px(1.1f), px(1.4f), px(1f), 0);
        list.addView(credit);
        scroll.addView(list, new FrameLayout.LayoutParams(MATCH, WRAP));
        drawer.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(drawer, new FrameLayout.LayoutParams(px(25f), MATCH, Gravity.START));
        drawer.setTranslationX(-cq(25f));
        drawer.setVisibility(View.INVISIBLE);
        list.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        setContentView(root);
        sky.show(Skies.ALPENGLOW, false);
        firstApp.requestFocus();
        updateClock();
    }

    // ---------------------------------------------------------------- entries

    private List<Entry> apps() {
        PackageManager pm = getPackageManager();
        List<Entry> known = new ArrayList<>(), other = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String pkg : Skies.ORDER) {
            Entry e = app(pm, pkg);
            if (e != null && seen.add(pkg)) known.add(e);
        }
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || pkg.equals(Guard.AMAZON) || !seen.add(pkg)) continue;
            Entry e = app(pm, pkg);
            if (e != null) other.add(e);
        }
        Collator collator = Collator.getInstance();
        Collections.sort(other, (a, b) -> collator.compare(a.label, b.label));
        known.addAll(other);
        return known;
    }

    private Entry app(PackageManager pm, String pkg) {
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) return null;
        Skies.Look look = Skies.look(pkg);
        String label;
        Drawable icon;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            label = look != null ? look.label : pm.getApplicationLabel(ai).toString();
            icon = pm.getApplicationIcon(ai);
        } catch (PackageManager.NameNotFoundException ex) {
            return null;
        }
        String glyph = look != null ? look.glyph : label.substring(0, 1).toUpperCase(Locale.US);
        int color = look != null ? look.color : Skies.color(pkg);
        return new Entry(APP, pkg, label, glyph, color, Skies.sky(pkg), launch, icon);
    }

    private List<Entry> inputs() {
        List<Entry> out = new ArrayList<>();
        TvInputManager tv = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tv == null) return out;
        List<TvInputInfo> hdmi = new ArrayList<>();
        for (TvInputInfo in : tv.getTvInputList()) {
            if (in.isPassthroughInput() && !in.isHidden(this)) hdmi.add(in);
        }
        Collator collator = Collator.getInstance();
        Collections.sort(hdmi, (a, b) -> {
            boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
            if (ha != hb) return ha ? -1 : 1;
            return collator.compare(label(a).toString(), label(b).toString());
        });
        for (int i = 0; i < hdmi.size(); i++) {
            TvInputInfo in = hdmi.get(i);
            Skies.Sky s = Skies.INPUTS[i % Skies.INPUTS.length];
            out.add(new Entry(INPUT, in.getId(), label(in).toString(), String.valueOf(i + 1), s.c[2], s, null, null));
        }
        return out;
    }

    private CharSequence label(TvInputInfo in) {
        CharSequence l = in.loadCustomLabel(this);
        return TextUtils.isEmpty(l) ? in.loadLabel(this) : l;
    }

    // ---------------------------------------------------------------- views

    private View dockTile(Entry e) {
        final float radius = cq(1.6f);
        FrameLayout t;
        if (e.icon != null) {
            t = new FrameLayout(this);
            ImageView img = new ImageView(this);
            img.setImageDrawable(e.icon);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            t.addView(img, new FrameLayout.LayoutParams(MATCH, MATCH));
            t.setBackgroundColor(e.color);
            t.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                }
            });
            t.setClipToOutline(true);
        } else {
            Glass.Panel g = new Glass.Panel(this, radius);
            g.tint = 0x26FFFFFF;
            TextView glyph = text(2.8f, Color.WHITE, Typeface.create("sans-serif", Typeface.BOLD));
            glyph.setText(e.glyph);
            glyph.setGravity(Gravity.CENTER);
            g.addView(glyph, new FrameLayout.LayoutParams(MATCH, MATCH));
            t = g;
        }
        t.setId(View.generateViewId());
        t.setFocusable(true);
        final FrameLayout tile = t;
        t.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.13f : 1f).scaleY(has ? 1.13f : 1f)
                    .translationY(has ? -cq(1.1f) : 0).setDuration(170)
                    .setInterpolator(new DecelerateInterpolator()).start();
            v.setElevation(has ? cq(1.2f) : 0);
            tile.setForeground(has ? ring(radius) : null);
            if (tile instanceof Glass.Panel) ((Glass.Panel) tile).setLit(has);
            if (has) {
                lastDock = v;
                focus(e.sky);
                v.post(() -> placeLabel(v, e.label));
            }
        });
        t.setOnClickListener(v -> open(e));
        return t;
    }

    private Drawable ring(float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(radius);
        d.setStroke(Math.max(2, px(.22f)), Color.WHITE);
        return d;
    }

    private View pill(Entry e) {
        Glass.Panel p = new Glass.Panel(this, cq(5f));
        p.setId(View.generateViewId());
        p.setFocusable(true);
        TextView t = text(1.2f, Color.WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setText(e.label);
        t.setPadding(px(1.5f), px(.65f), px(1.5f), px(.65f));
        p.addView(t);
        p.setOnFocusChangeListener((v, has) -> {
            p.setLit(has);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(150).start();
            if (has) {
                focus(e.sky);
                dockLabel.setText("");
            }
        });
        p.setOnClickListener(v -> open(e));
        return p;
    }

    private View row(Entry e) {
        LinearLayout r = new LinearLayout(this);
        r.setId(View.generateViewId());
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setPadding(px(1.1f), px(.45f), px(1.1f), px(.45f));
        final float rad = cq(.45f);
        View badge;
        if (e.icon != null) {
            ImageView img = new ImageView(this);
            img.setImageDrawable(e.icon);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            badge = img;
        } else {
            badge = new View(this);
            badge.setBackground(round(e.color, rad));
        }
        badge.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad);
            }
        });
        badge.setClipToOutline(true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(px(1.9f), px(1.9f));
        slp.rightMargin = px(1.1f);
        r.addView(badge, slp);
        TextView name = text(1.32f, 0xffe4e7f0, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setText(e.label);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));
        r.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? round(0xF2FFFFFF, cq(.9f)) : null);
            name.setTextColor(has ? 0xff111111 : 0xffe4e7f0);
            if (has) focus(e.sky);
        });
        r.setOnClickListener(v -> open(e));
        return r;
    }

    private View separator() {
        View s = new View(this);
        s.setBackgroundColor(0x26FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, Math.max(1, px(.08f)));
        lp.setMargins(px(1.1f), px(.6f), px(1.1f), px(.6f));
        s.setLayoutParams(lp);
        return s;
    }

    private void placeLabel(View tile, String label) {
        dockLabel.setText(label);
        dockLabel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        tile.getLocationOnScreen(at);
        float center = at[0] + tile.getWidth() * tile.getScaleX() / 2f;
        dockLabel.setTranslationX(center - dockLabel.getMeasuredWidth() / 2f);
    }

    private void focus(Skies.Sky s) {
        focusedSky = s;
        sky.show(s, true);
        updateClock();
    }

    // ---------------------------------------------------------------- player host

    @Override
    public void playerFocus(String pkg) {
        focus(Skies.sky(pkg));
        dockLabel.setText("");
    }

    @Override
    public void playerShown(boolean shown) {
        View f = getCurrentFocus();
        if (!shown && f != null && (f == player.prev || f == player.play || f == player.next)) lastDock.requestFocus();
    }

    // ---------------------------------------------------------------- drawer and keys

    private void slide(View v, float x, long ms) {
        ViewPropertyAnimator a = v.animate().translationX(x).setDuration(ms).setInterpolator(new DecelerateInterpolator());
        if (v instanceof Glass.Panel) a.setUpdateListener(u -> v.invalidate());
        a.start();
    }

    private void openDrawer(View target) {
        if (!drawerOpen) {
            drawerOpen = true;
            drawer.setVisibility(View.VISIBLE);
            list.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            slide(drawer, 0, 260);
            slide(clock, cq(21.5f), 260);
            slide(date, cq(21.5f), 260);
            slide(weather, cq(21.5f), 260);
            slide(dock, cq(5f), 260);
            dockLabel.setText("");
        }
        View t = target != null ? target : list.getChildAt(1);
        if (t != null) t.requestFocus();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        allTile.requestFocus();
        list.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        slide(drawer, -cq(25f), 240);
        slide(clock, 0, 240);
        slide(date, 0, 240);
        slide(weather, 0, 240);
        slide(dock, 0, 240);
    }

    private boolean inDock(View f) {
        return f != null && f.getParent() == dockRow;
    }

    private boolean inPlayer(View f) {
        return f != null && (f == player.prev || f == player.play || f == player.next);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        View f = getCurrentFocus();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down) {
                if (drawerOpen) closeDrawer();
                else firstApp.requestFocus();
            }
            return true;
        }
        if (!down) return super.dispatchKeyEvent(e);
        if (drawerOpen) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                closeDrawer();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) return true;
            return super.dispatchKeyEvent(e);
        }
        boolean playerUp = player.showing();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (f == allTile) {
                    openDrawer(null);
                    return true;
                }
                if (f == player.prev) return true;
                if (f == inputsPill) return true;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (f == player.next || f == settingsPill) return true;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (inDock(f)) {
                    (playerUp ? player.play : inputsPill).requestFocus();
                    return true;
                }
                if (inPlayer(f)) {
                    settingsPill.requestFocus();
                    return true;
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (f == inputsPill || f == settingsPill) {
                    (playerUp ? player.play : lastDock).requestFocus();
                    return true;
                }
                if (inPlayer(f)) {
                    lastDock.requestFocus();
                    return true;
                }
                return true;
            default:
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------------------------------------------------------------- opening things

    private void open(Entry e) {
        switch (e.kind) {
            case ALL:
                openDrawer(null);
                return;
            case INPUTS:
                openDrawer(firstInputRow);
                return;
            case INPUT:
                startActivity(new Intent(this, Input.class).putExtra(Input.EXTRA_ID, e.id)
                        .putExtra(Input.EXTRA_NAME, e.label));
                return;
            case SETTINGS:
                Intent tv = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS");
                launch(tv.resolveActivity(getPackageManager()) != null ? tv : new Intent(Settings.ACTION_SETTINGS));
                return;
            default:
                launch(new Intent(e.intent));
        }
    }

    private void launch(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                    ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
        } catch (ActivityNotFoundException | SecurityException ex) {
            Toast.makeText(this, R.string.could_not_open, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (drawerOpen) closeDrawer();
        firstApp.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        inFront = true;
        Guard.arrived(this);
        registerReceiver(tick, new IntentFilter(Intent.ACTION_TIME_TICK));
        updateClock();
        player.resume();
        weather.resume();
        getWindow().getDecorView().post(Guard::shown);
    }

    @Override
    protected void onPause() {
        inFront = false;
        player.pause();
        weather.pause();
        try {
            unregisterReceiver(tick);
        } catch (IllegalArgumentException ignored) {
        }
        super.onPause();
    }

    private void updateClock() {
        Date d = new Date();
        boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
        clock.setText(new SimpleDateFormat(h24 ? "H:mm" : "h:mm", Locale.US).format(d));
        String s = dayFormat.format(d);
        if (focusedSky != null) s += "  ·  " + focusedSky.name + " sky";
        date.setText(s);
    }

    // ---------------------------------------------------------------- helpers

    private TextView text(float size, int color, Typeface face) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, cq(size));
        t.setTextColor(color);
        t.setTypeface(face);
        return t;
    }

    private FrameLayout.LayoutParams at(int gravity, float left, float top, float right, float bottom) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, WRAP, gravity);
        lp.setMargins(px(left), px(top), px(right), px(bottom));
        return lp;
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }
}
