package app.kuro.anime.data;

import org.junit.Test;

import app.kuro.anime.Fixtures;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.AnimePage;
import app.kuro.anime.data.model.Episode;
import app.kuro.anime.data.source.jikan.JikanMapper;
import app.kuro.anime.data.source.kitsu.KitsuMapper;
import app.kuro.anime.data.source.kitsu.KitsuMapping;

import static org.junit.Assert.*;

public class KitsuJikanMapperTest {

    @Test
    public void kitsuMappingResolvesId() throws Exception {
        KitsuMapping m = KitsuMapper.parseMapping(Fixtures.load("kitsu_mapping_frieren.json"));
        assertTrue(m.found());
        assertEquals(46474, m.kitsuId);
        assertEquals(28, m.episodeCount);
        assertNotNull(m.startDate);
        assertEquals(2023, m.startDate.year);
    }

    @Test
    public void kitsuMissingMappingIsNone() throws Exception {
        assertFalse(KitsuMapper.parseMapping(Fixtures.load("kitsu_mapping_none.json")).found());
    }

    @Test
    public void kitsuEpisodes() throws Exception {
        KitsuMapper.EpisodePage p = KitsuMapper.parseEpisodes(Fixtures.load("kitsu_episodes_frieren_0.json"));
        assertEquals(20, p.episodes.size());
        assertEquals(28, p.totalCount);
        for (int i = 0; i < p.episodes.size(); i++) assertEquals(i + 1, p.episodes.get(i).number);
        Episode e1 = p.episodes.get(0);
        assertNotNull(e1.title);
        assertNotNull(e1.airDate);
        int thumbs = 0;
        for (Episode e : p.episodes) if (e.thumbnailUrl != null) thumbs++;
        assertTrue("expected real thumbnails from Kitsu", thumbs > 0);
    }

    @Test
    public void jikanEpisodesCarryFlags() throws Exception {
        JikanMapper.EpisodePage p = JikanMapper.parseEpisodes(Fixtures.load("jikan_episodes_schema_sample.json"));
        assertEquals(3, p.episodes.size());
        assertFalse(p.hasNext);
        assertEquals("Bouken no Owari", p.episodes.get(0).titleAlt);
        assertNull(p.episodes.get(2).airDate);
        assertTrue(p.episodes.get(2).filler);
        assertTrue(p.episodes.get(2).recap);
    }

    @Test
    public void jikanListMapsAndFiltersAdult() throws Exception {
        AnimePage p = JikanMapper.parseList(Fixtures.load("jikan_list_schema_sample.json"));
        assertEquals(1, p.items.size());
        assertTrue(p.hasNext);
        Anime a = p.items.get(0);
        assertEquals(0, a.anilistId);
        assertEquals(52991, a.malId);
        assertEquals("mal:52991", a.key());
        assertEquals("TV", a.format);
        assertEquals("FINISHED", a.status);
        assertEquals(93, a.averageScore);
        assertEquals(24, a.duration);
        assertEquals("FALL", a.season);
        assertFalse(a.synopsis.contains("MAL Rewrite"));
        assertTrue(a.coverUrl.endsWith("l.webp"));
    }
}
