package app.kuro.anime.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import java.io.File;

import app.kuro.anime.BuildConfig;
import app.kuro.anime.KuroApp;
import app.kuro.anime.R;
import app.kuro.anime.core.AppExecutors;
import app.kuro.anime.core.DeviceProfile;
import app.kuro.anime.core.Text;
import app.kuro.anime.data.model.TitleLanguage;
import app.kuro.anime.data.repo.SettingsStore;
import app.kuro.anime.databinding.FragmentSettingsBinding;
import app.kuro.anime.databinding.ItemSettingBinding;
import app.kuro.anime.playback.PlaybackProviders;
import app.kuro.anime.ui.common.InsetsHelper;
import app.kuro.anime.ui.common.Layouts;

/** Language, display, local data management and app information. */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding b;
    private ItemSettingBinding cacheRow;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentSettingsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        boolean tv = DeviceProfile.isTelevision(requireContext());
        InsetsHelper.pad(b.settingsScroll, InsetsHelper.TOP | (tv || Layouts.usesRail(requireContext()) ? InsetsHelper.BOTTOM : 0));
        Layouts.constrainWidth(b.settingsContent, 760);
        build();
    }

    private void build() {
        Context c = requireContext();
        SettingsStore s = KuroApp.get().settings();
        LinearLayout root = b.settingsContent;
        while (root.getChildCount() > 1) root.removeViewAt(1);

        LinearLayout general = section(root, R.string.settings_section_general);
        row(general, R.drawable.ic_globe, getString(R.string.settings_language), languageLabel(), null, v -> pickLanguage());
        row(general, R.drawable.ic_list, getString(R.string.settings_title_language), titleLabel(s.titleLanguage()), null, v -> pickTitleLanguage());

        LinearLayout display = section(root, R.string.settings_section_display);
        row(display, R.drawable.ic_image, getString(R.string.settings_image_quality),
                getString(s.dataSaver() ? R.string.image_quality_saver : R.string.image_quality_high), null, v -> {
                    s.setDataSaver(!s.dataSaver());
                    build();
                });
        row(display, R.drawable.ic_grid, getString(R.string.settings_grid_density),
                getString(s.compactGrid() ? R.string.density_compact : R.string.density_comfortable), null, v -> {
                    s.setCompactGrid(!s.compactGrid());
                    build();
                });
        row(display, R.drawable.ic_eye_off, getString(R.string.settings_hide_spoilers),
                getString(R.string.settings_hide_spoilers_desc), s.hideSpoilers(), v -> {
                    s.setHideSpoilers(!s.hideSpoilers());
                    build();
                });

        LinearLayout data = section(root, R.string.settings_section_data);
        cacheRow = row(data, R.drawable.ic_database, getString(R.string.settings_clear_cache), null, null, v -> clearCache());
        updateCacheSize();
        row(data, R.drawable.ic_history, getString(R.string.settings_clear_history), null, null, v ->
                AppExecutors.disk().execute(() -> {
                    KuroApp.get().searchHistory().clear();
                    AppExecutors.main(() -> toast(R.string.cleared));
                }));
        row(data, R.drawable.ic_trash, getString(R.string.settings_reset_library),
                getString(R.string.settings_reset_library_desc), null, v -> confirmReset());

        LinearLayout about = section(root, R.string.settings_section_about);
        row(about, R.drawable.ic_layers, getString(R.string.settings_data_sources),
                getString(R.string.settings_data_sources_desc), null, v -> new AlertDialog.Builder(c, R.style.Kuro_Dialog)
                        .setTitle(R.string.settings_data_sources)
                        .setMessage(R.string.settings_data_sources_body)
                        .setPositiveButton(R.string.ok, null).show());
        boolean playback = PlaybackProviders.get().isAvailable();
        row(about, R.drawable.ic_play, getString(R.string.settings_playback),
                playback ? PlaybackProviders.get().displayName() : getString(R.string.settings_playback_none), null, null);
        row(about, R.drawable.ic_info, getString(R.string.settings_version), BuildConfig.VERSION_NAME, null, null);
    }

    private LinearLayout section(LinearLayout root, int title) {
        Context c = requireContext();
        TextView h = new TextView(c, null, 0, R.style.Kuro_Text_Caption);
        h.setText(title);
        h.setAllCaps(true);
        h.setLetterSpacing(0.08f);
        h.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        androidx.core.view.ViewCompat.setAccessibilityHeading(h, true);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = Layouts.dp(c, 28);
        hp.bottomMargin = Layouts.dp(c, 8);
        hp.setMarginStart(Layouts.dp(c, 12));
        root.addView(h, hp);
        LinearLayout group = new LinearLayout(c);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackgroundResource(R.drawable.bg_surface_round);
        group.setPadding(0, Layouts.dp(c, 4), 0, Layouts.dp(c, 4));
        root.addView(group, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return group;
    }

    private ItemSettingBinding row(LinearLayout group, @DrawableRes int icon, String title, @Nullable String summary,
                                   @Nullable Boolean toggle, @Nullable View.OnClickListener click) {
        ItemSettingBinding r = ItemSettingBinding.inflate(getLayoutInflater(), group, false);
        r.settingIcon.setImageResource(icon);
        r.settingTitle.setText(title);
        if (summary != null) {
            r.settingSummary.setText(summary);
            r.settingSummary.setVisibility(View.VISIBLE);
        }
        if (toggle != null) {
            r.settingSwitch.setVisibility(View.VISIBLE);
            r.settingSwitch.setChecked(toggle);
            r.getRoot().setContentDescription(title + ", " + (summary == null ? "" : summary));
            r.getRoot().setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.Switch");
                    info.setCheckable(true);
                    info.setChecked(toggle);
                }
            });
        }
        if (click != null) r.getRoot().setOnClickListener(click);
        else {
            r.getRoot().setClickable(false);
            r.getRoot().setFocusable(false);
        }
        group.addView(r.getRoot());
        return r;
    }

    private String languageLabel() {
        LocaleListCompat l = AppCompatDelegate.getApplicationLocales();
        if (l.isEmpty()) return getString(R.string.settings_language_system);
        String tag = l.get(0) == null ? "" : l.get(0).getLanguage();
        return "ar".equals(tag) ? "العربية" : "English";
    }

    private void pickLanguage() {
        String[] labels = {getString(R.string.settings_language_system), "English", "العربية"};
        String[] tags = {"", "en", "ar"};
        LocaleListCompat cur = AppCompatDelegate.getApplicationLocales();
        String curTag = cur.isEmpty() || cur.get(0) == null ? "" : cur.get(0).getLanguage();
        int checked = 0;
        for (int i = 0; i < tags.length; i++) if (tags[i].equals(curTag)) checked = i;
        new AlertDialog.Builder(requireContext(), R.style.Kuro_Dialog)
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    // Recreates activities with the new locale and layout direction (RTL for Arabic).
                    AppCompatDelegate.setApplicationLocales(which == 0 ? LocaleListCompat.getEmptyLocaleList()
                            : LocaleListCompat.forLanguageTags(tags[which]));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String titleLabel(TitleLanguage l) {
        switch (l) {
            case ENGLISH: return getString(R.string.title_lang_english);
            case NATIVE: return getString(R.string.title_lang_native);
            default: return getString(R.string.title_lang_romaji);
        }
    }

    private void pickTitleLanguage() {
        TitleLanguage[] all = TitleLanguage.values();
        String[] labels = new String[all.length];
        for (int i = 0; i < all.length; i++) labels[i] = titleLabel(all[i]);
        new AlertDialog.Builder(requireContext(), R.style.Kuro_Dialog)
                .setTitle(R.string.settings_title_language)
                .setSingleChoiceItems(labels, KuroApp.get().settings().titleLanguage().ordinal(), (d, which) -> {
                    KuroApp.get().settings().setTitleLanguage(all[which]);
                    d.dismiss();
                    build();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateCacheSize() {
        Context app = requireContext().getApplicationContext();
        AppExecutors.disk().execute(() -> {
            long bytes = KuroApp.get().responseCache().sizeBytes() + dirSize(new File(app.getCacheDir(), "image_manager_disk_cache"));
            AppExecutors.main(() -> {
                if (b == null || cacheRow == null) return;
                cacheRow.settingSummary.setText(getString(R.string.settings_clear_cache_desc, Text.formatBytes(bytes)));
                cacheRow.settingSummary.setVisibility(View.VISIBLE);
            });
        });
    }

    private void clearCache() {
        Context app = requireContext().getApplicationContext();
        Glide.get(app).clearMemory();
        AppExecutors.disk().execute(() -> {
            KuroApp.get().responseCache().clear();
            Glide.get(app).clearDiskCache();
            AppExecutors.main(() -> {
                if (b == null) return;
                toast(R.string.cleared);
                updateCacheSize();
            });
        });
    }

    private void confirmReset() {
        new AlertDialog.Builder(requireContext(), R.style.Kuro_Dialog)
                .setTitle(R.string.settings_reset_library)
                .setMessage(R.string.settings_reset_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.remove, (d, w) -> AppExecutors.disk().execute(() -> {
                    KuroApp.get().library().clearAll();
                    AppExecutors.main(() -> toast(R.string.cleared));
                }))
                .show();
    }

    private void toast(int res) {
        if (getContext() != null) Toast.makeText(getContext(), res, Toast.LENGTH_SHORT).show();
    }

    private static long dirSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long total = 0;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) total += dirSize(c);
        return total;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
        cacheRow = null;
    }
}
