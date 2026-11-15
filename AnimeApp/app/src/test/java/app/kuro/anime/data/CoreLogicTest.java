package app.kuro.anime.data;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.core.Text;
import app.kuro.anime.core.WindowSizeClass;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.AnimeQuery;
import app.kuro.anime.data.model.Season;
import app.kuro.anime.data.model.TitleLanguage;
import app.kuro.anime.data.net.RateLimiter;
import app.kuro.anime.ui.common.GridAutoFit;

import static org.junit.Assert.*;

public class CoreLogicTest {

    @Test
    public void stripHtml() {
        assertEquals("Line one\nLine two & \"quoted\" — ok",
                Text.stripHtml("Line one<br>Line two &amp; &quot;quoted&quot; &mdash; <i>ok</i>"));
        assertEquals("a\n\nb", Text.stripHtml("a<br><br><br><br>b"));
        assertEquals("é", Text.stripHtml("&#233;"));
        assertNull(Text.stripHtml(null));
    }

    @Test
    public void windowSizeClasses() {
        assertEquals(WindowSizeClass.COMPACT, WindowSizeClass.fromWidthDp(411));
        assertEquals(WindowSizeClass.MEDIUM, WindowSizeClass.fromWidthDp(600));
        assertEquals(WindowSizeClass.MEDIUM, WindowSizeClass.fromWidthDp(839));
        assertEquals(WindowSizeClass.EXPANDED, WindowSizeClass.fromWidthDp(840));
    }

    @Test
    public void gridSpans() {
        assertEquals(2, GridAutoFit.spansFor(0, 300, 30));
        assertEquals(3, GridAutoFit.spansFor(1000, 300, 30));
        assertEquals(2, GridAutoFit.spansFor(500, 300, 30));
    }

    @Test
    public void seasons() {
        assertEquals("WINTER", Season.of(2026, 1).season);
        assertEquals("SPRING", Season.of(2026, 4).season);
        assertEquals("SUMMER", Season.of(2026, 9).season);
        assertEquals("FALL", Season.of(2026, 11).season);
    }

    @Test
    public void queryIdentityAndCacheKey() {
        AnimeQuery a = new AnimeQuery();
        a.genre = "Action";
        AnimeQuery b = a.copy();
        assertEquals(a, b);
        assertEquals(a.cacheKey(2), b.cacheKey(2));
        b.year = 2024;
        assertNotEquals(a, b);
        assertNotEquals(a.cacheKey(1), b.cacheKey(1));
        assertNotEquals(a.cacheKey(1), a.cacheKey(2));
    }

    @Test
    public void titlesFallBackGracefully() {
        Anime x = new Anime();
        x.titleRomaji = "Sousou no Frieren";
        x.titleNative = "葬送のフリーレン";
        assertEquals("Sousou no Frieren", x.displayTitle(TitleLanguage.ENGLISH));
        assertEquals("葬送のフリーレン", x.displayTitle(TitleLanguage.NATIVE));
        assertEquals("葬送のフリーレン", x.secondaryTitle(TitleLanguage.ROMAJI));
    }

    @Test
    public void rateLimiterSpacesAndCapsRequests() throws Exception {
        FakeClock clock = new FakeClock();
        RateLimiter l = new RateLimiter(3, 1_000, 100, clock);
        for (int i = 0; i < 3; i++) l.acquire();
        // 3 requests spaced by the 100ms minimum interval
        assertEquals(200, clock.now);
        l.acquire(); // 4th must wait for the 1s window to roll over (first stamp at t=0)
        assertTrue("window cap enforced, t=" + clock.now, clock.now >= 1_000);
        l.pauseFor(5_000);
        long before = clock.now;
        l.acquire();
        assertTrue(clock.now - before >= 5_000);
    }

    static final class FakeClock implements RateLimiter.Clock {
        long now;
        final List<Long> sleeps = new ArrayList<>();

        @Override public long now() { return now; }

        @Override
        public void sleep(long ms) {
            sleeps.add(ms);
            now += ms;
        }
    }
}
