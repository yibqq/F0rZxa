package app.kuro.anime.data.source.jikan;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import app.kuro.anime.core.AppError;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.AnimePage;
import app.kuro.anime.data.model.AnimeQuery;
import app.kuro.anime.data.net.HttpClient;
import app.kuro.anime.data.net.RateLimiter;
import app.kuro.anime.data.source.SourceCall;

/**
 * Secondary source: Jikan v4, an unofficial, keyless MyAnimeList API.
 * Used (1) as the catalog fallback when AniList is unavailable and (2) for episode titles,
 * dates and filler/recap flags when Kitsu has none. Limits: 3 req/s and 60 req/min.
 */
public final class JikanSource {

    public static final String HOST = "api.jikan.moe";
    private static final String BASE = "https://" + HOST + "/v4";
    private static final long HOUR = 3_600_000L;
    private static final Map<String, Integer> GENRE_IDS = new HashMap<>();

    static {
        // Stable MyAnimeList genre/theme IDs for the AniList genres offered in Explore.
        GENRE_IDS.put("Action", 1);
        GENRE_IDS.put("Adventure", 2);
        GENRE_IDS.put("Comedy", 4);
        GENRE_IDS.put("Mystery", 7);
        GENRE_IDS.put("Drama", 8);
        GENRE_IDS.put("Fantasy", 10);
        GENRE_IDS.put("Horror", 14);
        GENRE_IDS.put("Mecha", 18);
        GENRE_IDS.put("Music", 19);
        GENRE_IDS.put("Romance", 22);
        GENRE_IDS.put("Sci-Fi", 24);
        GENRE_IDS.put("Sports", 30);
        GENRE_IDS.put("Slice of Life", 36);
        GENRE_IDS.put("Supernatural", 37);
        GENRE_IDS.put("Psychological", 40);
        GENRE_IDS.put("Thriller", 41);
        GENRE_IDS.put("Mahou Shoujo", 66);
    }

    private final HttpClient http;
    private final Map<String, String> headers = Collections.singletonMap("Accept", "application/json");

    public JikanSource(HttpClient http) {
        this.http = http;
        http.registerLimiter(HOST, new RateLimiter(50, 60_000, 400));
    }

    public SourceCall<JikanMapper.EpisodePage> episodes(int malId, int page) {
        String url = BASE + "/anime/" + malId + "/episodes?page=" + page;
        return new SourceCall<>("jikan|eps|" + malId + "|" + page, 12 * HOUR, () -> get(url), JikanMapper::parseEpisodes);
    }

    public SourceCall<Anime> details(int malId) {
        String url = BASE + "/anime/" + malId + "/full";
        return new SourceCall<>("jikan|details|" + malId, 6 * HOUR, () -> get(url), JikanMapper::parseDetails);
    }

    /** "top/anime" lists: filter = null (by score), "airing", "upcoming" or "bypopularity". */
    public SourceCall<AnimePage> top(String filter, int page) {
        String url = BASE + "/top/anime?sfw=true&limit=25&page=" + page + (filter == null ? "" : "&filter=" + filter);
        return new SourceCall<>("jikan|top|" + filter + "|" + page, HOUR, () -> get(url), JikanMapper::parseList);
    }

    public SourceCall<AnimePage> season(int year, String season, int page) {
        String url = BASE + "/seasons/" + year + "/" + season.toLowerCase(Locale.ROOT) + "?sfw=true&limit=25&page=" + page;
        return new SourceCall<>("jikan|season|" + year + season + "|" + page, HOUR, () -> get(url), JikanMapper::parseList);
    }

    /** Best-effort translation of a unified query. Returns null when the query can't be expressed faithfully. */
    public SourceCall<AnimePage> browse(AnimeQuery q, int page) {
        if (q.sort == AnimeQuery.Sort.TRENDING && q.search == null) return null; // no Jikan equivalent
        if (q.season != null && q.year > 0 && q.genre == null && q.search == null) {
            return season(q.year, q.season, page);
        }
        if (q.season != null) return null;
        StringBuilder url = new StringBuilder(BASE).append("/anime?sfw=true&limit=25&page=").append(page);
        if (q.search != null && !q.search.trim().isEmpty()) url.append("&q=").append(enc(q.search.trim()));
        if (q.genre != null) {
            Integer id = GENRE_IDS.get(q.genre);
            if (id == null) return null;
            url.append("&genres=").append(id);
        }
        if (q.year > 0) url.append("&start_date=").append(q.year).append("-01-01&end_date=").append(q.year).append("-12-31");
        if (q.format != null) {
            String t = type(q.format);
            if (t == null) return null;
            url.append("&type=").append(t);
        }
        if (q.status != null) {
            String s = "RELEASING".equals(q.status) ? "airing" : "FINISHED".equals(q.status) ? "complete"
                    : "NOT_YET_RELEASED".equals(q.status) ? "upcoming" : null;
            if (s == null) return null;
            url.append("&status=").append(s);
        }
        switch (q.sort) {
            case SCORE: url.append("&order_by=score&sort=desc"); break;
            case NEWEST: url.append("&order_by=start_date&sort=desc"); break;
            case TITLE: url.append("&order_by=title&sort=asc"); break;
            case RELEVANCE: if (q.search == null) url.append("&order_by=members&sort=desc"); break;
            default: url.append("&order_by=members&sort=desc");
        }
        String u = url.toString();
        return new SourceCall<>("jikan|" + q.cacheKey(page), HOUR, () -> get(u), JikanMapper::parseList);
    }

    private static String type(String format) {
        switch (format) {
            case "TV": return "tv";
            case "MOVIE": return "movie";
            case "OVA": return "ova";
            case "ONA": return "ona";
            case "SPECIAL": return "special";
            case "MUSIC": return "music";
            default: return null;
        }
    }

    private static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    private String get(String url) throws AppError { return http.get(url, headers); }
}
