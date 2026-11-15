package app.kuro.anime.ui;

import android.content.Intent;
import android.view.View;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import app.kuro.anime.KuroApp;
import app.kuro.anime.R;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.WatchStatus;
import app.kuro.anime.ui.common.NavigationBar;
import app.kuro.anime.ui.common.Navigator;
import app.kuro.anime.ui.details.DetailsActivity;
import app.kuro.anime.ui.main.MainActivity;
import app.kuro.anime.ui.tv.TvDetailsActivity;
import app.kuro.anime.ui.tv.TvMainActivity;

import static org.junit.Assert.*;

/**
 * Renders real screens (with recorded API data) across device classes and both layout
 * directions, asserting the adaptive structure and writing PNGs to app/build/screenshots.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 36)
public class ScreenshotTest {

    static final String PHONE = "w411dp-h891dp-port-xxhdpi";
    static final String TABLET = "w1280dp-h800dp-land-xhdpi";
    static final String TV = "w960dp-h540dp-land-television-xhdpi";

    @Before
    public void seed() {
        KuroApp.get().library().clearAll();
        Screens.seedCatalog();
        Anime frieren = new Anime();
        frieren.anilistId = 154587;
        frieren.malId = 52991;
        frieren.titleRomaji = "Sousou no Frieren";
        frieren.titleEnglish = "Frieren: Beyond Journey’s End";
        frieren.coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx154587-qQTzQnEJJ3oB.jpg";
        frieren.coverColor = "#bbf1a1";
        frieren.episodes = 28;
        frieren.status = "FINISHED";
        frieren.format = "TV";
        KuroApp.get().library().setWatched(frieren, 1, 9, true);
        KuroApp.get().library().setFavorite(frieren, true);
    }

    private static Intent details(Class<?> target) {
        Intent i = new Intent(ApplicationProvider.getApplicationContext(), target);
        i.putExtra(Navigator.EXTRA_ANILIST_ID, 154587);
        i.putExtra(Navigator.EXTRA_MAL_ID, 52991);
        i.putExtra(Navigator.EXTRA_TITLE, "Sousou no Frieren");
        return i;
    }

    private void shootMain(String name, int tab, boolean expectRail) throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a -> a.selectTab(tab));
            Screens.settle(7000);
            s.onActivity(a -> {
                NavigationBar nav = a.findViewById(R.id.nav);
                assertEquals(expectRail, nav.isRail());
                try {
                    Screens.capture(a.getWindow().getDecorView(), name);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Test @Config(qualifiers = PHONE)
    public void phoneHome() throws Exception { shootMain("phone_home", NavigationBar.HOME, false); }

    @Test @Config(qualifiers = "ar-" + PHONE)
    public void phoneHomeArabicRtl() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            Screens.settle(7000);
            s.onActivity(a -> {
                assertEquals(View.LAYOUT_DIRECTION_RTL, a.getWindow().getDecorView().getLayoutDirection());
                try {
                    Screens.capture(a.getWindow().getDecorView(), "phone_home_ar");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Test @Config(qualifiers = PHONE)
    public void phoneExplore() throws Exception { shootMain("phone_explore", NavigationBar.EXPLORE, false); }

    @Test @Config(qualifiers = PHONE)
    public void phoneMyList() throws Exception { shootMain("phone_my_list", NavigationBar.MY_LIST, false); }

    @Test @Config(qualifiers = PHONE)
    public void phoneSettings() throws Exception { shootMain("phone_settings", NavigationBar.SETTINGS, false); }

    @Test @Config(qualifiers = PHONE)
    public void phoneSearchWithResults() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a -> a.selectTab(NavigationBar.SEARCH));
            Screens.settle(500);
            s.onActivity(a -> ((EditText) a.findViewById(R.id.search_input)).setText("frieren"));
            Screens.settle(3500);
            s.onActivity(a -> {
                try {
                    Screens.capture(a.getWindow().getDecorView(), "phone_search");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private void shootDetails(String name, Class<? extends android.app.Activity> target) throws Exception {
        try (ActivityScenario<android.app.Activity> s = ActivityScenario.launch(details(target))) {
            Screens.settle(8000);
            s.onActivity(a -> {
                try {
                    Screens.capture(a.getWindow().getDecorView(), name);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Test @Config(qualifiers = PHONE)
    public void phoneDetails() throws Exception { shootDetails("phone_details", DetailsActivity.class); }

    @Test @Config(qualifiers = "ar-" + PHONE)
    public void phoneDetailsArabicRtl() throws Exception { shootDetails("phone_details_ar", DetailsActivity.class); }

    @Test @Config(qualifiers = PHONE)
    public void phoneDetailsEpisodes() throws Exception {
        try (ActivityScenario<DetailsActivity> s = ActivityScenario.launch(details(DetailsActivity.class))) {
            Screens.settle(7000);
            s.onActivity(a -> {
                androidx.recyclerview.widget.RecyclerView list = a.findViewById(R.id.details_list);
                list.scrollToPosition(4);
                list.scrollBy(0, a.getResources().getDisplayMetrics().heightPixels / 3);
            });
            Screens.settle(2500);
            s.onActivity(a -> {
                try {
                    Screens.capture(a.getWindow().getDecorView(), "phone_details_episodes");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Test @Config(qualifiers = TABLET)
    public void tabletHome() throws Exception { shootMain("tablet_home", NavigationBar.HOME, true); }

    @Test @Config(qualifiers = TABLET)
    public void tabletMyListMasterDetail() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a -> a.selectTab(NavigationBar.MY_LIST));
            Screens.settle(1500);
            s.onActivity(a -> {
                androidx.recyclerview.widget.RecyclerView grid = a.findViewById(R.id.list_grid);
                assertNotNull("expanded layout keeps a detail pane", a.findViewById(R.id.detail_container));
                grid.findViewHolderForAdapterPosition(0).itemView.performClick();
            });
            Screens.settle(7000);
            s.onActivity(a -> {
                try {
                    Screens.capture(a.getWindow().getDecorView(), "tablet_my_list_master_detail");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Test @Config(qualifiers = TABLET)
    public void tabletDetails() throws Exception { shootDetails("tablet_details", DetailsActivity.class); }

    /** TVs have no touch mode, so D-pad focus can be placed; emulate that. */
    private static void dpadMode() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
    }

    @Test @Config(qualifiers = TV)
    public void tvHome() throws Exception {
        dpadMode();
        try (ActivityScenario<TvMainActivity> s = ActivityScenario.launch(TvMainActivity.class)) {
            Screens.settle(8000);
            s.onActivity(a -> {
                // Activity#getCurrentFocus is not wired in Robolectric; ask the view tree.
                View focused = a.getWindow().getDecorView().findFocus();
                assertNotNull("TV home must place D-pad focus", focused);
                try {
                    Screens.capture(a.getWindow().getDecorView(), "tv_home");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Test @Config(qualifiers = TV)
    public void tvDetails() throws Exception {
        dpadMode();
        try (ActivityScenario<android.app.Activity> s = ActivityScenario.launch(details(TvDetailsActivity.class))) {
            Screens.settle(8000);
            s.onActivity(a -> {
                View focused = a.getWindow().getDecorView().findFocus();
                assertEquals("primary action gets initial D-pad focus", R.id.tvd_watch, focused == null ? -1 : focused.getId());
                try {
                    Screens.capture(a.getWindow().getDecorView(), "tv_details");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Test @Config(qualifiers = TV)
    public void mainActivityRedirectsToTvShell() {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            Intent next = org.robolectric.Shadows.shadowOf(
                    (android.app.Application) ApplicationProvider.getApplicationContext()).getNextStartedActivity();
            assertNotNull(next);
            assertEquals(TvMainActivity.class.getName(), next.getComponent().getClassName());
        }
    }
}
