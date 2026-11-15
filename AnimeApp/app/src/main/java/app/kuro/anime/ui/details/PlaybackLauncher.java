package app.kuro.anime.ui.details;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.kuro.anime.R;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.playback.EpisodePlaybackProvider;
import app.kuro.anime.playback.PlaybackProviders;
import app.kuro.anime.playback.PlaybackRequest;

/**
 * Bridges the "Watch" button to whatever {@link EpisodePlaybackProvider} is installed.
 * With none installed it says so plainly — there is no fake player.
 */
public final class PlaybackLauncher {
    private PlaybackLauncher() {}

    public static void play(Activity activity, Anime a, int episode, @Nullable String episodeTitle) {
        EpisodePlaybackProvider provider = PlaybackProviders.get();
        if (!provider.isAvailable()) {
            new AlertDialog.Builder(activity, R.style.Kuro_Dialog)
                    .setTitle(R.string.playback_unavailable_title)
                    .setMessage(R.string.playback_unavailable_body)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }
        String lang = activity.getResources().getConfiguration().getLocales().get(0).toLanguageTag();
        PlaybackRequest req = new PlaybackRequest(a.anilistId, a.malId, a.titleRomaji, a.titleEnglish, a.titleNative,
                episode, episodeTitle, a.episodes, lang);
        AlertDialog progress = new AlertDialog.Builder(activity, R.style.Kuro_Dialog)
                .setView(progressView(activity))
                .setOnCancelListener(d -> provider.cancel())
                .show();
        provider.resolve(req, new EpisodePlaybackProvider.Callback() {
            @Override
            public void onReady(@NonNull EpisodePlaybackProvider.PlaybackSession session) {
                progress.dismiss();
                if (!activity.isFinishing()) session.start(activity);
            }

            @Override
            public void onUnavailable(@NonNull String reason) {
                progress.dismiss();
                show(activity, reason);
            }

            @Override
            public void onError(@NonNull Throwable error) {
                progress.dismiss();
                show(activity, String.valueOf(error.getMessage()));
            }
        });
    }

    private static LinearLayout progressView(Activity a) {
        int pad = Math.round(24 * a.getResources().getDisplayMetrics().density);
        LinearLayout row = new LinearLayout(a);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(pad, pad, pad, pad);
        ProgressBar bar = new ProgressBar(a);
        bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        row.addView(bar);
        TextView t = new TextView(a);
        t.setText(R.string.playback_resolving);
        t.setTextColor(0xFFFFFFFF);
        t.setPaddingRelative(pad, 0, 0, 0);
        row.addView(t);
        return row;
    }

    private static void show(Activity activity, String reason) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity, R.style.Kuro_Dialog)
                .setMessage(activity.getString(R.string.playback_error, reason))
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
