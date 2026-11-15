package app.kuro.anime.ui.tv;

import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.R;
import app.kuro.anime.databinding.ActivityTvMainBinding;
import app.kuro.anime.databinding.ItemTvMenuBinding;
import app.kuro.anime.ui.common.Images;
import app.kuro.anime.ui.settings.SettingsFragment;

/**
 * Android TV / Google TV shell: a side menu and content panes, fully operable with a D-pad.
 * Back from content returns to the menu; back from the menu exits.
 */
public class TvMainActivity extends AppCompatActivity implements TvBackdropHost {

    private static final int[] ICONS = {R.drawable.ic_home, R.drawable.ic_search, R.drawable.ic_bookmark, R.drawable.ic_settings};
    private static final int[] LABELS = {R.string.nav_home, R.string.nav_search, R.string.nav_my_list, R.string.nav_settings};
    private static final String STATE_TAB = "tv_tab";

    private ActivityTvMainBinding b;
    private final List<View> menuItems = new ArrayList<>();
    private int current = -1;
    private String backdropUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityTvMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        for (int i = 0; i < ICONS.length; i++) {
            final int index = i;
            ItemTvMenuBinding m = ItemTvMenuBinding.inflate(getLayoutInflater(), b.tvMenu, false);
            m.menuIcon.setImageResource(ICONS[i]);
            m.menuLabel.setText(LABELS[i]);
            m.getRoot().setContentDescription(getString(LABELS[i]));
            m.getRoot().setOnClickListener(v -> show(index, true));
            b.tvMenu.addView(m.getRoot());
            menuItems.add(m.getRoot());
        }
        show(savedInstanceState == null ? 0 : savedInstanceState.getInt(STATE_TAB, 0), false);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!b.tvMenu.hasFocus()) {
                    menuItems.get(Math.max(0, current)).requestFocus();
                } else if (current != 0) {
                    show(0, false);
                    menuItems.get(0).requestFocus();
                } else {
                    finish();
                }
            }
        });
    }

    private void show(int index, boolean moveFocus) {
        if (index != current) {
            Fragment f = getSupportFragmentManager().findFragmentByTag("tv-" + index);
            if (f == null) f = create(index);
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.tv_content, f, "tv-" + index)
                    .commit();
            current = index;
        }
        for (int i = 0; i < menuItems.size(); i++) menuItems.get(i).setSelected(i == index);
        if (index != 0) showBackdrop(null, null);
        if (moveFocus) b.tvContent.post(() -> b.tvContent.requestFocus(View.FOCUS_RIGHT));
    }

    private static Fragment create(int index) {
        switch (index) {
            case 1: return new TvSearchFragment();
            case 2: return new TvLibraryFragment();
            case 3: return new SettingsFragment();
            default: return new TvHomeFragment();
        }
    }

    @Override
    public void showBackdrop(@Nullable String url, @Nullable String color) {
        if (url == null) {
            backdropUrl = null;
            Images.clear(b.tvBackdrop);
            return;
        }
        if (url.equals(backdropUrl)) return;
        backdropUrl = url;
        Images.backdrop(b.tvBackdrop, url, color);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_TAB, Math.max(0, current));
    }
}
