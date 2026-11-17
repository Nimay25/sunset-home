package tv.rajs.sunset;

import android.graphics.Color;

import java.util.HashMap;
import java.util.Map;

/** One sunset per app, input and menu. Colors: sky top, upper, middle, horizon, cloud glow, light on the peaks. */
final class Skies {
    static final class Sky {
        final String name;
        final int[] c;
        Sky(String name, int... c) {
            this.name = name;
            this.c = c;
        }
    }

    /** What a tile looks like: brand color, text color on it, glyph, and a friendly name. */
    static final class Look {
        final String label, glyph;
        final int color, ink;
        Look(String label, int color, int ink, String glyph) {
            this.label = label;
            this.color = color;
            this.ink = ink;
            this.glyph = glyph;
        }
    }

    static final Sky ALPENGLOW = new Sky("Alpenglow", 0xff1e2350, 0xff5a3f8a, 0xffe0607e, 0xffffb36b, 0xffff7a6b, 0xffff9a6b);
    static final Sky BLUE_HOUR = new Sky("Blue Hour", 0xff070b18, 0xff1d2742, 0xff4c5b85, 0xffaab6d6, 0xff8696c4, 0xff8a9bc8);
    static final Sky TEAL_TANGERINE = new Sky("Teal and Tangerine", 0xff04202a, 0xff12606e, 0xffff8a5c, 0xffffd2a1, 0xffff9e70, 0xffff9a6b);
    static final Sky[] INPUTS = {
        new Sky("Neon", 0xff04021a, 0xff2b1fb8, 0xffff2fa8, 0xffffcf6b, 0xffff4fd0, 0xff6b5cff),
        new Sky("Steel Dawn", 0xff0c1420, 0xff34506f, 0xff9cb4cc, 0xffffe0bf, 0xffd6e4f2, 0xffb8cde2),
        new Sky("Copper Dusk", 0xff1a0b05, 0xff6b2f12, 0xffd9773b, 0xffffe1b0, 0xffffae6b, 0xffe8955a),
    };

    private static final Map<String, Sky> SKY = new HashMap<>();
    private static final Map<String, Look> LOOK = new HashMap<>();

    /** Packages in the order they appear: dock picks come from the front of this list. */
    static final String[] ORDER = {
        "com.amazon.firetv.youtube", "com.netflix.ninja", "com.hulu.plus", "com.hbo.hbonow",
        "com.disney.disneyplus", "com.spotify.tv.android", "com.amazon.firebat", "com.peacock.peacockfiretv",
        "com.cbs.ott", "com.philo.philo", "com.roku.web.trc", "com.apple.airplay.amazon.airplay",
        "com.amazon.bueller.music",
    };

    static {
        app("com.amazon.firetv.youtube", "YouTube", 0xffe5231b, Color.WHITE, "Y",
                new Sky("Ember", 0xff14030a, 0xff6e0b1a, 0xffe5231b, 0xffffb347, 0xffff4a2e, 0xffff5a3a));
        app("com.netflix.ninja", "Netflix", 0xffc3121c, Color.WHITE, "N",
                new Sky("Crimson Night", 0xff050003, 0xff3a0009, 0xffb20710, 0xffff6f4f, 0xffe50914, 0xffc3121c));
        app("com.amazon.firebat", "Prime Video", 0xff1f8fff, Color.WHITE, "P",
                new Sky("Glacier", 0xff020d1f, 0xff0a3d75, 0xff1f8fff, 0xff9fe8ff, 0xff59c2ff, 0xff6fc8ff));
        app("com.hulu.plus", "Hulu", 0xff1ce783, 0xff062114, "H",
                new Sky("Aurora", 0xff010f12, 0xff06463f, 0xff1ce783, 0xffe6ff8a, 0xff39ffa0, 0xff3df2a0));
        app("com.hbo.hbonow", "Max", 0xff2f3dff, Color.WHITE, "M",
                new Sky("Ultraviolet", 0xff08061f, 0xff2a1d8f, 0xff6a5cff, 0xffff9ad5, 0xff8f7dff, 0xff7a6bff));
        app("com.disney.disneyplus", "Disney+", 0xff1b44c8, Color.WHITE, "D+",
                new Sky("Starlit Gold", 0xff030828, 0xff13308f, 0xff4f74ff, 0xffffd98a, 0xffffd36b, 0xff7f9bff));
        app("com.peacock.peacockfiretv", "Peacock", 0xff16161a, Color.WHITE, "Pk",
                new Sky("Peacock Plume", 0xff050a1a, 0xff0b5568, 0xff5b3ccf, 0xffffc94a, 0xff2fd6c9, 0xff38c9c0));
        app("com.cbs.ott", "Paramount+", 0xff0064ff, Color.WHITE, "P+",
                new Sky("Midnight Summit", 0xff01041a, 0xff0b1e66, 0xff3456c9, 0xffe6ecff, 0xffc7d3ff, 0xff9fb4ff));
        app("com.philo.philo", "Philo", 0xff6a3bd1, Color.WHITE, "Ph",
                new Sky("Lilac Haze", 0xff1a0f2e, 0xff5b3f9a, 0xffc58cf0, 0xffffe3f2, 0xffd9a8ff, 0xffc99af0));
        app("com.roku.web.trc", "The Roku Channel", 0xff6c1d9e, Color.WHITE, "R",
                new Sky("Grape and Peach", 0xff14021e, 0xff4d0f73, 0xffa845c9, 0xffffb07a, 0xffc867ff, 0xffb65ad8));
        app("com.spotify.tv.android", "Spotify", 0xff1ed760, 0xff062114, "S",
                new Sky("Emerald Dusk", 0xff03100a, 0xff0b4429, 0xff1db954, 0xffffd36e, 0xff5cf58a, 0xff2fe070));
        app("com.apple.airplay.amazon.airplay", "AirPlay", 0xffdfe2ea, 0xff111111, "A",
                new Sky("Cotton Candy", 0xff233063, 0xff7d6fc4, 0xfff59bbd, 0xfffff0d1, 0xffffc2dc, 0xfff7b0cc));
        app("com.amazon.bueller.music", "Amazon Music", 0xff25d1da, 0xff04181a, "♪",
                new Sky("Lagoon", 0xff021418, 0xff0b4f5c, 0xff25d1da, 0xfffff0b8, 0xff6ff3ff, 0xff5fe3ea));
    }

    private static void app(String pkg, String label, int color, int ink, String glyph, Sky sky) {
        LOOK.put(pkg, new Look(label, color, ink, glyph));
        SKY.put(pkg, sky);
    }

    static Look look(String pkg) {
        return LOOK.get(pkg);
    }

    /** The app's own sunset, or one made up from its name so every app still gets a sky. */
    static Sky sky(String pkg) {
        Sky s = SKY.get(pkg);
        if (s != null) return s;
        float h = Math.abs(pkg.hashCode() % 360);
        return new Sky("Wildcard",
                hsv(h, .7f, .1f), hsv(h, .75f, .42f), hsv(h, .7f, .88f), hsv(h + 30, .4f, 1f),
                hsv(h, .55f, 1f), hsv(h, .5f, 1f));
    }

    static int color(String pkg) {
        Look l = LOOK.get(pkg);
        return l != null ? l.color : sky(pkg).c[2];
    }

    private static int hsv(float h, float s, float v) {
        return Color.HSVToColor(new float[] {(h % 360 + 360) % 360, s, v});
    }

    private Skies() {}
}
