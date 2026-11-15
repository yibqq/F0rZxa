package app.kuro.anime.core;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared thread pools. Network calls may block (rate limiting), so they never run on the main thread. */
public final class AppExecutors {

    private static final ExecutorService IO = Executors.newFixedThreadPool(4, named("kuro-io"));
    private static final ExecutorService DISK = Executors.newSingleThreadExecutor(named("kuro-disk"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() {}

    public static ExecutorService io() { return IO; }

    public static ExecutorService disk() { return DISK; }

    public static void main(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run(); else MAIN.post(r);
    }

    public static Handler mainHandler() { return MAIN; }

    private static ThreadFactory named(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.setDaemon(true);
            return t;
        };
    }
}
