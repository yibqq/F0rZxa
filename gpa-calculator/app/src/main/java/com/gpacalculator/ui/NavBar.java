package com.gpacalculator.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gpacalculator.R;
import com.gpacalculator.ui.util.Motion;
import com.gpacalculator.ui.util.SystemInsets;

/**
 * Overlay navigation bar. Transparent while the large title is visible; once the title scrolls
 * under it, a translucent background, hairline and inline title fade in (UIKit large-title behaviour).
 */
final class NavBar {
    final View bar;
    final TextView title;
    final TextView action;
    final View back;
    private final View background;
    private final View line;
    private final View content;
    private final int contentHeight;
    private boolean collapsed;

    NavBar(View bar) {
        this.bar = bar;
        background = bar.findViewById(R.id.nav_bg);
        line = bar.findViewById(R.id.nav_line);
        content = bar.findViewById(R.id.nav_content);
        title = bar.findViewById(R.id.nav_title);
        action = bar.findViewById(R.id.nav_action);
        back = bar.findViewById(R.id.nav_back);
        contentHeight = bar.getResources().getDimensionPixelSize(R.dimen.nav_bar_height);
    }

    /** Total height including the status bar. */
    int height() {
        return bar.getLayoutParams().height;
    }

    void applyInsets(SystemInsets insets) {
        ViewGroup.LayoutParams lp = bar.getLayoutParams();
        lp.height = insets.top + contentHeight;
        bar.setLayoutParams(lp);
        content.setPadding(insets.left, 0, insets.right, 0);
    }

    void setCollapsed(boolean value) {
        if (value == collapsed) return;
        collapsed = value;
        float a = value ? 1f : 0f;
        background.animate().alpha(a).setDuration(Motion.FAST).start();
        line.animate().alpha(a).setDuration(Motion.FAST).start();
        title.animate().alpha(a).setDuration(Motion.FAST).start();
    }
}
