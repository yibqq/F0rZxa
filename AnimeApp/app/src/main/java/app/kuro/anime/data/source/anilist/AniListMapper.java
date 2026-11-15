package app.kuro.anime.data.source.anilist;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.kuro.anime.core.AppError;
import app.kuro.anime.core.Text;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.AnimePage;
import app.kuro.anime.data.model.CharacterRef;
import app.kuro.anime.data.model.FuzzyDate;
import app.kuro.anime.data.model.HomeFeed;
import app.kuro.anime.data.model.NextAiring;
import app.kuro.anime.data.model.Relation;
import app.kuro.anime.data.model.SectionType;
import app.kuro.anime.data.model.StaffRef;
import app.kuro.anime.data.model.StreamingEpisode;

import static app.kuro.anime.data.source.Json.arr;
import static app.kuro.anime.data.source.Json.bool;
import static app.kuro.anime.data.source.Json.integer;
import static app.kuro.anime.data.source.Json.lng;
import static app.kuro.anime.data.source.Json.obj;
import static app.kuro.anime.data.source.Json.str;

/** Maps AniList GraphQL responses into the unified model. Pure Java; covered by unit tests. */
public final class AniListMapper {

    public static final String SOURCE = "AniList";
    private static final Pattern STREAM_EP = Pattern.compile("^Episode\\s+(\\d+)\\s*[-–:]\\s*(.+)$");

    private AniListMapper() {}

    static JSONObject data(String body) throws JSONException, AppError {
        JSONObject root = new JSONObject(body);
        JSONArray errors = arr(root, "errors");
        JSONObject data = obj(root, "data");
        if (data == null) {
            String msg = errors.length() > 0 ? str(errors.optJSONObject(0), "message") : "No data";
            int status = errors.length() > 0 ? integer(errors.optJSONObject(0), "status") : 0;
            throw new AppError(status == 404 ? AppError.Kind.NOT_FOUND : AppError.Kind.SERVER, msg);
        }
        return data;
    }

    public static HomeFeed parseHome(String body) throws JSONException, AppError {
        JSONObject data = data(body);
        HomeFeed feed = new HomeFeed();
        addSection(feed, SectionType.TRENDING, data, "trending");
        addSection(feed, SectionType.SEASONAL, data, "seasonal");
        addSection(feed, SectionType.AIRING, data, "airing");
        addSection(feed, SectionType.TOP_RATED, data, "top");
        addSection(feed, SectionType.UPCOMING, data, "upcoming");
        addSection(feed, SectionType.POPULAR, data, "popular");
        return feed;
    }

    private static void addSection(HomeFeed feed, SectionType type, JSONObject data, String alias) {
        List<Anime> items = parseMediaList(arr(obj(data, alias), "media"));
        if (!items.isEmpty()) feed.sections.add(new HomeFeed.Section(type, items));
    }

    public static AnimePage parsePage(String body) throws JSONException, AppError {
        JSONObject page = obj(data(body), "Page");
        JSONObject info = obj(page, "pageInfo");
        return new AnimePage(parseMediaList(arr(page, "media")),
                Math.max(1, integer(info, "currentPage")), bool(info, "hasNextPage"));
    }

    public static Anime parseDetails(String body) throws JSONException, AppError {
        JSONObject m = obj(data(body), "Media");
        if (m == null) throw new AppError(AppError.Kind.NOT_FOUND, "Media missing");
        if (bool(m, "isAdult")) throw new AppError(AppError.Kind.NOT_FOUND, "Adult content is not shown");
        Anime a = parseCard(m);
        a.detailed = true;
        JSONArray syn = arr(m, "synonyms");
        for (int i = 0; i < syn.length(); i++) {
            String s = syn.optString(i, null);
            if (!Text.isBlank(s)) a.synonyms.add(s.trim());
        }
        a.synopsis = Text.stripHtml(str(m, "description"));
        a.source = str(m, "source");
        if (a.averageScore == 0) a.averageScore = integer(m, "meanScore");
        a.endDate = date(obj(m, "endDate"));

        JSONArray rel = arr(obj(m, "relations"), "edges");
        for (int i = 0; i < rel.length(); i++) {
            JSONObject e = rel.optJSONObject(i);
            JSONObject node = obj(e, "node");
            // Only anime relations are navigable inside this app.
            if (node == null || !"ANIME".equals(str(node, "type"))) continue;
            a.relations.add(new Relation(str(e, "relationType"), parseCard(node)));
        }

        JSONArray chars = arr(obj(m, "characters"), "edges");
        for (int i = 0; i < chars.length(); i++) {
            JSONObject e = chars.optJSONObject(i);
            JSONObject node = obj(e, "node");
            if (node == null) continue;
            CharacterRef c = new CharacterRef();
            c.id = integer(node, "id");
            c.name = str(obj(node, "name"), "full");
            c.nativeName = str(obj(node, "name"), "native");
            c.imageUrl = str(obj(node, "image"), "large");
            c.role = str(e, "role");
            JSONArray vas = arr(e, "voiceActors");
            if (vas.length() > 0) {
                JSONObject va = vas.optJSONObject(0);
                c.voiceActorName = str(obj(va, "name"), "full");
                c.voiceActorImageUrl = str(obj(va, "image"), "large");
            }
            if (c.name != null) a.characters.add(c);
        }

        JSONArray staff = arr(obj(m, "staff"), "edges");
        for (int i = 0; i < staff.length(); i++) {
            JSONObject e = staff.optJSONObject(i);
            JSONObject node = obj(e, "node");
            if (node == null) continue;
            StaffRef s = new StaffRef();
            s.id = integer(node, "id");
            s.name = str(obj(node, "name"), "full");
            s.imageUrl = str(obj(node, "image"), "large");
            s.role = str(e, "role");
            if (s.name != null) a.staff.add(s);
        }

        JSONArray recs = arr(obj(m, "recommendations"), "nodes");
        for (int i = 0; i < recs.length(); i++) {
            JSONObject r = obj(recs.optJSONObject(i), "mediaRecommendation");
            if (r != null) a.recommendations.add(parseCard(r));
        }

        a.streamingEpisodes.addAll(parseStreamingEpisodes(arr(m, "streamingEpisodes")));
        return a;
    }

