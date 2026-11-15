package app.kuro.anime.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;

import app.kuro.anime.Fixtures;
import app.kuro.anime.KuroApp;
import app.kuro.anime.data.local.ResponseCache;
import app.kuro.anime.data.model.AnimeQuery;
import app.kuro.anime.data.model.Season;

import static org.robolectric.Shadows.shadowOf;

/** Seeds the response cache with recorded API data and captures rendered screens. */
final class Screens {
    private Screens() {}

    static void seedCatalog() {
        ResponseCache c = KuroApp.get().responseCache();
        Season.Current now = Season.now();
        c.put("al|home|" + now.season + now.year, Fixtures.load("anilist_home.json"));
        c.put("al|details|154587", Fixtures.load("anilist_details_frieren.json"));
        c.put("al|details|21", Fixtures.load("anilist_details_onepiece.json"));
        c.put("kitsu|map|anilist/anime|154587", Fixtures.load("kitsu_mapping_frieren.json"));
        c.put("kitsu|eps|46474|0", Fixtures.load("kitsu_episodes_frieren_0.json"));
        c.put("kitsu|eps|46474|20", Fixtures.load("kitsu_episodes_frieren_20.json"));
        AnimeQuery explore = new AnimeQuery();
        explore.perPage = 30;
        c.put("al|" + explore.cacheKey(1), Fixtures.load("anilist_browse_action_2024.json"));
        AnimeQuery search = new AnimeQuery();
        search.search = "frieren";
        search.sort = AnimeQuery.Sort.RELEVANCE;
        search.perPage = 24;
        c.put("al|" + search.cacheKey(1), Fixtures.load("anilist_search_frieren.json"));
    }

    /** Lets background work, Glide image loads and animations settle. */
    static void settle(long millis) {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    static File capture(View root, String name) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.EXACTLY));
        Bitmap bmp = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bmp));
        String dir = System.getProperty("kuro.screenshots.dir", "build/screenshots");
        File out = new File(dir, name + ".png");
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        return out;
    }
}
