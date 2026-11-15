package app.kuro.anime.data.repo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import app.kuro.anime.core.AppError;
import app.kuro.anime.core.Fetched;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.Episode;
import app.kuro.anime.data.model.EpisodeChunk;
import app.kuro.anime.data.model.StreamingEpisode;
import app.kuro.anime.data.source.anilist.AniListMapper;
import app.kuro.anime.data.source.jikan.JikanMapper;
import app.kuro.anime.data.source.jikan.JikanSource;
import app.kuro.anime.data.source.kitsu.KitsuMapper;
import app.kuro.anime.data.source.kitsu.KitsuMapping;
import app.kuro.anime.data.source.kitsu.KitsuSource;

/**
 * Builds episode lists from several sources, merged strictly by episode number:
 * <ol>
 *   <li>Kitsu — titles, air dates, real thumbnails (resolved via Kitsu's AniList/MAL ID mapping)</li>
 *   <li>Jikan — titles, dates, filler/recap flags when Kitsu lacks them</li>
 *   <li>AniList streaming episodes — official partner thumbnails when the number parses exactly</li>
 * </ol>
 * The series cover is never used as an episode image.
 */
public final class EpisodeRepository {

    private final KitsuSource kitsu;
    private final JikanSource jikan;
    private final CachedExecutor exec;

    public EpisodeRepository(KitsuSource kitsu, JikanSource jikan, CachedExecutor exec) {
        this.kitsu = kitsu;
        this.jikan = jikan;
        this.exec = exec;
    }

    public Fetched<EpisodeChunk> loadChunk(Anime anime, int index, boolean force) throws AppError {
        KitsuMapping map = resolveKitsu(anime, force);
        int total = totalEpisodes(anime, map);
        if (total <= 0) return Fetched.fresh(new EpisodeChunk(0, 0, new ArrayList<>(), new ArrayList<>(), false));
        int chunks = EpisodeChunk.chunkCount(total);
        index = Math.max(0, Math.min(index, chunks - 1));
        int first = EpisodeChunk.firstOf(index);
        int last = EpisodeChunk.lastOf(index, total);

        TreeMap<Integer, Episode> eps = new TreeMap<>();
        for (int n = first; n <= last; n++) eps.put(n, new Episode(n));
        List<String> sources = new ArrayList<>();
        boolean stale = false;
        AppError lastError = null;

        if (map.found()) {
            boolean merged = false;
            for (int offset = first - 1; offset < last; offset += KitsuSource.PAGE_LIMIT) {
                try {
                    Fetched<KitsuMapper.EpisodePage> page = exec.execute(kitsu.episodes(map.kitsuId, offset), force, true);
                    stale |= page.stale;
                    merged |= mergeInto(eps, page.value.episodes, false);
                    if (page.value.episodes.size() < KitsuSource.PAGE_LIMIT) break;
                } catch (AppError e) {
                    lastError = e;
                    break;
                }
            }
            if (merged) sources.add(KitsuMapper.SOURCE);
        }

        int missing = 0;
        for (Episode e : eps.values()) if (e.title == null) missing++;
        if (anime.malId > 0 && missing > eps.size() / 2) {
            try {
                // Jikan pages hold 100 episodes, matching our chunk size exactly.
                Fetched<JikanMapper.EpisodePage> page = exec.execute(jikan.episodes(anime.malId, index + 1), force, true);
                stale |= page.stale;
                if (mergeInto(eps, page.value.episodes, true)) sources.add(JikanMapper.SOURCE);
            } catch (AppError e) {
                lastError = e;
            }
        }

        if (applyStreamingThumbnails(eps, anime.streamingEpisodes)) sources.add(AniListMapper.SOURCE);

        boolean anyDetails = false;
        for (Episode e : eps.values()) if (e.hasDetails()) { anyDetails = true; break; }
        if (!anyDetails && lastError != null && lastError.kind != AppError.Kind.NOT_FOUND) throw lastError;

        return new Fetched<>(new EpisodeChunk(index, total, new ArrayList<>(eps.values()), sources, stale), stale, false);
    }

    /** Episodes to list: aired count for airing shows, total for finished ones, 0 before release. */
    static int totalEpisodes(Anime a, KitsuMapping map) {
        if (a.isUpcoming()) return 0;
        if (a.nextAiring != null && a.nextAiring.episode > 0) return a.nextAiring.episode - 1;
        if (a.episodes > 0) return a.episodes;
        if (map.found() && map.episodeCount > 0) return map.episodeCount;
        int max = 0;
        for (StreamingEpisode s : a.streamingEpisodes) max = Math.max(max, s.number);
        return max;
    }

    KitsuMapping resolveKitsu(Anime a, boolean force) {
        KitsuMapping m = KitsuMapping.NONE;
        try {
            if (a.anilistId > 0) m = exec.execute(kitsu.mapping("anilist/anime", a.anilistId), force, true).value;
            if (!m.found() && a.malId > 0) m = exec.execute(kitsu.mapping("myanimelist/anime", a.malId), force, true).value;
        } catch (AppError e) {
            return KitsuMapping.NONE;
        }
        return verified(a, m) ? m : KitsuMapping.NONE;
    }

    /** Sanity check on top of the explicit ID mapping: premiere years must agree. */
    static boolean verified(Anime a, KitsuMapping m) {
        if (!m.found()) return false;
        if (a.startDate == null || m.startDate == null || a.startDate.year <= 0 || m.startDate.year <= 0) return true;
        return Math.abs(a.startDate.year - m.startDate.year) <= 1;
    }

    /** Fills empty fields only (except flags). Returns whether anything useful was merged. */
    static boolean mergeInto(Map<Integer, Episode> target, List<Episode> incoming, boolean takeFlags) {
        boolean merged = false;
        for (Episode in : incoming) {
            Episode e = target.get(in.number);
            if (e == null) continue;
            if (e.title == null && in.title != null) { e.title = in.title; merged = true; }
            if (e.titleAlt == null && in.titleAlt != null) e.titleAlt = in.titleAlt;
            if (e.airDate == null && in.airDate != null) { e.airDate = in.airDate; merged = true; }
            if (e.thumbnailUrl == null && in.thumbnailUrl != null) { e.thumbnailUrl = in.thumbnailUrl; merged = true; }
            if (e.lengthMinutes == 0) e.lengthMinutes = in.lengthMinutes;
            if (takeFlags) {
                e.filler |= in.filler;
                e.recap |= in.recap;
            }
        }
        return merged;
    }

    static boolean applyStreamingThumbnails(Map<Integer, Episode> target, List<StreamingEpisode> streaming) {
        boolean used = false;
        // Ambiguity guard: if a number appears twice (e.g. multiple seasons in one entry), skip it.
        Map<Integer, Integer> counts = new TreeMap<>();
        for (StreamingEpisode s : streaming) if (s.number > 0) counts.merge(s.number, 1, Integer::sum);
        for (StreamingEpisode s : streaming) {
            if (s.number <= 0 || counts.get(s.number) > 1) continue;
            Episode e = target.get(s.number);
            if (e == null) continue;
            if (e.thumbnailUrl == null) { e.thumbnailUrl = s.thumbnailUrl; used = true; }
            if (e.title == null && s.title != null) { e.title = s.title; used = true; }
        }
        return used;
    }
}
