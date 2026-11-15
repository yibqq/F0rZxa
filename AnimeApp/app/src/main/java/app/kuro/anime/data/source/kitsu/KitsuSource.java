package app.kuro.anime.data.source.kitsu;

import java.util.HashMap;
import java.util.Map;

import app.kuro.anime.core.AppError;
import app.kuro.anime.data.net.HttpClient;
import app.kuro.anime.data.net.RateLimiter;
import app.kuro.anime.data.source.SourceCall;

/**
 * Episode source: Kitsu JSON:API (keyless). Chosen because it exposes per-episode titles,
 * air dates and real episode thumbnails, plus an explicit ID mapping table (AniList/MAL → Kitsu)
 * so no title matching is needed.
 */
public final class KitsuSource {

    public static final String HOST = "kitsu.app";
    private static final String BASE = "https://" + HOST + "/api/edge";
    public static final int PAGE_LIMIT = 20; // Kitsu's maximum page size for episodes
    private static final long HOUR = 3_600_000L;

    private final HttpClient http;
    private final Map<String, String> headers = new HashMap<>();

    public KitsuSource(HttpClient http) {
        this.http = http;
        headers.put("Accept", "application/vnd.api+json");
        http.registerLimiter(HOST, new RateLimiter(60, 60_000, 200));
    }

    /** @param site "anilist/anime" or "myanimelist/anime" */
    public SourceCall<KitsuMapping> mapping(String site, int externalId) {
        String url = BASE + "/mappings?filter%5BexternalSite%5D=" + site.replace("/", "%2F")
                + "&filter%5BexternalId%5D=" + externalId
                + "&include=item&fields%5Banime%5D=startDate,episodeCount";
        return new SourceCall<>("kitsu|map|" + site + "|" + externalId, 7 * 24 * HOUR,
                () -> get(url), KitsuMapper::parseMapping);
    }

    public SourceCall<KitsuMapper.EpisodePage> episodes(int kitsuId, int offset) {
        String url = BASE + "/anime/" + kitsuId + "/episodes?sort=number&page%5Blimit%5D=" + PAGE_LIMIT
                + "&page%5Boffset%5D=" + offset
                + "&fields%5Bepisodes%5D=number,canonicalTitle,titles,airdate,thumbnail,length";
        return new SourceCall<>("kitsu|eps|" + kitsuId + "|" + offset, 12 * HOUR,
                () -> get(url), KitsuMapper::parseEpisodes);
    }

    private String get(String url) throws AppError { return http.get(url, headers); }
}
