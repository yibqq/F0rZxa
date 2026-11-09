package com.mizan.gpa.export;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.mizan.gpa.MizanApp;
import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.ui.Dialogs;
import com.mizan.gpa.util.Fmt;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PDF export and JSON backup/restore through the system file picker (Storage Access Framework):
 * no storage permission is needed and the user chooses where files go.
 */
public final class DataTransfer {
    private static final String TAG = "MizanTransfer";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private final AppCompatActivity a;
    private final ActivityResultLauncher<String> pdf;
    private final ActivityResultLauncher<String> backup;
    private final ActivityResultLauncher<String[]> restore;

    /** Must be created during onCreate of the activity. */
    public DataTransfer(AppCompatActivity activity) {
        this.a = activity;
        pdf = a.registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"), uri -> {
            if (uri != null) writePdf(uri);
        });
        backup = a.registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri != null) writeBackup(uri);
        });
        restore = a.registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) readBackup(uri);
        });
    }

    private AppData snapshot() throws Exception {
        // A deep copy, so the background thread never sees the data change under it.
        return AppData.parse(Repository.get(a).data().serialize(0));
    }

    public void exportPdf() {
        if (Repository.get(a).data().semesters.isEmpty()) {
            snack(a.getString(R.string.nothing_to_export), null, null);
            return;
        }
        launch(pdf, "Mizan-report-" + Fmt.today() + ".pdf");
    }

    public void backup() {
        launch(backup, "mizan-backup-" + Fmt.today() + ".json");
    }

    public void restore() {
        try {
            restore.launch(new String[]{"application/json", "text/plain", "application/octet-stream"});
        } catch (ActivityNotFoundException e) {
            snack(a.getString(R.string.no_file_picker), null, null);
        }
    }

    private void launch(ActivityResultLauncher<String> l, String name) {
        try {
            l.launch(name);
        } catch (ActivityNotFoundException e) {
            snack(a.getString(R.string.no_file_picker), null, null);
        }
    }

    private void writePdf(Uri uri) {
        final AppData copy;
        try {
            copy = snapshot();
        } catch (Exception e) {
            fail(e);
            return;
        }
        IO.execute(() -> {
            try (OutputStream out = a.getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new java.io.IOException("No output stream");
                PdfReport.write(a, copy, out);
                a.runOnUiThread(() -> snack(a.getString(R.string.pdf_saved), a.getString(R.string.open), () -> open(uri, "application/pdf")));
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    private void writeBackup(Uri uri) {
        final String json;
        try {
            json = Repository.get(a).data().serialize(2);
        } catch (Exception e) {
            fail(e);
            return;
        }
        IO.execute(() -> {
            try (OutputStream out = a.getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new java.io.IOException("No output stream");
                out.write(json.getBytes(StandardCharsets.UTF_8));
                a.runOnUiThread(() -> snack(a.getString(R.string.backup_saved), null, null));
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    private void readBackup(Uri uri) {
        IO.execute(() -> {
            try {
                InputStream in = a.getContentResolver().openInputStream(uri);
                if (in == null) throw new java.io.IOException("No input stream");
                String text = Repository.readAll(in);
                AppData parsed = AppData.parse(text);
                int courses = 0;
                for (Semester s : parsed.semesters) courses += s.courses.size();
                int c = courses;
                a.runOnUiThread(() -> Dialogs.confirm(a, a.getString(R.string.restore_confirm_title),
                        a.getString(R.string.restore_confirm_text, parsed.semesters.size(), c), R.string.restore, () -> {
                            Repository.get(a).replace(parsed);
                            MizanApp.applyTheme(parsed.settings.themeMode);
                            snack(a.getString(R.string.restore_done), null, null);
                            a.recreate();
                        }));
            } catch (Exception e) {
                Log.w(TAG, "Restore failed", e);
                a.runOnUiThread(() -> snack(a.getString(R.string.restore_invalid), null, null));
            }
        });
    }

    private void open(Uri uri, String type) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (ActivityNotFoundException e) {
            snack(a.getString(R.string.no_pdf_viewer), null, null);
        }
    }

    private void fail(Exception e) {
        Log.e(TAG, "Export failed", e);
        a.runOnUiThread(() -> snack(a.getString(R.string.export_failed), null, null));
    }

    private void snack(String msg, String action, Runnable onAction) {
        android.view.View root = a.findViewById(R.id.snack_anchor_root);
        if (root == null) root = a.findViewById(android.R.id.content);
        Snackbar s = Snackbar.make(root, msg, Snackbar.LENGTH_LONG);
        android.view.View nav = a.findViewById(R.id.nav);
        if (nav instanceof com.google.android.material.bottomnavigation.BottomNavigationView) s.setAnchorView(nav);
        if (action != null) s.setAction(action, v -> onAction.run());
        s.show();
    }
}
