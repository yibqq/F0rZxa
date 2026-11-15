package app.kuro.anime.ui.common;

import android.content.Context;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Responsive grid: the column count is derived from the RecyclerView's real width and a minimum
 * card width, so it adapts to phones, tablets, split-screen and foldables without fixed layouts.
 */
public final class GridAutoFit {
    private GridAutoFit() {}

    public static GridLayoutManager attach(RecyclerView rv, int minItemWidthPx, int spacingPx) {
        Context c = rv.getContext();
        int estimate = Math.round(c.getResources().getConfiguration().screenWidthDp
                * c.getResources().getDisplayMetrics().density);
        GridLayoutManager glm = new GridLayoutManager(c, spansFor(estimate, minItemWidthPx, spacingPx));
        rv.setLayoutManager(glm);
        rv.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l == or - ol && glm.getSpanCount() > 0 && ol != 0) return;
            int width = (r - l) - v.getPaddingLeft() - v.getPaddingRight();
            int spans = spansFor(width, minItemWidthPx, spacingPx);
            if (spans != glm.getSpanCount()) v.post(() -> glm.setSpanCount(spans));
        });
        return glm;
    }

    public static int spansFor(int widthPx, int minItemWidthPx, int spacingPx) {
        if (widthPx <= 0) return 2;
        return Math.max(2, (widthPx + spacingPx) / (minItemWidthPx + spacingPx));
    }
}
