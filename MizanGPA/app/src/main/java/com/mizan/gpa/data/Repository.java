package com.mizan.gpa.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Log;

import com.mizan.gpa.notify.ReminderScheduler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps the app data in memory and persists it as a JSON file in the app's private storage.
 * Writes go through {@link AtomicFile}, so a crash during a save never leaves a half-written file.
 * The first save of every app run also keeps a copy of the previous file as a safety net.
 */
public final class Repository {
    private static final String TAG = "MizanRepo";
    private static final String FILE = "mizan-data.json";
    private static final String SAFETY_FILE = "mizan-data.previous.json";

    public interface Listener {
        void onDataChanged();
    }

    private static Repository instance;

    private final Context app;
    private final AtomicFile file;
    private final File safety;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private AppData data;
    private boolean safetyWritten;

    public static synchronized Repository get(Context context) {
        if (instance == null) instance = new Repository(context.getApplicationContext());
        return instance;
    }

    private Repository(Context app) {
        this.app = app;
        this.file = new AtomicFile(new File(app.getFilesDir(), FILE));
        this.safety = new File(app.getFilesDir(), SAFETY_FILE);
        this.data = load();
    }

    private AppData load() {
        try {
            if (file.getBaseFile().exists()) {
                return AppData.parse(new String(file.readFully(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.e(TAG, "Data file unreadable, trying the safety copy", e);
            try {
                if (safety.exists()) return AppData.parse(readAll(new FileInputStream(safety)));
            } catch (Exception e2) {
                Log.e(TAG, "Safety copy unreadable too", e2);
            }
        }
        return new AppData();
    }

    public synchronized AppData data() {
        return data;
    }

    public Settings settings() {
        return data().settings;
    }

    /** Persists the current data and notifies listeners. Call after every change. */
    public synchronized void save() {
        write();
        ReminderScheduler.scheduleNext(app);
        com.mizan.gpa.widget.Widgets.updateAll(app);
        main.post(() -> {
            for (Listener l : listeners) l.onDataChanged();
        });
    }

    /** Persists without re-scheduling reminders or refreshing screens (for internal bookkeeping). */
    public synchronized void saveQuietly() {
        write();
    }

    private void write() {
        writeSafetyCopyOnce();
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(data.serialize(0).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (Exception e) {
            Log.e(TAG, "Saving failed", e);
            if (out != null) file.failWrite(out);
        }
    }

    /** Replaces all data (restore from backup, sample data, clear). */
    public synchronized void replace(AppData newData) {
        this.data = newData;
        save();
    }

    private void writeSafetyCopyOnce() {
        if (safetyWritten) return;
        safetyWritten = true;
        File base = file.getBaseFile();
        if (!base.exists()) return;
        try (InputStream in = new FileInputStream(base); OutputStream o = new FileOutputStream(safety)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        } catch (IOException e) {
            Log.w(TAG, "Could not write safety copy", e);
        }
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public static String readAll(InputStream in) throws IOException {
        try (InputStream is = in) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString(StandardCharsets.UTF_8.name());
        }
    }
}
