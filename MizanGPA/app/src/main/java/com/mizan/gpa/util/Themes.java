package com.mizan.gpa.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
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
        /** The palette's primary color resource (follows light/dark mode). */
        @ColorRes public final int swatch;

        Palette(String id, int name, int overlay, int swatch) {
            this.id = id;
            this.name = name;
            this.overlay = overlay;
            this.swatch = swatch;
        }
    }

    public static final Palette[] PALETTES = {
            new Palette("teal", R.string.theme_teal, 0, R.color.md_primary),
            new Palette("ocean", R.string.theme_ocean, R.style.ThemeOverlay_Mizan_Ocean, R.color.theme_ocean_primary),
            new Palette("violet", R.string.theme_violet, R.style.ThemeOverlay_Mizan_Violet, R.color.theme_violet_primary),
            new Palette("rose", R.string.theme_rose, R.style.ThemeOverlay_Mizan_Rose, R.color.theme_rose_primary),
            new Palette("sunset", R.string.theme_sunset, R.style.ThemeOverlay_Mizan_Sunset, R.color.theme_sunset_primary),
            new Palette("forest", R.string.theme_forest, R.style.ThemeOverlay_Mizan_Forest, R.color.theme_forest_primary),
            new Palette("gold", R.string.theme_gold, R.style.ThemeOverlay_Mizan_Gold, R.color.theme_gold_primary),
            new Palette("graphite", R.string.theme_graphite, R.style.ThemeOverlay_Mizan_Graphite, R.color.theme_graphite_primary),
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

    /**
     * A color attribute of the chosen palette, always in light mode (for the PDF report and
     * notifications, which are not drawn on the app's own background).
     */
    @ColorInt
    public static int lightColor(Context ctx, int attr) {
        Configuration c = new Configuration(ctx.getResources().getConfiguration());
        c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        android.view.ContextThemeWrapper themed = new android.view.ContextThemeWrapper(ctx.createConfigurationContext(c), R.style.Theme_Mizan);
        Settings st = com.mizan.gpa.data.Repository.get(ctx).settings();
        Palette p = find(st.colorTheme);
        if (p.overlay != 0) themed.getTheme().applyStyle(p.overlay, true);
        return com.google.android.material.color.MaterialColors.getColor(themed, attr, 0xFF006A65);
    }

    /**
     * Paints the app logo on a tile: the icon's gauge and bars in white over a gradient of the
     * current palette, so the logo matches whichever color theme is chosen.
     */
    public static void brandTile(android.view.View tile, android.widget.ImageView icon, float radiusDp) {
        Context c = tile.getContext();
        int primary = com.mizan.gpa.util.Ui.attr(c, androidx.appcompat.R.attr.colorPrimary);
        int tertiary = com.mizan.gpa.util.Ui.attr(c, com.google.android.material.R.attr.colorTertiary);
        boolean night = isNight(c);
        // In dark mode the primary is light, so use the deeper container tones for the tile.
        if (night) {
            primary = com.mizan.gpa.util.Ui.attr(c, com.google.android.material.R.attr.colorPrimaryContainer);
            tertiary = com.mizan.gpa.util.Ui.attr(c, com.google.android.material.R.attr.colorTertiaryContainer);
        }
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{primary, com.mizan.gpa.util.Ui.blend(primary, tertiary, 0.55f)});
        g.setCornerRadius(com.mizan.gpa.util.Ui.dp(c, radiusDp));
        tile.setBackground(g);
        tile.setClipToOutline(true);
        icon.setImageResource(R.drawable.ic_launcher_monochrome);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(com.mizan.gpa.util.Ui.attr(c,
                night ? com.google.android.material.R.attr.colorOnPrimaryContainer : com.google.android.material.R.attr.colorOnPrimary)));
        icon.setPadding(0, 0, 0, 0);
        icon.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        // The adaptive-icon artwork sits in the middle two thirds; enlarge it to fill the tile.
        icon.setScaleX(1.35f);
        icon.setScaleY(1.35f);
    }
}
