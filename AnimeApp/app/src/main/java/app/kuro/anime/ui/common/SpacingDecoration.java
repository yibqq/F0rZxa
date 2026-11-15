package app.kuro.anime.ui.common;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Even gutters for grids and horizontal rows (RTL-aware via the layout manager). */
public final class SpacingDecoration extends RecyclerView.ItemDecoration {
    private final int space;

    public SpacingDecoration(int spacePx) { this.space = spacePx; }

    @Override
    public void getItemOffsets(@NonNull Rect out, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        RecyclerView.LayoutManager lm = parent.getLayoutManager();
        if (lm instanceof GridLayoutManager) {
            GridLayoutManager g = (GridLayoutManager) lm;
            GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams) view.getLayoutParams();
            int spans = g.getSpanCount();
            int size = lp.getSpanSize();
            if (size == spans) {
                out.set(0, 0, 0, space);
                return;
            }
            int column = lp.getSpanIndex();
            int start = column * space / spans;
            int end = space - (column + 1) * space / spans;
            boolean rtl = parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            out.left = rtl ? end : start;
            out.right = rtl ? start : end;
            out.bottom = space + space / 2;
        } else {
            boolean rtl = parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            int pos = parent.getChildAdapterPosition(view);
            if (pos > 0) {
                if (rtl) out.right = space; else out.left = space;
            }
        }
    }
}
