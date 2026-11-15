package app.kuro.anime.playback;

import android.app.Activity;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

/**
 * Contract for plugging a streaming backend into Kuro later.
 *
 * <p>Kuro's anime metadata (AniList/Kitsu/Jikan) is completely separate from playback. The app
 * never builds stream URLs, never scrapes, and ships no player. A provider receives a
 * {@link PlaybackRequest} describing <em>which</em> episode the user wants (with every external ID
 * Kuro knows, so the provider can match reliably) and decides by itself how to resolve and play it.</p>
 *
 * <p>Implementations must do their own network work off the main thread and report back through
 * {@link Callback} on the main thread. Register an implementation with
 * {@link PlaybackProviders#install(EpisodePlaybackProvider)} in {@code KuroApp#onCreate()}.</p>
 */
public interface EpisodePlaybackProvider {

    /** Stable identifier, e.g. "my-backend". Shown in Settings. */
    @NonNull String id();

    /** Human-readable name for Settings. */
    @NonNull String displayName();

    /** Whether the provider is configured and may be offered to the user. */
    boolean isAvailable();

    /**
     * Asks the provider whether it can play the requested episode. Must not block.
     * Implementations should call exactly one callback method, on the main thread.
     */
    @MainThread
    void resolve(@NonNull PlaybackRequest request, @NonNull Callback callback);

    /** Cancels any in-flight work started by {@link #resolve}. */
    @MainThread
    void cancel();

    interface Callback {
        /** The provider can play the episode; {@code session.start(activity)} launches its own UI. */
        void onReady(@NonNull PlaybackSession session);

        /** The provider has nothing for this episode (not an error). */
        void onUnavailable(@NonNull String reason);

        void onError(@NonNull Throwable error);
    }

    /** Opaque handle owned by the provider. Kuro never inspects stream details. */
    interface PlaybackSession {
        @MainThread
        void start(@NonNull Activity host);
    }
}
