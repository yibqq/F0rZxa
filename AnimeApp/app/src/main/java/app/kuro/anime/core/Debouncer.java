package app.kuro.anime.core;

import android.os.Handler;
import android.os.Looper;

/** Delays an action until input has been quiet for {@code delayMs}. Main-thread only. */
public final class Debouncer {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long delayMs;
    private Runnable pending;

    public Debouncer(long delayMs) { this.delayMs = delayMs; }

    public void submit(Runnable action) {
        cancel();
        pending = action;
        handler.postDelayed(action, delayMs);
    }

    public void cancel() {
        if (pending != null) handler.removeCallbacks(pending);
        pending = null;
    }
}
