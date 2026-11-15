package app.kuro.anime.ui.common;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Micro-interactions: subtle press-down on touch and lift on focus (D-pad, keyboard). */
public final class Motion {
    public static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private Motion() {}

    @SuppressLint("ClickableViewAccessibility")
    public static void pressable(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).setInterpolator(EASE).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float s = view.isFocused() && !view.isInTouchMode() ? focusScale(view) : 1f;
                    view.animate().scaleX(s).scaleY(s).setDuration(160).setInterpolator(EASE).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** Scales a card up while focused. Chains with any existing focus listener passed in. */
    public static void focusLift(View v, float scale, View.OnFocusChangeListener chained) {
        v.setTag(app.kuro.anime.R.id.tag_focus_scale, scale);
        v.setOnFocusChangeListener((view, has) -> {
            float s = has ? scale : 1f;
            view.animate().scaleX(s).scaleY(s).translationZ(has ? 8f : 0f).setDuration(180).setInterpolator(EASE).start();
            if (chained != null) chained.onFocusChange(view, has);
        });
    }

    private static float focusScale(View v) {
        Object t = v.getTag(app.kuro.anime.R.id.tag_focus_scale);
        return t instanceof Float ? (Float) t : 1f;
    }

    public static void fadeIn(View v) {
        if (v.getVisibility() == View.VISIBLE && v.getAlpha() == 1f) return;
        v.setAlpha(0f);
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).setDuration(200).setInterpolator(EASE).start();
    }
}
