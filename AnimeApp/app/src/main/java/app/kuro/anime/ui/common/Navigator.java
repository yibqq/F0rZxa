package app.kuro.anime.ui.common;

import android.content.Context;
import android.content.Intent;

import app.kuro.anime.core.DeviceProfile;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.AnimeQuery;
import app.kuro.anime.ui.browse.BrowseActivity;
import app.kuro.anime.ui.details.DetailsActivity;
import app.kuro.anime.ui.tv.TvBrowseActivity;
import app.kuro.anime.ui.tv.TvDetailsActivity;

/** Central place for screen navigation; picks the TV or touch experience automatically. */
public final class Navigator {
    public static final String EXTRA_ANILIST_ID = "anilist_id";
    public static final String EXTRA_MAL_ID = "mal_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_COVER = "cover";
    public static final String EXTRA_COLOR = "color";
    public static final String EXTRA_QUERY = "query";

    private Navigator() {}

    public static void openDetails(Context c, Anime a) {
        Class<?> target = DeviceProfile.isTelevision(c) ? TvDetailsActivity.class : DetailsActivity.class;
        Intent i = new Intent(c, target);
        putAnime(i, a);
        c.startActivity(i);
    }

    public static void putAnime(Intent i, Anime a) {
        i.putExtra(EXTRA_ANILIST_ID, a.anilistId);
        i.putExtra(EXTRA_MAL_ID, a.malId);
        i.putExtra(EXTRA_TITLE, a.displayTitle(app.kuro.anime.KuroApp.get().settings().titleLanguage()));
        i.putExtra(EXTRA_COVER, a.coverUrl);
        i.putExtra(EXTRA_COLOR, a.coverColor);
    }

    public static void openBrowse(Context c, String title, AnimeQuery q) {
        Class<?> target = DeviceProfile.isTelevision(c) ? TvBrowseActivity.class : BrowseActivity.class;
        Intent i = new Intent(c, target);
        i.putExtra(EXTRA_TITLE, title);
        i.putExtra(EXTRA_QUERY, q);
        c.startActivity(i);
    }
}
