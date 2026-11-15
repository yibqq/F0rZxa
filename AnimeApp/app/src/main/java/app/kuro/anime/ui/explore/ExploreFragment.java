package app.kuro.anime.ui.explore;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import app.kuro.anime.R;
import app.kuro.anime.data.model.AnimeQuery;
import app.kuro.anime.data.model.Season;
import app.kuro.anime.databinding.FragmentExploreBinding;
import app.kuro.anime.ui.common.Chips;
import app.kuro.anime.ui.common.InsetsHelper;
import app.kuro.anime.ui.common.Labels;
import app.kuro.anime.ui.common.Layouts;
import app.kuro.anime.ui.common.Navigator;
import app.kuro.anime.ui.common.PagedGridBinder;
import app.kuro.anime.ui.common.ScrollableTab;

/** Browse the catalog by genre, year, season, format and status with sorting. Filters survive navigation. */
public class ExploreFragment extends Fragment implements ScrollableTab {

    static final String[] FORMATS = {"TV", "MOVIE", "OVA", "ONA", "SPECIAL", "TV_SHORT"};
    static final String[] STATUSES = {"RELEASING", "FINISHED", "NOT_YET_RELEASED"};
    static final AnimeQuery.Sort[] SORTS = {AnimeQuery.Sort.POPULARITY, AnimeQuery.Sort.TRENDING,
            AnimeQuery.Sort.SCORE, AnimeQuery.Sort.NEWEST, AnimeQuery.Sort.TITLE};

