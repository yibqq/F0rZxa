package app.kuro.anime.data;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

import app.kuro.anime.KuroApp;
import app.kuro.anime.core.AppError;
import app.kuro.anime.core.Fetched;
import app.kuro.anime.data.local.ResponseCache;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.LibraryEntry;
import app.kuro.anime.data.model.WatchStatus;
import app.kuro.anime.data.repo.CachedExecutor;
import app.kuro.anime.data.repo.LibraryRepository;
import app.kuro.anime.data.source.SourceCall;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class LibraryAndCacheTest {

    private LibraryRepository library;
    private ResponseCache cache;

    @Before
    public void setUp() {
        library = KuroApp.get().library();
        library.clearAll();
        cache = KuroApp.get().responseCache();
        cache.clear();
    }

    private static Anime anime(int al, int mal, int episodes, String status) {
        Anime a = new Anime();
        a.anilistId = al;
        a.malId = mal;
        a.titleRomaji = "Title " + al;
        a.episodes = episodes;
        a.status = status;
        return a;
    }

    @Test
    public void statusFavoriteAndOrphanCleanup() {
        Anime a = anime(10, 20, 12, "FINISHED");
        library.setStatus(a, WatchStatus.PLANNING);
        assertEquals(WatchStatus.PLANNING, library.get("al:10").status);
        library.setFavorite(a, true);
        assertTrue(library.get("al:10").favorite);
        library.setStatus(a, null);
        assertNotNull("still a favourite, so the row stays", library.get("al:10"));
        library.setFavorite(a, false);
        assertNull("no status, favourite or progress → removed", library.get("al:10"));
    }

    @Test
    public void watchingAnEpisodeAddsToWatchingAndDetectsCompletion() {
        Anime a = anime(11, 21, 3, "FINISHED");
        assertFalse(library.setWatched(a, 1, 1, true));
        LibraryEntry e = library.get("al:11");
        assertEquals(WatchStatus.WATCHING, e.status);
        assertEquals(1, e.watchedCount);
        assertTrue("all 3 watched", library.setWatched(a, 2, 3, true));
        assertEquals(3, library.watchedEpisodes("al:11").size());
        library.setWatched(a, 2, 2, false);
        assertEquals(2, library.get("al:11").watchedCount);
    }

    @Test
    public void airingShowsAreNeverAutoCompleted() {
        Anime a = anime(12, 22, 2, "RELEASING");
        assertFalse(library.setWatched(a, 1, 2, true));
    }

    @Test
    public void malOnlyEntryMigratesToAniListKeyWithoutDuplicates() {
        Anime fromFallback = anime(0, 52991, 28, "FINISHED");
        library.setWatched(fromFallback, 1, 5, true);
        assertNotNull(library.get("mal:52991"));

        Anime canonical = anime(154587, 52991, 28, "FINISHED");
        assertEquals("found through MAL id", "mal:52991", library.find(canonical).key);
        library.setFavorite(canonical, true);
        assertNull(library.get("mal:52991"));
        LibraryEntry e = library.get("al:154587");
        assertNotNull(e);
        assertTrue(e.favorite);
        assertEquals(5, e.watchedCount);
        assertEquals(1, library.byStatus(null).size());
    }

    @Test
    public void cacheServesFreshWithoutNetworkAndStaleOnFailure() throws Exception {
        CachedExecutor exec = new CachedExecutor(cache);
        AtomicInteger calls = new AtomicInteger();
        SourceCall<String> ok = new SourceCall<>("k1", 60_000, () -> {
            calls.incrementAndGet();
            return "v1";
        }, body -> body);
        assertEquals("v1", exec.execute(ok, false, true).value);
        assertEquals("v1", exec.execute(ok, false, true).value);
        assertEquals("second call is a cache hit", 1, calls.get());

        SourceCall<String> failing = new SourceCall<>("k1", 0, () -> {
            throw new AppError(AppError.Kind.OFFLINE, "down");
        }, body -> body);
        Fetched<String> stale = exec.execute(failing, false, true);
        assertEquals("v1", stale.value);
        assertTrue(stale.stale);
        try {
            exec.execute(failing, false, false);
            fail("stale data not allowed here");
        } catch (AppError e) {
            assertEquals(AppError.Kind.OFFLINE, e.kind);
        }
    }

    @Test
    public void unparseableResponsesAreNeverCached() {
        CachedExecutor exec = new CachedExecutor(cache);
        SourceCall<Integer> bad = new SourceCall<>("k2", 60_000, () -> "not-a-number", Integer::parseInt);
        try {
            exec.execute(bad, false, true);
            fail();
        } catch (AppError e) {
            assertEquals(AppError.Kind.PARSE, e.kind);
        }
        assertNull(cache.get("k2"));
    }
}
