package app.kuro.anime.data.model;

/** Stable local identity: the AniList ID when known, otherwise the MyAnimeList ID. */
public final class AnimeKey {
    private AnimeKey() {}

    public static String of(int anilistId, int malId) {
        if (anilistId > 0) return "al:" + anilistId;
        if (malId > 0) return "mal:" + malId;
        throw new IllegalArgumentException("Anime has no usable ID");
    }
}
