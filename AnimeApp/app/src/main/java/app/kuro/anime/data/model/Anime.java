package app.kuro.anime.data.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Source-independent anime model. Every adapter (AniList, Jikan, Kitsu…) maps into this shape.
 * Identity is anchored on explicit external IDs, never on titles.
 */
public final class Anime {

    // Identity
    public int anilistId;      // 0 when unknown
    public int malId;          // 0 when unknown
    public String origin;      // DataSource name that produced this record

    // Titles
    public String titleRomaji;
    public String titleEnglish;
    public String titleNative;
    public List<String> synonyms = new ArrayList<>();

    // Artwork
    public String coverUrl;        // best quality cover
    public String coverUrlMedium;  // smaller cover for data-saver mode
    public String coverColor;      // dominant hex colour provided by the source, may be null
    public String bannerUrl;

    // Facts
    public String synopsis;
    public String format;   // TV, TV_SHORT, MOVIE, SPECIAL, OVA, ONA, MUSIC
    public String status;   // FINISHED, RELEASING, NOT_YET_RELEASED, CANCELLED, HIATUS
    public int episodes;    // 0 = unknown
    public int duration;    // minutes per episode, 0 = unknown
    public String season;   // WINTER, SPRING, SUMMER, FALL
    public int seasonYear;
    public FuzzyDate startDate;
    public FuzzyDate endDate;
    public int averageScore; // 0..100, 0 = unknown
    public int popularity;
    public String source;    // ORIGINAL, MANGA, LIGHT_NOVEL…
    public List<String> genres = new ArrayList<>();
    public List<String> studios = new ArrayList<>();
    @Nullable public NextAiring nextAiring;

    // Extended details (only populated on the details call)
    public List<Relation> relations = new ArrayList<>();
    public List<CharacterRef> characters = new ArrayList<>();
    public List<StaffRef> staff = new ArrayList<>();
    public List<Anime> recommendations = new ArrayList<>();
    public List<StreamingEpisode> streamingEpisodes = new ArrayList<>();
    public boolean detailed;

    public String key() { return AnimeKey.of(anilistId, malId); }

    public boolean isAiring() { return "RELEASING".equals(status); }

    public boolean isUpcoming() { return "NOT_YET_RELEASED".equals(status); }

    /**
     * Number of episodes that have actually aired (or the total for finished shows), 0 if unknown.
     * For airing shows the next scheduled episode tells us exactly how many have aired.
     */
    public int airedEpisodeCount() {
        if (isUpcoming()) return 0;
        if (nextAiring != null && nextAiring.episode > 0) return nextAiring.episode - 1;
        return episodes;
    }

    public String displayTitle(TitleLanguage lang) {
        String t;
        switch (lang) {
            case ENGLISH: t = firstNonEmpty(titleEnglish, titleRomaji, titleNative); break;
            case NATIVE: t = firstNonEmpty(titleNative, titleRomaji, titleEnglish); break;
            default: t = firstNonEmpty(titleRomaji, titleEnglish, titleNative);
        }
        return t == null ? "" : t;
    }

    /** A secondary title line that differs from the display title, or null. */
    @Nullable
    public String secondaryTitle(TitleLanguage lang) {
        String primary = displayTitle(lang);
        String[] options = lang == TitleLanguage.NATIVE
                ? new String[]{titleRomaji, titleEnglish}
                : new String[]{lang == TitleLanguage.ENGLISH ? titleRomaji : titleEnglish, titleNative};
        for (String o : options) if (o != null && !o.isEmpty() && !o.equalsIgnoreCase(primary)) return o;
        return null;
    }

    public String coverFor(boolean dataSaver) {
        if (dataSaver && coverUrlMedium != null) return coverUrlMedium;
        return coverUrl != null ? coverUrl : coverUrlMedium;
    }

    private static String firstNonEmpty(String... v) {
        for (String s : v) if (s != null && !s.trim().isEmpty()) return s;
        return null;
    }
}
