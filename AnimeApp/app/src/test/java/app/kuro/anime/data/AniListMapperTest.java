package app.kuro.anime.data;

import org.junit.Test;

import app.kuro.anime.Fixtures;
import app.kuro.anime.core.AppError;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.AnimePage;
import app.kuro.anime.data.model.HomeFeed;
import app.kuro.anime.data.model.Relation;
import app.kuro.anime.data.model.SectionType;
import app.kuro.anime.data.model.StreamingEpisode;
import app.kuro.anime.data.source.anilist.AniListMapper;

import static org.junit.Assert.*;

public class AniListMapperTest {

    @Test
    public void homeHasAllSectionsWithIds() throws Exception {
        HomeFeed feed = AniListMapper.parseHome(Fixtures.load("anilist_home.json"));
        for (SectionType t : SectionType.values()) {
            HomeFeed.Section s = feed.get(t);
            assertNotNull("missing section " + t, s);
            assertFalse(s.items.isEmpty());
            for (Anime a : s.items) {
                assertTrue(a.anilistId > 0);
                assertNotNull(a.coverUrl);
                assertNotNull(a.displayTitle(app.kuro.anime.data.model.TitleLanguage.ROMAJI));
            }
        }
        for (Anime a : feed.get(SectionType.UPCOMING).items) assertEquals("NOT_YET_RELEASED", a.status);
        for (Anime a : feed.get(SectionType.AIRING).items) assertEquals("RELEASING", a.status);
    }

    @Test
    public void detailsMapsEverything() throws Exception {
        Anime a = AniListMapper.parseDetails(Fixtures.load("anilist_details_frieren.json"));
        assertEquals(154587, a.anilistId);
        assertEquals(52991, a.malId);
        assertTrue(a.detailed);
        assertEquals("FINISHED", a.status);
        assertEquals(28, a.episodes);
        assertNotNull(a.synopsis);
        assertFalse("html must be stripped", a.synopsis.contains("<br"));
        assertFalse(a.synopsis.contains("<i>"));
        assertFalse(a.characters.isEmpty());
        assertNotNull(a.characters.get(0).name);
        assertEquals("MAIN", a.characters.get(0).role);
        assertFalse(a.staff.isEmpty());
        assertFalse(a.recommendations.isEmpty());
        assertTrue(a.studios.contains("MADHOUSE"));
        for (Relation r : a.relations) assertNotNull(r.anime.anilistId);
        assertFalse(a.streamingEpisodes.isEmpty());
        StreamingEpisode first = null;
        for (StreamingEpisode s : a.streamingEpisodes) if (s.number == 1) first = s;
        assertNotNull("Episode 1 must be parsed from 'Episode 1 - …'", first);
        assertEquals("The Journey's End", first.title);
    }

    @Test
    public void airingShowCountsOnlyAiredEpisodes() throws Exception {
        Anime a = AniListMapper.parseDetails(Fixtures.load("anilist_details_onepiece.json"));
        assertEquals("RELEASING", a.status);
        assertNotNull(a.nextAiring);
        assertEquals(a.nextAiring.episode - 1, a.airedEpisodeCount());
    }

    @Test
    public void searchPage() throws Exception {
        AnimePage p = AniListMapper.parsePage(Fixtures.load("anilist_search_frieren.json"));
        assertEquals(1, p.page);
        assertFalse(p.items.isEmpty());
        assertTrue(p.items.get(0).titleRomaji.contains("Frieren"));
    }

    @Test
    public void browsePageRespectsFilters() throws Exception {
        AnimePage p = AniListMapper.parsePage(Fixtures.load("anilist_browse_action_2024.json"));
        assertFalse(p.items.isEmpty());
        for (Anime a : p.items) {
            assertEquals("TV", a.format);
            assertEquals(2024, a.seasonYear);
            assertTrue(a.genres.contains("Action"));
        }
    }

    @Test
    public void graphQlErrorsBecomeAppErrors() {
        try {
            AniListMapper.parseDetails("{\"errors\":[{\"message\":\"Not Found.\",\"status\":404}],\"data\":null}");
            fail();
        } catch (AppError e) {
            assertEquals(AppError.Kind.NOT_FOUND, e.kind);
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    @Test
    public void adultTitlesAreRejected() {
        try {
            AniListMapper.parseDetails("{\"data\":{\"Media\":{\"id\":1,\"isAdult\":true,\"title\":{\"romaji\":\"x\"}}}}");
            fail();
        } catch (AppError e) {
            assertEquals(AppError.Kind.NOT_FOUND, e.kind);
        } catch (Exception e) {
            fail(e.toString());
        }
    }
}
