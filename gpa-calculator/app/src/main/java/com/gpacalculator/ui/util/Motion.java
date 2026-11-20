package com.gpacalculator.ui.util;

import android.animation.LayoutTransition;
import android.animation.TimeInterpolator;
import android.view.animation.PathInterpolator;

/** Shared motion curves and durations. */
public final class Motion {
    /** Gentle spring with a barely perceptible settle; for sheets and navigation. */
    public static final TimeInterpolator SPRING = new SpringInterpolator(0.86f);
    /** Livelier spring for small elements: buttons, toggles, icons. */
    public static final TimeInterpolator SPRING_BOUNCY = new SpringInterpolator(0.62f);
    /** iOS default ease-out. */
    public static final TimeInterpolator EASE_OUT = new PathInterpolator(0.2f, 0.9f, 0.25f, 1f);
    public static final TimeInterpolator EASE_IN = new PathInterpolator(0.4f, 0f, 1f, 1f);

    public static final long FAST = 180;
    public static final long NORMAL = 280;
    public static final long SPRING_DURATION = 520;

    private Motion() {
    }

    /** Layout transition for lists whose rows appear and disappear. */
    public static LayoutTransition listTransition() {
        LayoutTransition t = new LayoutTransition();
        int[] types = {LayoutTransition.APPEARING, LayoutTransition.DISAPPEARING,
                LayoutTransition.CHANGE_APPEARING, LayoutTransition.CHANGE_DISAPPEARING};
        for (int type : types) {
            t.setStartDelay(type, 0);
            t.setDuration(type, NORMAL);
        }
        t.setInterpolator(LayoutTransition.CHANGE_APPEARING, EASE_OUT);
        t.setInterpolator(LayoutTransition.CHANGE_DISAPPEARING, EASE_OUT);
        t.setInterpolator(LayoutTransition.APPEARING, EASE_OUT);
        t.setInterpolator(LayoutTransition.DISAPPEARING, EASE_OUT);
        t.setDuration(LayoutTransition.DISAPPEARING, FAST);
        return t;
    }
}
