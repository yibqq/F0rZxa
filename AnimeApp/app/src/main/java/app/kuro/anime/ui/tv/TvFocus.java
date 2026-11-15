package app.kuro.anime.ui.tv;

import android.view.View;
import android.view.ViewGroup;

/**
 * Places D-pad focus once the target is attached, laid out and actually focusable.
 * Requests made earlier (e.g. while data arrives during onStart) would otherwise be lost.
 */
final class TvFocus {
    private static final int MAX_TRIES = 40;
    private static final long INTERVAL_MS = 50;

    private TvFocus() {}

    static void request(View target) { attempt(target, 0); }

    private static void attempt(View target, int tries) {
        if (tries > MAX_TRIES) return;
        if (target.isAttachedToWindow() && target.isLaidOut() && target.getWidth() > 0) {
            View root = target.getRootView();
            View current = root == null ? null : root.findFocus();
            if (current != null && current != root && !(current instanceof ViewGroup && current == target.getParent())) {
                // Something meaningful already has focus (e.g. the user moved); don't steal it.
                if (isDescendant(current, target) || current == target) return;
            }
            if (target.requestFocus()) return;
        }
        target.postDelayed(() -> attempt(target, tries + 1), INTERVAL_MS);
    }

    private static boolean isDescendant(View v, View ancestor) {
        for (android.view.ViewParent p = v.getParent(); p instanceof View; p = p.getParent()) {
            if (p == ancestor) return true;
        }
        return false;
    }
}
