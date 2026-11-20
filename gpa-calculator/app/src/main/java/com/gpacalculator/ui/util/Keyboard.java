package com.gpacalculator.ui.util;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public final class Keyboard {
    private Keyboard() {
    }

    public static void hide(View anyViewInWindow) {
        InputMethodManager imm = (InputMethodManager) anyViewInWindow.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(anyViewInWindow.getWindowToken(), 0);
        }
    }
}
