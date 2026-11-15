package app.kuro.anime.ui.main;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import app.kuro.anime.core.DeviceProfile;
import app.kuro.anime.databinding.ActivityMainBinding;
import app.kuro.anime.ui.common.InsetsHelper;
import app.kuro.anime.ui.common.NavigationBar;
import app.kuro.anime.ui.common.ScrollableTab;
import app.kuro.anime.ui.common.TabHost;
import app.kuro.anime.ui.explore.ExploreFragment;
import app.kuro.anime.ui.home.HomeFragment;
import app.kuro.anime.ui.list.MyListFragment;
import app.kuro.anime.ui.search.SearchFragment;
import app.kuro.anime.ui.settings.SettingsFragment;
import app.kuro.anime.ui.tv.TvMainActivity;

/**
 * Touch shell for phones, foldables and tablets. The layout (bottom bar vs. rail) is chosen by
 * window width via resource qualifiers, so it follows split-screen and fold/unfold changes.
 * Tabs are kept alive with show/hide so each keeps its scroll position and filters.
 */
public class MainActivity extends AppCompatActivity implements TabHost {

    private static final String STATE_TAB = "tab";
    private ActivityMainBinding b;
    private int current = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DeviceProfile.isTelevision(this)) {
            startActivity(new Intent(this, TvMainActivity.class));
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        boolean rail = b.nav.isRail();
        InsetsHelper.pad(b.nav, rail ? (InsetsHelper.TOP | InsetsHelper.BOTTOM | InsetsHelper.START)
                : (InsetsHelper.BOTTOM | InsetsHelper.START | InsetsHelper.END));
        InsetsHelper.pad(b.content, rail ? InsetsHelper.END : (InsetsHelper.START | InsetsHelper.END));

        b.nav.setOnItemSelected((index, reselected) -> {
            if (reselected) {
                Fragment f = getSupportFragmentManager().findFragmentByTag(tag(index));
                if (f instanceof ScrollableTab) ((ScrollableTab) f).scrollToTop();
            } else {
                show(index);
            }
        });

        int tab = savedInstanceState != null ? savedInstanceState.getInt(STATE_TAB, 0) : 0;
        show(tab);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (current != NavigationBar.HOME) {
                    show(NavigationBar.HOME);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    @Override
    public void selectTab(int index) { show(index); }

    private void show(int index) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true)
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        for (int i = 0; i <= NavigationBar.SETTINGS; i++) {
            Fragment f = fm.findFragmentByTag(tag(i));
            if (i == index) {
                if (f == null) tx.add(b.content.getId(), create(i), tag(i));
                else tx.show(f);
            } else if (f != null && !f.isHidden()) {
                tx.hide(f);
            }
        }
        tx.commitNow();
        current = index;
        b.nav.select(index, false);
    }

    private static Fragment create(int index) {
        switch (index) {
            case NavigationBar.EXPLORE: return new ExploreFragment();
            case NavigationBar.SEARCH: return new SearchFragment();
            case NavigationBar.MY_LIST: return new MyListFragment();
            case NavigationBar.SETTINGS: return new SettingsFragment();
            case NavigationBar.HOME:
            default: return new HomeFragment();
        }
    }

    private static String tag(int index) { return "tab-" + index; }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_TAB, current < 0 ? 0 : current);
    }
}
