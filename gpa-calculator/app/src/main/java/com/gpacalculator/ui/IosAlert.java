package com.gpacalculator.ui;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import com.gpacalculator.R;
import com.gpacalculator.ui.util.Motion;

/** Centered two-button confirmation in the style of a UIAlertController with a destructive action. */
final class IosAlert extends OverlayDialog {
    private final CharSequence title;
    private final CharSequence message;
    private final CharSequence confirmLabel;
    private final Runnable onConfirm;
    private View card;

    static void showDestructive(Activity activity, CharSequence title, CharSequence message,
                                CharSequence confirmLabel, Runnable onConfirm) {
        new IosAlert(activity, title, message, confirmLabel, onConfirm).show();
    }

    private IosAlert(Activity activity, CharSequence title, CharSequence message,
                     CharSequence confirmLabel, Runnable onConfirm) {
        super(activity);
        this.title = title;
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
    }

    @Override
    protected int layoutRes() {
        return R.layout.dialog_alert;
    }

    @Override
    protected void onViewsCreated(View root) {
        card = root.findViewById(R.id.alert_card);
        ((TextView) root.findViewById(R.id.alert_title)).setText(title);
        ((TextView) root.findViewById(R.id.alert_message)).setText(message);
        TextView confirm = root.findViewById(R.id.alert_confirm);
        confirm.setText(confirmLabel);
        TextView cancel = root.findViewById(R.id.alert_cancel);
        cancel.setOnClickListener(v -> dismissAnimated());
        confirm.setOnClickListener(v -> {
            if (isClosing()) return;
            dismissAnimated();
            onConfirm.run();
        });
    }

    @Override
    protected void onScrimTapped() {
        // Like iOS alerts, require an explicit choice.
    }

    @Override
    protected void animateIn() {
        scrim.animate().alpha(1f).setDuration(Motion.FAST).start();
        card.setScaleX(1.12f);
        card.setScaleY(1.12f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(Motion.SPRING_DURATION).setInterpolator(Motion.SPRING).start();
        card.announceForAccessibility(title);
    }

    @Override
    protected void animateOut(Runnable onEnd) {
        card.animate().alpha(0f).setDuration(Motion.FAST).start();
        scrim.animate().alpha(0f).setDuration(Motion.NORMAL).withEndAction(onEnd).start();
    }
}
