package com.gpacalculator.ui.util;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** Light haptic vocabulary. All calls respect the system "touch feedback" setting. */
public final class Haptics {
    private Haptics() {
    }

    /** Changing a selection: segmented control, grade key, tab, stepper. */
    public static void selection(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    public static void toggle(View view, boolean on) {
        if (Build.VERSION.SDK_INT >= 34) {
            view.performHapticFeedback(on ? HapticFeedbackConstants.TOGGLE_ON : HapticFeedbackConstants.TOGGLE_OFF);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    public static void success(View view) {
        if (Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    public static void warning(View view) {
        if (Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }

    /** Crossing a gesture threshold, e.g. swipe-to-delete. */
    public static void threshold(View view) {
        if (Build.VERSION.SDK_INT >= 34) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }
}
