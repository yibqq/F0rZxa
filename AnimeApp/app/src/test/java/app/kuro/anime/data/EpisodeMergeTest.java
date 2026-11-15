package app.kuro.anime.data;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import app.kuro.anime.Fixtures;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.Episode;
import app.kuro.anime.data.model.EpisodeChunk;
import app.kuro.anime.data.model.FuzzyDate;
import app.kuro.anime.data.model.NextAiring;
import app.kuro.anime.data.model.StreamingEpisode;
import app.kuro.anime.data.repo.EpisodeRepositoryTestAccess;
import app.kuro.anime.data.source.kitsu.KitsuMapper;
import app.kuro.anime.data.source.kitsu.KitsuMapping;

import static org.junit.Assert.*;

public class EpisodeMergeTest {

    private static TreeMap<Integer, Episode> blank(int from, int to) {
        TreeMap<Integer, Episode> m = new TreeMap<>();
        for (int i = from; i <= to; i++) m.put(i, new Episode(i));
        return m;
    }

    @Test
    public void totalEpisodesRules() {
        Anime upcoming = new Anime();
        upcoming.status = "NOT_YET_RELEASED";
        upcoming.episodes = 12;
        assertEquals(0, EpisodeRepositoryTestAccess.total(upcoming, KitsuMapping.NONE));

        Anime airing = new Anime();
        airing.status = "RELEASING";
        airing.episodes = 24;
        airing.nextAiring = new NextAiring(8, 0);
        assertEquals("only aired episodes are listed", 7, EpisodeRepositoryTestAccess.total(airing, KitsuMapping.NONE));

        Anime finished = new Anime();
        finished.status = "FINISHED";
        finished.episodes = 28;
        assertEquals(28, EpisodeRepositoryTestAccess.total(finished, KitsuMapping.NONE));

        Anime unknown = new Anime();
        unknown.status = "FINISHED";
        assertEquals(13, EpisodeRepositoryTestAccess.total(unknown, new KitsuMapping(5, 13, null)));
    }

    @Test
    public void mergeFillsOnlyMissingFieldsByNumber() throws Exception {
        TreeMap<Integer, Episode> target = blank(1, 28);
        target.get(1).title = "Existing title";
        List<Episode> kitsu = KitsuMapper.parseEpisodes(Fixtures.load("kitsu_episodes_frieren_0.json")).episodes;
        assertTrue(EpisodeRepositoryTestAccess.merge(target, kitsu, false));
        assertEquals("Existing title", target.get(1).title);
        assertNotNull(target.get(2).title);
        assertNull("episode 21+ not in this page", target.get(21).title);
        // Episodes outside the requested range are ignored rather than added.
        assertEquals(28, target.size());
    }

    @Test
    public void streamingThumbnailsSkipAmbiguousNumbers() {
        TreeMap<Integer, Episode> target = blank(1, 3);
        List<StreamingEpisode> s = new ArrayList<>(Arrays.asList(
                new StreamingEpisode(1, "A", "thumb1"),
                new StreamingEpisode(2, "B", "thumb2a"),
                new StreamingEpisode(2, "B (S2)", "thumb2b"),
                new StreamingEpisode(0, "Special", "thumbX")));
        EpisodeRepositoryTestAccess.streaming(target, s);
        assertEquals("thumb1", target.get(1).thumbnailUrl);
        assertNull("duplicate number 2 must not be guessed", target.get(2).thumbnailUrl);
        assertNull(target.get(3).thumbnailUrl);
    }

    @Test
    public void kitsuMappingYearVerification() {
        Anime a = new Anime();
        a.startDate = new FuzzyDate(2023, 9, 29);
        assertTrue(EpisodeRepositoryTestAccess.verified(a, new KitsuMapping(1, 28, new FuzzyDate(2023, 9, 29))));
        assertTrue(EpisodeRepositoryTestAccess.verified(a, new KitsuMapping(1, 28, new FuzzyDate(2024, 1, 1))));
        assertFalse(EpisodeRepositoryTestAccess.verified(a, new KitsuMapping(1, 28, new FuzzyDate(2010, 1, 1))));
        assertFalse(EpisodeRepositoryTestAccess.verified(a, KitsuMapping.NONE));
    }

    @Test
    public void chunkMath() {
        assertEquals(0, EpisodeChunk.chunkCount(0));
        assertEquals(1, EpisodeChunk.chunkCount(28));
        assertEquals(12, EpisodeChunk.chunkCount(1155));
        assertEquals(1101, EpisodeChunk.firstOf(11));
        assertEquals(1155, EpisodeChunk.lastOf(11, 1155));
        assertEquals(200, EpisodeChunk.lastOf(1, 1155));
    }
}
