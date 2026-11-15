package app.kuro.anime.core;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Width-based window size classes, using the official Android breakpoints
 * (compact &lt; 600dp, medium &lt; 840dp, expanded ≥ 840dp). Based on the current window,
 * so split-screen and freeform windows are classified correctly.
 */
public enum WindowSizeClass {
    COMPACT, MEDIUM, EXPANDED;

    public static WindowSizeClass fromWidthDp(int widthDp) {
        if (widthDp >= 840) return EXPANDED;
        if (widthDp >= 600) return MEDIUM;
        return COMPACT;
    }

    public static WindowSizeClass of(Context context) {
        Configuration c = context.getResources().getConfiguration();
        return fromWidthDp(c.screenWidthDp);
    }

    public boolean atLeast(WindowSizeClass other) { return ordinal() >= other.ordinal(); }
}
