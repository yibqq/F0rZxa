package com.gpacalculator.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.gpacalculator.R;
import com.gpacalculator.ui.util.EdgeToEdge;
import com.gpacalculator.ui.util.Keyboard;
import com.gpacalculator.ui.util.SystemInsets;

/**
 * Full-screen transparent dialog that draws its own scrim, so enter and exit motion is fully
 * controlled (window animations are disabled in {@code Theme.GPA.Overlay}).
 */
abstract class OverlayDialog extends Dialog {
    protected final Activity activity;
    protected View root;
    protected View scrim;
    private boolean closing;
    /** {@code OnBackInvokedCallback} on API 33+, held as Object so older runtimes never resolve the type. */
    private Object backCallback;

    OverlayDialog(Activity activity) {
        super(activity, R.style.Theme_GPA_Overlay);
        this.activity = activity;
    }

    protected abstract int layoutRes();

    protected abstract void onViewsCreated(View root);

    protected abstract void animateIn();

    protected abstract void animateOut(Runnable onEnd);

    protected void onInsets(SystemInsets insets) {
    }

    protected void onScrimTapped() {
        dismissAnimated();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layoutRes());
        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            EdgeToEdge.apply(window);
        }
        root = findViewById(R.id.overlay_root);
        scrim = findViewById(R.id.overlay_scrim);
        scrim.setOnClickListener(v -> onScrimTapped());
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            onInsets(SystemInsets.from(insets));
            return insets;
        });
        onViewsCreated(root);
        root.post(this::animateIn);
    }

    /** Plays the exit animation, then dismisses. Safe to call repeatedly. */
    public final void dismissAnimated() {
        if (closing) return;
        closing = true;
        if (root != null) Keyboard.hide(root);
        animateOut(() -> {
            if (isShowing() && !activity.isFinishing() && !activity.isDestroyed()) {
                dismiss();
            }
        });
    }

    protected boolean isClosing() {
        return closing;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= 33) {
            if (backCallback == null) {
                backCallback = (OnBackInvokedCallback) this::dismissAnimated;
            }
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY, (OnBackInvokedCallback) backCallback);
        }
    }

    @Override
    protected void onStop() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) backCallback);
        }
        super.onStop();
    }

    /** Pre-Android 13 path; on 13+ the callback registered in {@link #onStart()} handles Back. */
    @SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        dismissAnimated();
    }
}
