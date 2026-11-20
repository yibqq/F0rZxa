package com.gpacalculator.ui;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

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

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        dismissAnimated();
    }
}
