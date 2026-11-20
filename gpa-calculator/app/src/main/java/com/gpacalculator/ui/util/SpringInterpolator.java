package com.gpacalculator.ui.util;

import android.animation.TimeInterpolator;

/**
 * Closed-form damped spring (the same model UIKit uses), normalised so the motion
 * settles exactly when the animation's duration ends.
 */
public final class SpringInterpolator implements TimeInterpolator {
    private final double zeta;
    private final double omega;
    private final double omegaD;

    /** @param dampingRatio between 0 (wobbly) and 1 (critically damped, exclusive). */
    public SpringInterpolator(float dampingRatio) {
        if (dampingRatio <= 0f || dampingRatio >= 1f) {
            throw new IllegalArgumentException("dampingRatio must be in (0, 1)");
        }
        zeta = dampingRatio;
        // Choose ω so the envelope e^(−ζωt) has decayed to 0.1% at t = 1.
        omega = -Math.log(0.001) / zeta;
        omegaD = omega * Math.sqrt(1 - zeta * zeta);
    }

    @Override
    public float getInterpolation(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        double envelope = Math.exp(-zeta * omega * t);
        return (float) (1 - envelope * (Math.cos(omegaD * t) + (zeta * omega / omegaD) * Math.sin(omegaD * t)));
    }
}
