package tv.rajs.sunset;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Current weather for Columbia, SC from Open-Meteo (free, no account, no key). The last answer is
 * kept so the pill shows at once after a reboot; it refreshes every 20 minutes while home is open.
 */
final class Weather extends Glass.Panel {
    private static final String URL_NOW = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=34.0007&longitude=-81.0348"
            + "&current=temperature_2m,weather_code,is_day"
            + "&daily=temperature_2m_max,temperature_2m_min"
            + "&temperature_unit=fahrenheit&timezone=America%2FNew_York&forecast_days=1";
    private static final long STALE_MS = 20 * 60 * 1000L;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final Icons icon;
    private final TextView temp, words, range;
    private final Runnable refresh = this::refresh;
    private boolean active, loading;

    Weather(Context c, Player.Host host) {
        super(c, host.cq(2f));
        prefs = c.getSharedPreferences("weather", Context.MODE_PRIVATE);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int h = Math.round(host.cq(1.4f)), v = Math.round(host.cq(.75f));
        row.setPadding(h, v, Math.round(host.cq(1.7f)), v);
        icon = new Icons(c, Icons.SUN);
        int s = Math.round(host.cq(2.3f));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(s, s);
        ilp.rightMargin = Math.round(host.cq(.9f));
        row.addView(icon, ilp);
        temp = text(c, host.cq(1.75f), 0xFFFFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        words = text(c, host.cq(1.35f), 0xE6FFFFFF, Typeface.DEFAULT);
        range = text(c, host.cq(1.2f), 0xA6FFFFFF, Typeface.DEFAULT);
        row.addView(temp);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.leftMargin = Math.round(host.cq(.9f));
        row.addView(words, wl);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
        rl.leftMargin = Math.round(host.cq(1.1f));
        row.addView(range, rl);
        addView(row);
        setVisibility(View.GONE);
        String cached = prefs.getString("json", null);
        if (cached != null) show(cached);
    }

    private static TextView text(Context c, float px, int color, Typeface face) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setIncludeFontPadding(false);
        return t;
    }

    void resume() {
        active = true;
        long age = System.currentTimeMillis() - prefs.getLong("at", 0);
        ui.removeCallbacks(refresh);
        if (age >= STALE_MS) refresh();
        else ui.postDelayed(refresh, STALE_MS - age);
    }

    void pause() {
        active = false;
        ui.removeCallbacks(refresh);
    }

    private void refresh() {
        ui.removeCallbacks(refresh);
        if (active) ui.postDelayed(refresh, STALE_MS);
        if (loading) return;
        loading = true;
        new Thread(() -> {
            String body = null;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(URL_NOW).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "SunsetHome/1.0");
                try (InputStream in = conn.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                    body = out.toString("UTF-8");
                }
            } catch (Exception ignored) {
            }
            final String got = body;
            ui.post(() -> {
                loading = false;
                if (got != null && show(got)) {
                    prefs.edit().putString("json", got).putLong("at", System.currentTimeMillis()).apply();
                }
            });
        }, "weather").start();
    }

    private boolean show(String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONObject cur = o.getJSONObject("current");
            JSONObject day = o.getJSONObject("daily");
            int code = cur.getInt("weather_code");
            boolean isDay = cur.optInt("is_day", 1) == 1;
            temp.setText(String.format(Locale.US, "%d°", Math.round(cur.getDouble("temperature_2m"))));
            words.setText(words(code));
            range.setText(String.format(Locale.US, "H %d°  L %d°",
                    Math.round(day.getJSONArray("temperature_2m_max").getDouble(0)),
                    Math.round(day.getJSONArray("temperature_2m_min").getDouble(0))));
            icon.set(icon(code, isDay));
            setVisibility(View.VISIBLE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** WMO weather codes, as Open-Meteo reports them. */
    private static String words(int c) {
        if (c == 0) return "Clear";
        if (c == 1) return "Mostly clear";
        if (c == 2) return "Partly cloudy";
        if (c == 3) return "Cloudy";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Showers";
        if (c == 85 || c == 86) return "Snow showers";
        if (c >= 95) return "Thunderstorms";
        return "—";
    }

    private static int icon(int c, boolean day) {
        if (c == 0) return day ? Icons.SUN : Icons.MOON;
        if (c == 1 || c == 2) return day ? Icons.PARTLY_DAY : Icons.PARTLY_NIGHT;
        if (c == 3) return Icons.CLOUD;
        if (c == 45 || c == 48) return Icons.FOG;
        if (c >= 51 && c <= 57) return Icons.DRIZZLE;
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return Icons.RAIN;
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return Icons.SNOW;
        if (c >= 95) return Icons.STORM;
        return Icons.CLOUD;
    }
}
