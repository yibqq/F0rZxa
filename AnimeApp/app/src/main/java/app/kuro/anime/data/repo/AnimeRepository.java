package app.kuro.anime.data.repo;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.kuro.anime.core.AppError;
import app.kuro.anime.core.Fetched;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.AnimePage;
import app.kuro.anime.data.model.AnimeQuery;
import app.kuro.anime.data.model.HomeFeed;
import app.kuro.anime.data.model.Season;
import app.kuro.anime.data.model.SectionType;
import app.kuro.anime.data.source.SourceCall;
import app.kuro.anime.data.source.anilist.AniListSource;
import app.kuro.anime.data.source.jikan.JikanSource;

/**
 * Catalog access with source fallback.
 * Order: AniList (fresh cache → network) → Jikan when AniList itself is failing → stale cache.
 * Results are de-duplicated by stable ID; records are never merged on title similarity.
 */
public final class AnimeRepository {

    private final AniListSource anilist;
    private final JikanSource jikan;
    private final CachedExecutor exec;

    public AnimeRepository(AniListSource anilist, JikanSource jikan, CachedExecutor exec) {
        this.anilist = anilist;
        this.jikan = jikan;
        this.exec = exec;
    }

    public Fetched<HomeFeed> home(boolean force) throws AppError {
        Season.Current now = Season.now();
        return withFallback(anilist.home(now), () -> jikanHome(now, force), force);
    }

    public Fetched<AnimePage> browse(AnimeQuery query, int page, boolean force) throws AppError {
        SourceCall<AnimePage> fb = page == 1 ? jikan.browse(query, page) : null; // never mix sources across pages
        Fetched<AnimePage> f = withFallback(anilist.browse(query, page),
                fb == null ? null : () -> exec.execute(fb, force, true), force);
        return f.map(dedupe(f.value));
    }

    public Fetched<Anime> details(int anilistId, int malId, boolean force) throws AppError {
        Fallback<Anime> fb = malId > 0 ? () -> exec.execute(jikan.details(malId), force, true) : null;
        return withFallback(anilist.details(anilistId, malId), fb, force);
    }

    /** Cached details without touching the network (for instant display). */
    @Nullable
    public Anime cachedDetails(int anilistId, int malId) {
        Fetched<Anime> f = exec.cachedOnly(anilist.details(anilistId, malId));
        return f == null ? null : f.value;
    }

    private interface Fallback<T> { Fetched<T> get() throws AppError; }

    private <T> Fetched<T> withFallback(SourceCall<T> primary, @Nullable Fallback<T> fallback, boolean force)
            throws AppError {
        try {
            return exec.execute(primary, force, false);
        } catch (AppError primaryError) {
            Fetched<T> stale = primaryError.kind == AppError.Kind.NOT_FOUND ? null : exec.cachedOnly(primary);
            if (fallback != null && primaryError.isSourceFailure()) {
                try {
                    Fetched<T> f = fallback.get();
                    return new Fetched<>(f.value, f.stale, true);
                } catch (AppError ignored) {
                    // fall through to stale data / original error
                }
            }
            if (stale != null) return stale;
            throw primaryError;
        }
    }

    private Fetched<HomeFeed> jikanHome(Season.Current now, boolean force) throws AppError {
        HomeFeed feed = new HomeFeed();
        AppError last = null;
        boolean stale = false;
        Object[][] plan = {
                {SectionType.SEASONAL, jikan.season(now.year, now.season, 1)},
                {SectionType.AIRING, jikan.top("airing", 1)},
                {SectionType.TOP_RATED, jikan.top(null, 1)},
                {SectionType.UPCOMING, jikan.top("upcoming", 1)},
                {SectionType.POPULAR, jikan.top("bypopularity", 1)},
        };
        for (Object[] step : plan) {
            try {
                @SuppressWarnings("unchecked")
                Fetched<AnimePage> p = exec.execute((SourceCall<AnimePage>) step[1], force, true);
                stale |= p.stale;
                List<Anime> items = dedupe(p.value).items;
                if (!items.isEmpty()) feed.sections.add(new HomeFeed.Section((SectionType) step[0], items));
            } catch (AppError e) {
                last = e;
            }
        }
        if (feed.sections.isEmpty()) throw last != null ? last : new AppError(AppError.Kind.UNKNOWN, "Empty feed");
        return new Fetched<>(feed, stale, true);
    }

    static AnimePage dedupe(AnimePage page) {
        Map<String, Anime> seen = new LinkedHashMap<>();
        for (Anime a : page.items) {
            if (a.anilistId <= 0 && a.malId <= 0) continue;
            seen.putIfAbsent(a.key(), a);
        }
        return new AnimePage(new ArrayList<>(seen.values()), page.page, page.hasNext);
    }
}
