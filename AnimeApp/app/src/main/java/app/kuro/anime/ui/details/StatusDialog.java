package app.kuro.anime.ui.details;

import android.app.AlertDialog;
import android.content.Context;

import androidx.annotation.Nullable;

import app.kuro.anime.R;
import app.kuro.anime.data.model.WatchStatus;
import app.kuro.anime.ui.common.Labels;

/** Pick a watch status, or remove the title from the list. */
public final class StatusDialog {
    public interface Callback { void onPicked(@Nullable WatchStatus status); }

    private StatusDialog() {}

    public static void show(Context c, @Nullable WatchStatus current, Callback cb) {
        WatchStatus[] all = WatchStatus.values();
        String[] labels = new String[all.length];
        int checked = -1;
        for (int i = 0; i < all.length; i++) {
            labels[i] = Labels.watchStatus(c, all[i]);
            if (all[i] == current) checked = i;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(c, R.style.Kuro_Dialog)
                .setTitle(current == null ? R.string.add_to_list : R.string.change_status)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    cb.onPicked(all[which]);
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null);
        if (current != null) builder.setNeutralButton(R.string.remove_from_list, (d, w) -> cb.onPicked(null));
        builder.show();
    }
}
