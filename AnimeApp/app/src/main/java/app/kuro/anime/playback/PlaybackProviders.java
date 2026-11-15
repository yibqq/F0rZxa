package app.kuro.anime.playback;

import androidx.annotation.NonNull;

/** Holds the active provider. Defaults to {@link UnconfiguredPlaybackProvider}. */
public final class PlaybackProviders {

    private static volatile EpisodePlaybackProvider current = new UnconfiguredPlaybackProvider();

    private PlaybackProviders() {}

    public static void install(@NonNull EpisodePlaybackProvider provider) { current = provider; }

    @NonNull
    public static EpisodePlaybackProvider get() { return current; }
}
