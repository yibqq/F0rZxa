package app.kuro.anime.data.source.kitsu;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.core.Text;
import app.kuro.anime.data.model.Episode;
import app.kuro.anime.data.model.FuzzyDate;

import static app.kuro.anime.data.source.Json.arr;
import static app.kuro.anime.data.source.Json.integer;
import static app.kuro.anime.data.source.Json.obj;
import static app.kuro.anime.data.source.Json.str;

public final class KitsuMapper {
    public static final String SOURCE = "Kitsu";

    private KitsuMapper() {}

    public static KitsuMapping parseMapping(String body) throws JSONException {
        JSONObject root = new JSONObject(body);
        JSONArray data = arr(root, "data");
        if (data.length() == 0) return KitsuMapping.NONE;
        JSONObject item = obj(obj(obj(data.optJSONObject(0), "relationships"), "item"), "data");
        if (item == null || !"anime".equals(str(item, "type"))) return KitsuMapping.NONE;
        int id;
        try {
            id = Integer.parseInt(str(item, "id"));
        } catch (NumberFormatException | NullPointerException e) {
            return KitsuMapping.NONE;
        }
        JSONArray included = arr(root, "included");
        for (int i = 0; i < included.length(); i++) {
            JSONObject inc = included.optJSONObject(i);
            if (String.valueOf(id).equals(str(inc, "id")) && "anime".equals(str(inc, "type"))) {
                JSONObject at = obj(inc, "attributes");
                return new KitsuMapping(id, integer(at, "episodeCount"), FuzzyDate.parseIso(str(at, "startDate")));
            }
        }
        return new KitsuMapping(id, 0, null);
    }

    public static final class EpisodePage {
        public final List<Episode> episodes;
        public final int totalCount;

        EpisodePage(List<Episode> episodes, int totalCount) {
            this.episodes = episodes;
            this.totalCount = totalCount;
        }
    }

    public static EpisodePage parseEpisodes(String body) throws JSONException {
        JSONObject root = new JSONObject(body);
        JSONArray data = arr(root, "data");
        List<Episode> out = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject at = obj(data.optJSONObject(i), "attributes");
            int number = integer(at, "number");
            if (number <= 0) continue;
            Episode e = new Episode(number);
            JSONObject titles = obj(at, "titles");
            e.title = Text.firstNonBlank(str(titles, "en_us"), str(titles, "en"), str(at, "canonicalTitle"));
            e.titleAlt = Text.firstNonBlank(str(titles, "en_jp"), str(titles, "ja_jp"));
            if (e.titleAlt != null && e.titleAlt.equals(e.title)) e.titleAlt = null;
            e.airDate = FuzzyDate.parseIso(str(at, "airdate"));
            e.thumbnailUrl = thumbnail(obj(at, "thumbnail"));
            e.lengthMinutes = integer(at, "length");
            out.add(e);
        }
        return new EpisodePage(out, integer(obj(root, "meta"), "count"));
    }

    @Nullable
    private static String thumbnail(@Nullable JSONObject t) {
        if (t == null) return null;
        // Prefer a mid-size rendition when Kitsu offers one; fall back to the original.
        return Text.firstNonBlank(str(t, "large"), str(t, "medium"), str(t, "original"));
    }
}
