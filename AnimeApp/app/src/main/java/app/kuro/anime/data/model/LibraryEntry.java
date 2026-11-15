package app.kuro.anime.data.model;

import androidx.annotation.Nullable;

/** A title the user saved locally, with their status, favourite flag and progress. */
public final class LibraryEntry {
    public String key;
    public int anilistId;
    public int malId;
    public String titleRomaji;
    public String titleEnglish;
    public String titleNative;
    public String coverUrl;
    public String coverColor;
    public String format;
    public String airingStatus;
    public int totalEpisodes;
    @Nullable public WatchStatus status;
    public boolean favorite;
    public int watchedCount;
    public long addedAt;
    public long updatedAt;

    public static LibraryEntry from(Anime a) {
        LibraryEntry e = new LibraryEntry();
        e.key = a.key();
        e.anilistId = a.anilistId;
        e.malId = a.malId;
        e.titleRomaji = a.titleRomaji;
        e.titleEnglish = a.titleEnglish;
        e.titleNative = a.titleNative;
        e.coverUrl = a.coverUrl;
        e.coverColor = a.coverColor;
        e.format = a.format;
        e.airingStatus = a.status;
        e.totalEpisodes = a.episodes;
        return e;
    }

    /** Lightweight Anime for list/grid display without a network call. */
    public Anime toAnime() {
        Anime a = new Anime();
        a.anilistId = anilistId;
        a.malId = malId;
        a.titleRomaji = titleRomaji;
        a.titleEnglish = titleEnglish;
        a.titleNative = titleNative;
        a.coverUrl = coverUrl;
        a.coverColor = coverColor;
        a.format = format;
        a.status = airingStatus;
        a.episodes = totalEpisodes;
        a.origin = "library";
        return a;
    }
}
