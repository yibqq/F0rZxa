package app.kuro.anime.data.source.anilist;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import app.kuro.anime.core.AppError;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.AnimePage;
import app.kuro.anime.data.model.AnimeQuery;
import app.kuro.anime.data.model.HomeFeed;
import app.kuro.anime.data.model.Season;
import app.kuro.anime.data.net.HttpClient;
import app.kuro.anime.data.net.RateLimiter;
import app.kuro.anime.data.source.SourceCall;

/**
 * Primary catalog source: AniList GraphQL (keyless, public). Chosen for complete catalog
 * metadata, high-resolution covers and banners, characters/staff/relations/recommendations,
 * precise airing schedules, and explicit MyAnimeList ID cross-references.
 * Rate limit: 90 req/min nominal (currently 30/min); we stay under it and honour Retry-After.
 */
public final class AniListSource {

    public static final String HOST = "graphql.anilist.co";
    private static final String URL = "https://" + HOST;
    private static final long MIN = 60_000L;
    private static final long HOUR = 60 * MIN;

    private final HttpClient http;

    public AniListSource(HttpClient http) {
        this.http = http;
        http.registerLimiter(HOST, new RateLimiter(28, 60_000, 350));
    }

    public SourceCall<HomeFeed> home(Season.Current now) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("season", now.season);
        vars.put("year", now.year);
        vars.put("perPage", 20);
        return new SourceCall<>("al|home|" + now.season + now.year, 30 * MIN,
                () -> post(AniListQueries.HOME, vars), AniListMapper::parseHome);
    }

    public SourceCall<AnimePage> browse(AnimeQuery q, int page) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("page", page);
        vars.put("perPage", q.perPage);
        boolean searching = q.search != null && !q.search.trim().isEmpty();
        if (searching) vars.put("search", q.search.trim());
        vars.put("sort", sortFor(q.sort, searching));
        // Unset filters must be omitted entirely: AniList treats some explicit nulls as filters.
        if (q.genre != null) vars.put("genre", q.genre);
        if (q.season != null) vars.put("season", q.season);
        if (q.year > 0) vars.put("seasonYear", q.year);
        if (q.format != null) vars.put("format", q.format);
        if (q.status != null) vars.put("status", q.status);
        long ttl = searching ? 30 * MIN : HOUR;
        return new SourceCall<>("al|" + q.cacheKey(page), ttl,
                () -> post(AniListQueries.BROWSE, vars), AniListMapper::parsePage);
    }

    /** Looks up by AniList ID, or by MyAnimeList ID when only that is known (verified cross-reference). */
    public SourceCall<Anime> details(int anilistId, int malId) {
        Map<String, Object> vars = new HashMap<>();
        if (anilistId > 0) vars.put("id", anilistId); else vars.put("idMal", malId);
        String key = anilistId > 0 ? "al|details|" + anilistId : "al|details|mal" + malId;
        return new SourceCall<>(key, 6 * HOUR, () -> post(AniListQueries.DETAILS, vars), AniListMapper::parseDetails);
    }

    static String[] sortFor(AnimeQuery.Sort sort, boolean searching) {
        switch (sort) {
            case TRENDING: return new String[]{"TRENDING_DESC", "POPULARITY_DESC"};
            case SCORE: return new String[]{"SCORE_DESC", "POPULARITY_DESC"};
            case NEWEST: return new String[]{"START_DATE_DESC", "POPULARITY_DESC"};
            case TITLE: return new String[]{"TITLE_ROMAJI"};
            case RELEVANCE:
                return searching ? new String[]{"SEARCH_MATCH", "POPULARITY_DESC"} : new String[]{"POPULARITY_DESC"};
            case POPULARITY:
            default: return new String[]{"POPULARITY_DESC"};
        }
    }

    private String post(String query, Map<String, Object> vars) throws AppError {
        try {
            JSONObject v = new JSONObject();
            for (Map.Entry<String, Object> e : vars.entrySet()) {
                Object val = e.getValue();
                if (val instanceof String[]) {
                    JSONArray a = new JSONArray();
                    for (String s : (String[]) val) a.put(s);
                    val = a;
                }
                v.put(e.getKey(), val);
            }
            JSONObject body = new JSONObject().put("query", query).put("variables", v);
            return http.postJson(URL, body.toString(), Collections.singletonMap("Accept", "application/json"));
        } catch (JSONException e) {
            throw AppError.from(e);
        }
    }
}
