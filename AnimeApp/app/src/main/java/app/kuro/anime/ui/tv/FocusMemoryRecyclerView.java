package app.kuro.anime.ui.tv;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * Horizontal row for D-pad navigation that returns focus to the item that was focused last,
 * instead of whichever item happens to be geometrically closest.
 */
public class FocusMemoryRecyclerView extends RecyclerView {
    private int lastFocused = 0;

    public FocusMemoryRecyclerView(Context context) { super(context); }

    public FocusMemoryRecyclerView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        int pos = getChildAdapterPosition(child);
        if (pos != NO_POSITION) lastFocused = pos;
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (!hasFocus() && getLayoutManager() != null) {
            View v = getLayoutManager().findViewByPosition(lastFocused);
            if (v != null && v.isFocusable() && v.getVisibility() == VISIBLE) {
                views.add(v);
                return;
            }
        }
        super.addFocusables(views, direction, focusableMode);
    }

    public void resetFocusMemory() { lastFocused = 0; }
}
