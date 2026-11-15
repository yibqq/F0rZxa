package app.kuro.anime.playback;

import androidx.annotation.NonNull;

/** Default: no playback backend is connected. The UI explains this honestly instead of faking a player. */
public final class UnconfiguredPlaybackProvider implements EpisodePlaybackProvider {

    @NonNull @Override public String id() { return "none"; }

    @NonNull @Override public String displayName() { return "None"; }

    @Override public boolean isAvailable() { return false; }

    @Override
    public void resolve(@NonNull PlaybackRequest request, @NonNull Callback callback) {
        callback.onUnavailable("No playback provider installed");
    }

    @Override public void cancel() {}
}
