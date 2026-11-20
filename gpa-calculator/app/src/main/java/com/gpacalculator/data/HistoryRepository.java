package com.gpacalculator.data;

import android.content.Context;
import android.database.SQLException;
import android.os.Handler;
import android.os.Looper;

import com.gpacalculator.model.HistoryRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous access to saved calculations. Work runs on one background thread (so writes are
 * ordered) and results are delivered on the main thread.
 */
public final class HistoryRepository {

    public interface Callback<T> {
        void onResult(T value);
    }

    public interface Listener {
        void onHistoryChanged();
    }

    private static volatile HistoryRepository instance;

    private final HistoryDatabase database;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "history-io");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();

    private HistoryRepository(Context context) {
        database = new HistoryDatabase(context);
    }

    public static HistoryRepository get(Context context) {
        HistoryRepository local = instance;
        if (local == null) {
            synchronized (HistoryRepository.class) {
                local = instance;
                if (local == null) {
                    local = new HistoryRepository(context.getApplicationContext());
                    instance = local;
                }
            }
        }
        return local;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public void loadAll(Callback<List<HistoryRecord>> callback) {
        io.execute(() -> {
            List<HistoryRecord> result;
            try {
                result = database.queryAll();
            } catch (SQLException e) {
                result = Collections.emptyList();
            }
            List<HistoryRecord> finalResult = result;
            main.post(() -> callback.onResult(finalResult));
        });
    }

    public void load(long id, Callback<HistoryRecord> callback) {
        io.execute(() -> {
            HistoryRecord result;
            try {
                result = database.query(id);
            } catch (SQLException e) {
                result = null;
            }
            HistoryRecord finalResult = result;
            main.post(() -> callback.onResult(finalResult));
        });
    }

    /** Saves a record and returns it with its database id, or {@code null} on failure. */
    public void save(HistoryRecord record, Callback<HistoryRecord> callback) {
        io.execute(() -> {
            HistoryRecord saved;
            try {
                long id = database.insert(record);
                saved = id > 0 ? record.withId(id) : null;
            } catch (SQLException e) {
                saved = null;
            }
            HistoryRecord finalSaved = saved;
            main.post(() -> {
                if (callback != null) callback.onResult(finalSaved);
                if (finalSaved != null) notifyChanged();
            });
        });
    }

    public void delete(long id, Callback<Boolean> callback) {
        io.execute(() -> {
            boolean ok;
            try {
                database.delete(id);
                ok = true;
            } catch (SQLException e) {
                ok = false;
            }
            boolean finalOk = ok;
            main.post(() -> {
                if (callback != null) callback.onResult(finalOk);
                notifyChanged();
            });
        });
    }

    private void notifyChanged() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.onHistoryChanged();
        }
    }
}
