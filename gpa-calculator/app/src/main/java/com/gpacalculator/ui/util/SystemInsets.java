package com.gpacalculator.ui.util;

import android.graphics.Insets;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.WindowInsets;

/** System bar, cutout and keyboard insets, normalised across API levels. */
public final class SystemInsets {
    public final int left;
    public final int top;
    public final int right;
    /** Navigation bar / gesture area. */
    public final int bottom;
    /** Bottom inset including the keyboard when it is open (never less than {@link #bottom}). */
    public final int ime;

    private SystemInsets(int left, int top, int right, int bottom, int ime) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.ime = Math.max(bottom, ime);
    }

    public boolean keyboardVisible() {
        return ime > bottom;
    }

    @SuppressWarnings("deprecation")
    public static SystemInsets from(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsets.Type.ime());
            return new SystemInsets(bars.left, bars.top, bars.right, bars.bottom, ime.bottom);
        }
        int left = insets.getSystemWindowInsetLeft();
        int top = insets.getSystemWindowInsetTop();
        int right = insets.getSystemWindowInsetRight();
        int bottom = insets.getStableInsetBottom();
        if (Build.VERSION.SDK_INT >= 28) {
            DisplayCutout cutout = insets.getDisplayCutout();
            if (cutout != null) {
                left = Math.max(left, cutout.getSafeInsetLeft());
                top = Math.max(top, cutout.getSafeInsetTop());
                right = Math.max(right, cutout.getSafeInsetRight());
            }
        }
        return new SystemInsets(left, top, right, bottom, insets.getSystemWindowInsetBottom());
    }
}
