package app.kuro.anime.data.source.jikan;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.kuro.anime.core.AppError;
import app.kuro.anime.core.Text;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.AnimePage;
import app.kuro.anime.data.model.Episode;
import app.kuro.anime.data.model.FuzzyDate;

import static app.kuro.anime.data.source.Json.arr;
import static app.kuro.anime.data.source.Json.bool;
import static app.kuro.anime.data.source.Json.dbl;
import static app.kuro.anime.data.source.Json.integer;
import static app.kuro.anime.data.source.Json.obj;
import static app.kuro.anime.data.source.Json.str;

/** Maps Jikan v4 (unofficial MyAnimeList API) responses into the unified model. */
public final class JikanMapper {
    public static final String SOURCE = "Jikan";
    private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*min");
    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*hr");
    private static final Map<String, String> GENRE_TO_ANILIST = new HashMap<>();

    static {
        GENRE_TO_ANILIST.put("Suspense", "Thriller");
        GENRE_TO_ANILIST.put("Mahou Shoujo", "Mahou Shoujo");
    }

    private JikanMapper() {}

    public static AnimePage parseList(String body) throws JSONException {
        JSONObject root = new JSONObject(body);
        JSONArray data = arr(root, "data");
        List<Anime> items = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            Anime a = parseAnime(data.optJSONObject(i));
            if (a != null) items.add(a);
        }
        JSONObject p = obj(root, "pagination");
        return new AnimePage(items, Math.max(1, integer(p, "current_page")), bool(p, "has_next_page"));
    }

    public static Anime parseDetails(String body) throws JSONException, AppError {
        Anime a = parseAnime(obj(new JSONObject(body), "data"));
        if (a == null) throw new AppError(AppError.Kind.NOT_FOUND, "Not available");
        return a;
    }

    @Nullable
    static Anime parseAnime(@Nullable JSONObject d) {
        if (d == null || integer(d, "mal_id") <= 0) return null;
        String rating = str(d, "rating");
        if (rating != null && rating.startsWith("Rx")) return null; // never surface adult titles
        Anime a = new Anime();
        a.origin = SOURCE;
        a.malId = integer(d, "mal_id");
        a.titleRomaji = str(d, "title");
        a.titleEnglish = str(d, "title_english");
        a.titleNative = str(d, "title_japanese");
        JSONArray syn = arr(d, "title_synonyms");
        for (int i = 0; i < syn.length(); i++) {
            String s = syn.optString(i, null);
            if (!Text.isBlank(s)) a.synonyms.add(s);
        }
        JSONObject images = obj(d, "images");
        JSONObject webp = obj(images, "webp");
        JSONObject jpg = obj(images, "jpg");
        a.coverUrl = Text.firstNonBlank(str(webp, "large_image_url"), str(jpg, "large_image_url"), str(jpg, "image_url"));
        a.coverUrlMedium = Text.firstNonBlank(str(webp, "image_url"), str(jpg, "image_url"), a.coverUrl);
        a.format = format(str(d, "type"));
        a.status = status(str(d, "status"));
        a.episodes = integer(d, "episodes");
        a.duration = minutes(str(d, "duration"));
        a.season = str(d, "season") == null ? null : str(d, "season").toUpperCase(Locale.ROOT);
        a.seasonYear = integer(d, "year");
        double score = dbl(d, "score");
        a.averageScore = score > 0 ? (int) Math.round(score * 10) : 0;
        a.popularity = integer(d, "members");
        a.startDate = FuzzyDate.parseIso(str(obj(d, "aired"), "from"));
        a.endDate = FuzzyDate.parseIso(str(obj(d, "aired"), "to"));
        a.source = str(d, "source");
        String synopsis = str(d, "synopsis");
        if (synopsis != null) a.synopsis = Text.stripHtml(synopsis.replace("[Written by MAL Rewrite]", ""));
        for (String key : new String[]{"genres", "themes", "demographics"}) {
            JSONArray g = arr(d, key);
            for (int i = 0; i < g.length(); i++) {
                String name = str(g.optJSONObject(i), "name");
                if (name == null) continue;
                String mapped = GENRE_TO_ANILIST.containsKey(name) ? GENRE_TO_ANILIST.get(name) : name;
                if (!a.genres.contains(mapped)) a.genres.add(mapped);
            }
        }
        JSONArray studios = arr(d, "studios");
        for (int i = 0; i < studios.length(); i++) {
            String s = str(studios.optJSONObject(i), "name");
            if (s != null) a.studios.add(s);
        }
        return a;
    }

    public static final class EpisodePage {
        public final List<Episode> episodes;
        public final boolean hasNext;
        public final int lastPage;

        EpisodePage(List<Episode> episodes, boolean hasNext, int lastPage) {
            this.episodes = episodes;
            this.hasNext = hasNext;
            this.lastPage = lastPage;
        }
    }

    public static EpisodePage parseEpisodes(String body) throws JSONException {
        JSONObject root = new JSONObject(body);
        JSONArray data = arr(root, "data");
        List<Episode> out = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            int number = integer(o, "mal_id");
            if (number <= 0) continue;
            Episode e = new Episode(number);
            e.title = str(o, "title");
            e.titleAlt = Text.firstNonBlank(str(o, "title_romanji"), str(o, "title_japanese"));
            e.airDate = FuzzyDate.parseIso(str(o, "aired"));
            e.filler = bool(o, "filler");
            e.recap = bool(o, "recap");
            out.add(e);
        }
        JSONObject p = obj(root, "pagination");
        return new EpisodePage(out, bool(p, "has_next_page"), integer(p, "last_visible_page"));
    }

    static String format(@Nullable String type) {
        if (type == null) return null;
        switch (type) {
            case "TV": return "TV";
            case "Movie": return "MOVIE";
            case "OVA": return "OVA";
            case "ONA": return "ONA";
            case "Special":
            case "TV Special": return "SPECIAL";
            case "Music": return "MUSIC";
            default: return null;
        }
    }

    static String status(@Nullable String s) {
        if (s == null) return null;
        switch (s) {
            case "Finished Airing": return "FINISHED";
            case "Currently Airing": return "RELEASING";
            case "Not yet aired": return "NOT_YET_RELEASED";
            default: return null;
        }
    }

    static int minutes(@Nullable String duration) {
        if (duration == null) return 0;
        int total = 0;
        Matcher h = HOURS.matcher(duration);
        if (h.find()) total += Integer.parseInt(h.group(1)) * 60;
        Matcher m = MINUTES.matcher(duration);
        if (m.find()) total += Integer.parseInt(m.group(1));
        return total;
    }
}
