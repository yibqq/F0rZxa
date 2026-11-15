package app.kuro.anime.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import java.util.List;

import app.kuro.anime.R;

/** iOS-style segmented control: monochrome track with a white selected segment. Keyboard/D-pad friendly. */
public class SegmentedControl extends LinearLayout {

    public interface OnSegmentSelected { void onSelected(int index); }

    private int selected = -1;
    private OnSegmentSelected listener;

    public SegmentedControl(Context context) { this(context, null); }

    public SegmentedControl(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setBackgroundResource(R.drawable.bg_segment_track);
        int p = dp(3);
        setPadding(p, p, p, p);
    }

    public void setOnSegmentSelected(OnSegmentSelected l) { listener = l; }

    public void setItems(List<String> labels) { setItems(labels.toArray(new String[0])); }

    public void setItems(String... labels) {
        removeAllViews();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView t = new TextView(getContext());
            t.setText(labels[i]);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(dp(36));
            t.setMinWidth(dp(56));
            t.setPadding(dp(14), 0, dp(14), 0);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            t.setTextColor(ContextCompat.getColorStateList(getContext(), R.color.chip_text));
            t.setBackgroundResource(R.drawable.bg_segment_item);
            t.setFocusable(true);
            t.setClickable(true);
            t.setMaxLines(1);
            t.setOnClickListener(v -> setSelectedIndex(index, true));
            ViewCompat.setAccessibilityDelegate(t, new AccessibilityDelegateCompat() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setRoleDescription(null);
                    info.setClassName("android.widget.RadioButton");
                    info.setCheckable(true);
                    info.setChecked(host.isSelected());
                }
            });
            LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            if (getLayoutParams() != null && getLayoutParams().width == LayoutParams.WRAP_CONTENT) {
                lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            }
            addView(t, lp);
        }
        int keep = selected;
        selected = -1;
        setSelectedIndex(Math.max(0, Math.min(keep, labels.length - 1)), false);
    }

    public int getSelectedIndex() { return selected; }

    public void setSelectedIndex(int index, boolean notify) {
        if (index < 0 || index >= getChildCount()) return;
        boolean changed = index != selected;
        selected = index;
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setSelected(i == index);
        if (changed && notify && listener != null) listener.onSelected(index);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
