package com.gpacalculator.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gpacalculator.R;
import com.gpacalculator.ui.util.Dimens;
import com.gpacalculator.ui.util.Motion;

/** Short-lived pill above the tab bar, optionally with an action such as Undo. */
final class Banner {
    private static final long SHORT = 2200;
    private static final long WITH_ACTION = 4500;

    private final View view;
    private final TextView text;
    private final TextView action;
    private final Runnable hideRunnable = this::hide;
    private Runnable pendingAction;

    Banner(View view) {
        this.view = view;
        this.text = view.findViewById(R.id.banner_text);
        this.action = view.findViewById(R.id.banner_action);
        action.setOnClickListener(v -> {
            Runnable a = pendingAction;
            hide();
            if (a != null) a.run();
        });
    }

    void setBottomOffset(int px) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        lp.bottomMargin = px + Dimens.dp(view.getContext(), 12);
        view.setLayoutParams(lp);
    }

    void show(CharSequence message) {
        show(message, null, null);
    }

    void show(CharSequence message, CharSequence actionLabel, Runnable onAction) {
        view.removeCallbacks(hideRunnable);
        view.animate().cancel();
        text.setText(message);
        pendingAction = onAction;
        action.setVisibility(onAction != null ? View.VISIBLE : View.GONE);
        if (actionLabel != null) action.setText(actionLabel);
        view.setPaddingRelative(view.getPaddingStart(), 0,
                Dimens.dp(view.getContext(), onAction != null ? 8 : 20), 0);

        if (view.getVisibility() != View.VISIBLE || view.getAlpha() < 1f) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.setTranslationY(Dimens.dpF(view.getContext(), 20));
            view.setScaleX(0.94f);
            view.setScaleY(0.94f);
        }
        view.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(Motion.SPRING_DURATION).setInterpolator(Motion.SPRING).start();
        view.postDelayed(hideRunnable, onAction != null ? WITH_ACTION : SHORT);
    }

    void hide() {
        view.removeCallbacks(hideRunnable);
        pendingAction = null;
        if (view.getVisibility() != View.VISIBLE) return;
        view.animate().alpha(0f).translationY(Dimens.dpF(view.getContext(), 12))
                .setDuration(Motion.FAST).setInterpolator(Motion.EASE_IN)
                .withEndAction(() -> view.setVisibility(View.GONE)).start();
    }
}
