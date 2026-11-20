package com.gpacalculator.ui.util;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;

/** Subtle shrink-on-touch with a spring release, like UIKit buttons. */
public final class PressEffect {
    private PressEffect() {
    }

    public static void attach(View view) {
        attach(view, 0.97f);
    }

    @SuppressLint("ClickableViewAccessibility")
    public static void attach(View view, float pressedScale) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (v.isEnabled()) {
                        v.animate().scaleX(pressedScale).scaleY(pressedScale)
                                .setDuration(110).setInterpolator(Motion.EASE_OUT).start();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f)
                            .setDuration(Motion.SPRING_DURATION).setInterpolator(Motion.SPRING_BOUNCY).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** One-shot "pop" used to acknowledge a selection. */
    public static void pop(View view, float from) {
        view.setScaleX(from);
        view.setScaleY(from);
        view.animate().scaleX(1f).scaleY(1f)
                .setDuration(Motion.SPRING_DURATION).setInterpolator(Motion.SPRING_BOUNCY).start();
    }

    /** Horizontal shake signalling invalid input. */
    public static void shake(View view) {
        float d = Dimens.dpF(view.getContext(), 8);
        view.animate().cancel();
        view.setTranslationX(0f);
        android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofFloat(view, View.TRANSLATION_X,
                0, -d, d, -d * 0.6f, d * 0.6f, -d * 0.25f, 0);
        a.setDuration(420);
        a.start();
    }
}
