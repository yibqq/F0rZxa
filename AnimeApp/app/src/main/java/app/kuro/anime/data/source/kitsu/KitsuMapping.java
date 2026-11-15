package app.kuro.anime.data.source.kitsu;

import androidx.annotation.Nullable;

import app.kuro.anime.data.model.FuzzyDate;

/** Result of resolving an AniList/MAL ID to a Kitsu anime through Kitsu's own mapping table. */
public final class KitsuMapping {
    public static final KitsuMapping NONE = new KitsuMapping(0, 0, null);

    public final int kitsuId;
    public final int episodeCount;
    @Nullable public final FuzzyDate startDate;

    public KitsuMapping(int kitsuId, int episodeCount, @Nullable FuzzyDate startDate) {
        this.kitsuId = kitsuId;
        this.episodeCount = episodeCount;
        this.startDate = startDate;
    }

    public boolean found() { return kitsuId > 0; }
}
