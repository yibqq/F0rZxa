package app.kuro.anime.ui.common;

import android.content.Context;

import app.kuro.anime.R;

public final class Layouts {
    private Layouts() {}

    /** Whether the main shell uses the side rail (so content must also clear the bottom system bar). */
    public static boolean usesRail(Context c) { return c.getResources().getBoolean(R.bool.use_nav_rail); }

    /** Insets a scrolling list should apply: always the top, plus the bottom when no bottom bar covers it. */
    public static int listInsetSides(Context c) {
        return InsetsHelper.TOP | (usesRail(c) ? InsetsHelper.BOTTOM : 0);
    }

    public static int px(Context c, int dimenRes) { return c.getResources().getDimensionPixelSize(dimenRes); }

    public static int dp(Context c, float dp) { return Math.round(dp * c.getResources().getDisplayMetrics().density); }

    public static int gridMinWidth(Context c) {
        int base = px(c, R.dimen.grid_card_min_width);
        boolean compact = app.kuro.anime.KuroApp.get().settings().compactGrid();
        return compact ? Math.round(base * 0.78f) : base;
    }

    /** Centres content on very wide windows by growing horizontal padding beyond {@code maxDp}. */
    public static void constrainWidth(android.view.View v, int maxDp) {
        final int baseStart = v.getPaddingStart(), baseEnd = v.getPaddingEnd();
        v.addOnLayoutChangeListener((view, l, t, r, bt, ol, ot, or, ob) -> {
            int width = r - l;
            int max = dp(view.getContext(), maxDp);
            int extra = Math.max(0, (width - max) / 2);
            if (view.getPaddingStart() != baseStart + extra) {
                view.post(() -> view.setPaddingRelative(baseStart + extra, view.getPaddingTop(), baseEnd + extra, view.getPaddingBottom()));
            }
        });
    }
}
