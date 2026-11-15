package app.kuro.anime.data.model;

/**
 * Episode artwork that AniList publishes from official streaming partners. Used only as a
 * fallback thumbnail source, and only when the episode number can be parsed unambiguously.
 */
public final class StreamingEpisode {
    public final int number;      // 0 if not parseable
    public final String title;
    public final String thumbnailUrl;

    public StreamingEpisode(int number, String title, String thumbnailUrl) {
        this.number = number;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
    }
}
