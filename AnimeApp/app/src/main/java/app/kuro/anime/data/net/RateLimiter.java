package app.kuro.anime.data.net;

import java.util.ArrayDeque;

/**
 * Blocking limiter combining a minimum spacing between requests with a sliding-window cap.
 * Also honours server back-off ("Retry-After") by pausing all callers until a given time.
 * Thread-safe; callers must be on background threads.
 */
public final class RateLimiter {

    public interface Clock {
        long now();
        void sleep(long ms) throws InterruptedException;
    }

    public static final Clock SYSTEM = new Clock() {
        @Override public long now() { return System.currentTimeMillis(); }
        @Override public void sleep(long ms) throws InterruptedException { Thread.sleep(ms); }
    };

    private final int maxPerWindow;
    private final long windowMs;
    private final long minIntervalMs;
    private final Clock clock;
    private final ArrayDeque<Long> stamps = new ArrayDeque<>();
    private long last;
    private boolean hasLast;
    private long pausedUntil;

    public RateLimiter(int maxPerWindow, long windowMs, long minIntervalMs, Clock clock) {
        this.maxPerWindow = maxPerWindow;
        this.windowMs = windowMs;
        this.minIntervalMs = minIntervalMs;
        this.clock = clock;
    }

    public RateLimiter(int maxPerWindow, long windowMs, long minIntervalMs) {
        this(maxPerWindow, windowMs, minIntervalMs, SYSTEM);
    }

    public void acquire() throws InterruptedException {
        while (true) {
            long wait;
            synchronized (this) {
                long now = clock.now();
                while (!stamps.isEmpty() && now - stamps.peekFirst() >= windowMs) stamps.pollFirst();
                wait = 0;
                if (pausedUntil > now) wait = pausedUntil - now;
                if (hasLast && now - last < minIntervalMs) wait = Math.max(wait, minIntervalMs - (now - last));
                if (stamps.size() >= maxPerWindow) wait = Math.max(wait, windowMs - (now - stamps.peekFirst()));
                if (wait <= 0) {
                    last = now;
                    hasLast = true;
                    stamps.addLast(now);
                    return;
                }
            }
            clock.sleep(Math.min(wait, 5_000));
        }
    }

    /** Called after an HTTP 429 so every caller backs off. */
    public synchronized void pauseFor(long ms) {
        pausedUntil = Math.max(pausedUntil, clock.now() + ms);
    }

    /** Time a caller would currently have to wait; used to fail fast instead of blocking for minutes. */
    public synchronized long pendingPauseMs() {
        return Math.max(0, pausedUntil - clock.now());
    }
}
