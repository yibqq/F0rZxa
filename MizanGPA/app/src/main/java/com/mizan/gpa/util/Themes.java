package com.mizan.gpa.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.ColorInt;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import com.mizan.gpa.R;
import com.mizan.gpa.data.Settings;

/** Color palettes. "teal" is the base theme; the others are Material 3 schemes generated from a seed color. */
public final class Themes {
    public static final class Palette {
        public final String id;
        @StringRes public final int name;
        @StyleRes public final int overlay;
        @ColorInt public final int swatch;

        Palette(String id, int name, int overlay, int swatch) {
            this.id = id;
            this.name = name;
            this.overlay = overlay;
            this.swatch = swatch;
        }
    }

    public static final Palette[] PALETTES = {
            new Palette("teal", R.string.theme_teal, 0, 0xFF0B7A74),
            new Palette("ocean", R.string.theme_ocean, R.style.ThemeOverlay_Mizan_Ocean, 0xFF2563EB),
            new Palette("violet", R.string.theme_violet, R.style.ThemeOverlay_Mizan_Violet, 0xFF7C3AED),
            new Palette("rose", R.string.theme_rose, R.style.ThemeOverlay_Mizan_Rose, 0xFFE11D48),
            new Palette("sunset", R.string.theme_sunset, R.style.ThemeOverlay_Mizan_Sunset, 0xFFEA580C),
            new Palette("forest", R.string.theme_forest, R.style.ThemeOverlay_Mizan_Forest, 0xFF16A34A),
            new Palette("gold", R.string.theme_gold, R.style.ThemeOverlay_Mizan_Gold, 0xFFB8860B),
            new Palette("graphite", R.string.theme_graphite, R.style.ThemeOverlay_Mizan_Graphite, 0xFF475569),
    };

    private Themes() {}

    public static Palette find(String id) {
        for (Palette p : PALETTES) if (p.id.equals(id)) return p;
        return PALETTES[0];
    }

    public static boolean isNight(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Applies the chosen palette and pure-black overlay; call before setContentView. */
    public static void apply(Activity a, Settings st) {
        if (!st.dynamicColor) {
            Palette p = find(st.colorTheme);
            if (p.overlay != 0) a.getTheme().applyStyle(p.overlay, true);
        }
        if (st.pureBlack && isNight(a)) a.getTheme().applyStyle(R.style.ThemeOverlay_Mizan_PureBlack, true);
    }

    /** Everything that requires recreating screens when it changes. */
    public static String styleKey(Settings st) {
        return st.colorTheme + "|" + st.pureBlack + "|" + st.dynamicColor + "|" + st.fontId + "|" + st.fontScale;
    }
}
