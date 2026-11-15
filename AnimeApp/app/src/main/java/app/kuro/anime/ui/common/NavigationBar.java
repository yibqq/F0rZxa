package app.kuro.anime.ui.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.R;
import app.kuro.anime.databinding.ItemNavBinding;

/** One navigation component rendered as a bottom bar (compact) or a side rail (medium/expanded). */
public class NavigationBar extends LinearLayout {

    public static final int HOME = 0, EXPLORE = 1, SEARCH = 2, MY_LIST = 3, SETTINGS = 4;
    private static final int[] ICONS = {R.drawable.ic_home, R.drawable.ic_explore, R.drawable.ic_search,
            R.drawable.ic_bookmark, R.drawable.ic_settings};
    private static final int[] LABELS = {R.string.nav_home, R.string.nav_explore, R.string.nav_search,
            R.string.nav_my_list, R.string.nav_settings};

    public interface OnItemSelected { void onSelected(int index, boolean reselected); }

    private final boolean rail;
    private final List<ItemNavBinding> items = new ArrayList<>();
    private int selected = -1;
    private OnItemSelected listener;

    public NavigationBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NavigationBar);
        rail = a.getBoolean(R.styleable.NavigationBar_rail, false);
        a.recycle();
        setOrientation(rail ? VERTICAL : HORIZONTAL);
        setGravity(rail ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        setBackgroundResource(rail ? R.drawable.bg_rail : R.drawable.bg_bottom_bar);
        LayoutInflater inf = LayoutInflater.from(context);
        if (rail) {
            View spacer = new View(context);
            addView(spacer, new LayoutParams(1, dp(12)));
        }
        for (int i = 0; i < ICONS.length; i++) {
            final int index = i;
            ItemNavBinding b = ItemNavBinding.inflate(inf, this, false);
            b.navIcon.setImageResource(ICONS[i]);
            b.navLabel.setText(LABELS[i]);
            b.getRoot().setContentDescription(context.getString(LABELS[i]));
            b.getRoot().setOnClickListener(v -> select(index, true));
            ViewCompat.setAccessibilityDelegate(b.getRoot(), new AccessibilityDelegateCompat() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.TabWidget");
                    info.setSelected(host.isSelected());
                }
            });
            LayoutParams lp = rail ? new LayoutParams(dp(80), dp(72)) : new LayoutParams(0, dp(64), 1f);
            if (rail) lp.topMargin = dp(4);
            addView(b.getRoot(), lp);
            items.add(b);
        }
    }

    public boolean isRail() { return rail; }

    public void setOnItemSelected(OnItemSelected l) { listener = l; }

    public int getSelected() { return selected; }

    public void select(int index, boolean fromUser) {
        boolean reselect = index == selected;
        selected = index;
        for (int i = 0; i < items.size(); i++) {
            boolean on = i == index;
            ItemNavBinding b = items.get(i);
            b.getRoot().setSelected(on);
            b.navIcon.setSelected(on);
            b.navIndicator.setSelected(on);
            b.navLabel.setSelected(on);
        }
        if (fromUser && listener != null) listener.onSelected(index, reselect);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