    private FragmentExploreBinding b;
    private ExploreViewModel vm;
    private PagedGridBinder grid;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentExploreBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(ExploreViewModel.class);
        InsetsHelper.pad(b.exploreRoot, InsetsHelper.TOP);
        if (Layouts.usesRail(requireContext())) InsetsHelper.pad(b.grid, InsetsHelper.BOTTOM);
        grid = new PagedGridBinder(getViewLifecycleOwner(), b.grid, b.state, b.banner, vm.list,
                a -> Navigator.openDetails(requireContext(), a),
                sv -> sv.show(R.drawable.ic_filter, getString(R.string.filter_results_empty), null,
                        getString(R.string.filter_reset), this::reset), false);
        buildFilters();
    }

    private void buildFilters() {
        Context c = requireContext();
        AnimeQuery q = vm.query;
        b.filters.removeAllViews();
        chip(sortLabel(c, q.sort), true, true, this::pickSort);
        chip(q.year > 0 ? String.valueOf(q.year) : getString(R.string.filter_year), q.year > 0, false, this::pickYear);
        chip(q.season != null ? Labels.season(c, q.season) : getString(R.string.filter_season), q.season != null, false, this::pickSeason);
        chip(q.format != null ? Labels.format(c, q.format) : getString(R.string.filter_format), q.format != null, false, this::pickFormat);
        chip(q.status != null ? Labels.status(c, q.status) : getString(R.string.filter_status), q.status != null, false, this::pickStatus);
        if (q.hasFilters()) Chips.add(b.filters, getString(R.string.filter_reset), false, this::reset);

        b.genres.removeAllViews();
        TextView all = Chips.add(b.genres, getString(R.string.filter_all_genres), false, () -> setGenre(null));
        all.setSelected(q.genre == null);
        String[] keys = getResources().getStringArray(R.array.genre_keys);
        String[] labels = getResources().getStringArray(R.array.genre_labels);
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            TextView chip = Chips.add(b.genres, labels[i], false, () -> setGenre(key));
            chip.setSelected(key.equals(q.genre));
        }
    }

    private void chip(String text, boolean active, boolean sortIcon, Runnable click) {
        TextView t = Chips.add(b.filters, text, true, click);
        t.setSelected(active && !sortIcon);
        if (sortIcon) {
            t.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_sort, 0, R.drawable.ic_chevron_down, 0);
            t.setPaddingRelative(Layouts.dp(requireContext(), 10), 0, Layouts.dp(requireContext(), 8), 0);
        }
    }

    static String sortLabel(Context c, AnimeQuery.Sort s) {
        switch (s) {
            case TRENDING: return c.getString(R.string.sort_trending);
            case SCORE: return c.getString(R.string.sort_score);
            case NEWEST: return c.getString(R.string.sort_newest);
            case TITLE: return c.getString(R.string.sort_title);
            case RELEVANCE: return c.getString(R.string.sort_relevance);
            case POPULARITY:
            default: return c.getString(R.string.sort_popularity);
        }
    }

    private void pickSort() {
        String[] labels = new String[SORTS.length];
        int checked = 0;
        for (int i = 0; i < SORTS.length; i++) {
            labels[i] = sortLabel(requireContext(), SORTS[i]);
            if (SORTS[i] == vm.query.sort) checked = i;
        }
        choose(R.string.filter_sort, labels, checked, false, i -> update(q -> q.sort = SORTS[i]));
    }

    private void pickYear() {
        int max = Calendar.getInstance().get(Calendar.YEAR) + 1;
        List<String> years = new ArrayList<>();
        int checked = 0;
        for (int y = max; y >= 1970; y--) {
            years.add(String.valueOf(y));
            if (y == vm.query.year) checked = years.size();
        }
        choose(R.string.filter_year, years.toArray(new String[0]), checked, true,
                i -> update(q -> q.year = i < 0 ? 0 : max - i));
    }

    private void pickSeason() {
        String[] labels = new String[Season.ALL.length];
        int checked = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Labels.season(requireContext(), Season.ALL[i]);
            if (Season.ALL[i].equals(vm.query.season)) checked = i + 1;
        }
        choose(R.string.filter_season, labels, checked, true, i -> update(q -> {
            q.season = i < 0 ? null : Season.ALL[i];
            // AniList needs a year with a season; default to the current one.
            if (q.season != null && q.year == 0) q.year = Season.now().year;
        }));
    }

    private void pickFormat() {
        String[] labels = new String[FORMATS.length];
        int checked = 0;
        for (int i = 0; i < FORMATS.length; i++) {
            labels[i] = Labels.format(requireContext(), FORMATS[i]);
            if (FORMATS[i].equals(vm.query.format)) checked = i + 1;
        }
        choose(R.string.filter_format, labels, checked, true, i -> update(q -> q.format = i < 0 ? null : FORMATS[i]));
    }

    private void pickStatus() {
        String[] labels = new String[STATUSES.length];
        int checked = 0;
        for (int i = 0; i < STATUSES.length; i++) {
            labels[i] = Labels.status(requireContext(), STATUSES[i]);
            if (STATUSES[i].equals(vm.query.status)) checked = i + 1;
        }
        choose(R.string.filter_status, labels, checked, true, i -> update(q -> q.status = i < 0 ? null : STATUSES[i]));
    }

    private interface IntConsumer { void accept(int i); }

    private interface Mutator { void apply(AnimeQuery q); }

    /** Single-choice dialog; with {@code withAny}, index 0 is "Any" and the callback receives -1 for it. */
    private void choose(int title, String[] labels, int checked, boolean withAny, IntConsumer cb) {
        String[] items = labels;
        if (withAny) {
            items = new String[labels.length + 1];
            items[0] = getString(R.string.filter_any);
            System.arraycopy(labels, 0, items, 1, labels.length);
        }
        new AlertDialog.Builder(requireContext(), R.style.Kuro_Dialog)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    cb.accept(withAny ? which - 1 : which);
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void update(Mutator m) {
        AnimeQuery q = vm.query.copy();
        m.apply(q);
        vm.apply(q);
        buildFilters();
        b.grid.scrollToPosition(0);
    }

    private void setGenre(@Nullable String genre) { update(q -> q.genre = genre); }

    private void reset() {
        update(q -> {
            q.genre = null;
            q.year = 0;
            q.season = null;
            q.format = null;
            q.status = null;
        });
    }

    @Override
    public void scrollToTop() { if (grid != null) grid.scrollToTop(); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
        grid = null;
    }
}
