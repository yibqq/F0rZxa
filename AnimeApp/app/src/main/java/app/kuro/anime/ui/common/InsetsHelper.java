package app.kuro.anime.ui.common;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Edge-to-edge helpers: pad views by system bars / display cutouts, keeping their original padding. */
public final class InsetsHelper {
    private InsetsHelper() {}

    public static final int TOP = 1, BOTTOM = 2, START = 4, END = 8;

    public static void pad(View view, int sides) {
        final int l = view.getPaddingLeft(), t = view.getPaddingTop(), r = view.getPaddingRight(), b = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, wi) -> {
            Insets i = wi.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            boolean rtl = v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            int startInset = rtl ? i.right : i.left;
            int endInset = rtl ? i.left : i.right;
            int left = l, right = r;
            if ((sides & START) != 0) { if (rtl) right += startInset; else left += startInset; }
            if ((sides & END) != 0) { if (rtl) left += endInset; else right += endInset; }
            v.setPadding(left, t + ((sides & TOP) != 0 ? i.top : 0), right, b + ((sides & BOTTOM) != 0 ? i.bottom : 0));
            return wi;
        });
        requestWhenAttached(view);
    }

    /** Adds the status-bar inset to a view's top margin (for floating buttons over images). */
    public static void marginTop(View view) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        final int base = lp.topMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, wi) -> {
            Insets i = wi.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            p.topMargin = base + i.top;
            v.setLayoutParams(p);
            return wi;
        });
        requestWhenAttached(view);
    }

    public static void requestWhenAttached(View view) {
        if (view.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(view);
        } else {
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    v.removeOnAttachStateChangeListener(this);
                    ViewCompat.requestApplyInsets(v);
                }
                @Override public void onViewDetachedFromWindow(View v) {}
            });
        }
    }
}