    static List<StreamingEpisode> parseStreamingEpisodes(JSONArray list) {
        List<StreamingEpisode> out = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject s = list.optJSONObject(i);
            String title = str(s, "title");
            String thumb = str(s, "thumbnail");
            if (title == null || thumb == null) continue;
            StreamingEpisode ep = parseStreamingTitle(title, thumb);
            out.add(ep);
        }
        return out;
    }

    /** "Episode 12 - Title" → number 12. Anything ambiguous gets number 0 and is never matched. */
    static StreamingEpisode parseStreamingTitle(String title, String thumb) {
        Matcher mt = STREAM_EP.matcher(title.trim());
        if (mt.matches()) {
            try {
                return new StreamingEpisode(Integer.parseInt(mt.group(1)), mt.group(2).trim(), thumb);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return new StreamingEpisode(0, title, thumb);
    }

    static List<Anime> parseMediaList(JSONArray media) {
        List<Anime> out = new ArrayList<>();
        for (int i = 0; i < media.length(); i++) {
            JSONObject m = media.optJSONObject(i);
            if (m != null && integer(m, "id") > 0) out.add(parseCard(m));
        }
        return out;
    }

    static Anime parseCard(JSONObject m) {
        Anime a = new Anime();
        a.origin = SOURCE;
        a.anilistId = integer(m, "id");
        a.malId = integer(m, "idMal");
        JSONObject t = obj(m, "title");
        a.titleRomaji = str(t, "romaji");
        a.titleEnglish = str(t, "english");
        a.titleNative = str(t, "native");
        JSONObject cover = obj(m, "coverImage");
        a.coverUrl = Text.firstNonBlank(str(cover, "extraLarge"), str(cover, "large"));
        a.coverUrlMedium = Text.firstNonBlank(str(cover, "large"), a.coverUrl);
        a.coverColor = str(cover, "color");
        a.bannerUrl = str(m, "bannerImage");
        a.format = str(m, "format");
        a.status = str(m, "status");
        a.episodes = integer(m, "episodes");
        a.duration = integer(m, "duration");
        a.season = str(m, "season");
        a.seasonYear = integer(m, "seasonYear");
        a.averageScore = integer(m, "averageScore");
        a.popularity = integer(m, "popularity");
        a.startDate = date(obj(m, "startDate"));
        JSONArray genres = arr(m, "genres");
        for (int i = 0; i < genres.length(); i++) {
            String g = genres.optString(i, null);
            if (!Text.isBlank(g)) a.genres.add(g);
        }
        JSONArray studios = arr(obj(m, "studios"), "nodes");
        for (int i = 0; i < studios.length(); i++) {
            String s = str(studios.optJSONObject(i), "name");
            if (s != null && !a.studios.contains(s)) a.studios.add(s);
        }
        JSONObject next = obj(m, "nextAiringEpisode");
        if (next != null && integer(next, "episode") > 0) {
            a.nextAiring = new NextAiring(integer(next, "episode"), lng(next, "airingAt"));
        }
        return a;
    }

    @Nullable
    private static FuzzyDate date(@Nullable JSONObject d) {
        if (d == null || integer(d, "year") <= 0) return null;
        return new FuzzyDate(integer(d, "year"), integer(d, "month"), integer(d, "day"));
    }
}
