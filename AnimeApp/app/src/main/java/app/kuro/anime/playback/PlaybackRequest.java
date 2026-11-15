package app.kuro.anime.playback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Everything Kuro knows about the episode the user wants to watch. IDs are explicit
 * cross-references (AniList ↔ MyAnimeList), so providers can match without guessing by title.
 */
public final class PlaybackRequest {
    public final int anilistId;          // 0 if unknown
    public final int malId;              // 0 if unknown
    @Nullable public final String titleRomaji;
    @Nullable public final String titleEnglish;
    @Nullable public final String titleNative;
    public final int episodeNumber;      // 1-based
    @Nullable public final String episodeTitle;
    public final int totalEpisodes;      // 0 if unknown
    @NonNull public final String preferredLanguageTag; // the app UI locale, e.g. "ar" or "en"

    public PlaybackRequest(int anilistId, int malId, @Nullable String titleRomaji, @Nullable String titleEnglish,
                           @Nullable String titleNative, int episodeNumber, @Nullable String episodeTitle,
                           int totalEpisodes, @NonNull String preferredLanguageTag) {
        this.anilistId = anilistId;
        this.malId = malId;
        this.titleRomaji = titleRomaji;
        this.titleEnglish = titleEnglish;
        this.titleNative = titleNative;
        this.episodeNumber = episodeNumber;
        this.episodeTitle = episodeTitle;
        this.totalEpisodes = totalEpisodes;
        this.preferredLanguageTag = preferredLanguageTag;
    }
}
