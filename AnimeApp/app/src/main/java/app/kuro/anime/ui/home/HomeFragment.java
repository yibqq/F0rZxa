package app.kuro.anime.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.kuro.anime.KuroApp;
import app.kuro.anime.R;
import app.kuro.anime.core.AppExecutors;
import app.kuro.anime.core.Resource;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.HomeFeed;
import app.kuro.anime.data.model.LibraryEntry;
import app.kuro.anime.data.model.Season;
import app.kuro.anime.data.model.SectionType;
import app.kuro.anime.data.model.WatchStatus;
import app.kuro.anime.databinding.FragmentHomeBinding;
import app.kuro.anime.ui.common.AnimeCardAdapter;
import app.kuro.anime.ui.common.InsetsHelper;
import app.kuro.anime.ui.common.Layouts;
import app.kuro.anime.ui.common.NavigationBar;
import app.kuro.anime.ui.common.Navigator;
import app.kuro.anime.ui.common.ScrollableTab;
import app.kuro.anime.ui.common.TabHost;

public class HomeFragment extends Fragment implements ScrollableTab {

    private FragmentHomeBinding b;
    private HomeViewModel vm;
    private HomeAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentHomeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(HomeViewModel.class);
        // The hero runs under the status bar; only the bottom needs clearing in rail mode.
        if (Layouts.usesRail(requireContext())) InsetsHelper.pad(b.list, InsetsHelper.BOTTOM);
        InsetsHelper.marginTop(b.banner);

        adapter = new HomeAdapter(new HomeAdapter.Listener() {
            @Override public void onOpen(Anime a) { Navigator.openDetails(requireContext(), a); }

            @Override
            public void onToggleList(Anime a, boolean inList) {
                AppExecutors.disk().execute(() -> KuroApp.get().library().setStatus(a, inList ? null : WatchStatus.PLANNING));
            }

            @Override
            public void onSeeAll(HomeAdapter.Row row) {
                if ("continue".equals(row.key)) {
                    if (requireActivity() instanceof TabHost) ((TabHost) requireActivity()).selectTab(NavigationBar.MY_LIST);
                    return;
                }
                SectionType type = SectionType.valueOf(row.key);
                Navigator.openBrowse(requireContext(), row.title, type.toQuery(Season.now()));
            }
        }, vm.rowStates);
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.list.setItemViewCacheSize(4);

        b.refresh.setColorSchemeColors(0xFF000000);
        b.refresh.setProgressBackgroundColorSchemeColor(0xFFFFFFFF);
        b.refresh.setOnRefreshListener(() -> vm.load(true));

        vm.feed().observe(getViewLifecycleOwner(), r -> render());
        vm.continueWatching().observe(getViewLifecycleOwner(), l -> render());
        vm.libraryKeys().observe(getViewLifecycleOwner(), k -> render());
        KuroApp.get().settings().changes().observe(getViewLifecycleOwner(), v -> adapter.notifyDataSetChanged());
    }

    private void render() {
        Resource<HomeFeed> r = vm.feed().getValue();
        if (r == null || b == null) return;
        Context c = requireContext();
        b.refresh.setRefreshing(r.isLoading() && r.data != null);
        Set<String> keys = vm.libraryKeys().getValue() == null ? new HashSet<>() : vm.libraryKeys().getValue();

        if (r.data == null) {
            if (r.isLoading()) {
                b.state.hide();
                List<HomeAdapter.Row> skel = new ArrayList<>();
                skel.add(new HomeAdapter.Row(HomeAdapter.SKELETON_FEATURED, "sk-f", "", Collections.emptyList(), null));
                skel.add(new HomeAdapter.Row(HomeAdapter.SKELETON_SECTION, "sk-1", c.getString(R.string.section_trending), Collections.emptyList(), null));
                skel.add(new HomeAdapter.Row(HomeAdapter.SKELETON_SECTION, "sk-2", c.getString(R.string.section_seasonal), Collections.emptyList(), null));
                adapter.submit(skel, keys);
            } else if (r.isError()) {
                adapter.submit(Collections.emptyList(), keys);
                b.state.showError(r.error, () -> vm.load(true));
            }
            b.banner.setVisibility(View.GONE);
            return;
        }

        b.state.hide();
        HomeFeed feed = r.data;
        List<HomeAdapter.Row> rows = new ArrayList<>();
        HomeFeed.Section trending = feed.get(SectionType.TRENDING);
        if (trending == null && !feed.sections.isEmpty()) trending = feed.sections.get(0);
        if (trending != null) {
            List<Anime> hero = new ArrayList<>();
            for (Anime a : trending.items) {
                if (a.coverUrl != null && (a.bannerUrl != null || hero.size() < 2)) hero.add(a);
                if (hero.size() == 5) break;
            }
            if (!hero.isEmpty()) rows.add(new HomeAdapter.Row(HomeAdapter.FEATURED, "featured", "", hero, null));
        }

        List<LibraryEntry> watching = vm.continueWatching().getValue();
        if (watching != null && !watching.isEmpty()) {
            List<Anime> items = new ArrayList<>();
            for (LibraryEntry e : watching) items.add(e.toAnime());
            rows.add(new HomeAdapter.Row(HomeAdapter.SECTION, "continue", c.getString(R.string.section_continue), items,
                    progressExtras(watching)));
        }

        for (HomeFeed.Section s : feed.sections) {
            rows.add(new HomeAdapter.Row(HomeAdapter.SECTION, s.type.name(), sectionTitle(c, s.type), s.items, null));
        }
        adapter.submit(rows, keys);

        if (r.stale || r.fallback || r.isError()) {
            b.banner.setText(r.fallback ? R.string.state_fallback_banner : R.string.state_stale_banner);
            b.banner.setVisibility(View.VISIBLE);
        } else {
            b.banner.setVisibility(View.GONE);
        }
    }

    public static AnimeCardAdapter.Extras progressExtras(List<LibraryEntry> entries) {
        return new AnimeCardAdapter.Extras() {
            @Override
            public String subtitle(Context c, Anime a) {
                LibraryEntry e = find(a);
                if (e == null) return null;
                return e.totalEpisodes > 0 ? c.getResources().getQuantityString(R.plurals.progress_format, e.totalEpisodes, e.watchedCount, e.totalEpisodes)
                        : c.getResources().getQuantityString(R.plurals.progress_format_unknown, e.watchedCount, e.watchedCount);
            }

            @Override
            public float progress(Anime a) {
                LibraryEntry e = find(a);
                if (e == null || e.totalEpisodes <= 0) return -1f;
                return e.watchedCount / (float) e.totalEpisodes;
            }

            private LibraryEntry find(Anime a) {
                for (LibraryEntry e : entries) if (e.key.equals(a.key())) return e;
                return null;
            }
        };
    }

    public static String sectionTitle(Context c, SectionType t) {
        switch (t) {
            case TRENDING: return c.getString(R.string.section_trending);
            case SEASONAL: return c.getString(R.string.section_seasonal);
            case AIRING: return c.getString(R.string.section_airing);
            case TOP_RATED: return c.getString(R.string.section_top_rated);
            case UPCOMING: return c.getString(R.string.section_upcoming);
            case POPULAR: default: return c.getString(R.string.section_popular);
        }
    }

    @Override
    public void scrollToTop() { if (b != null) b.list.smoothScrollToPosition(0); }

    @Override
    public void onDestroyView() {
        if (adapter != null && b != null) adapter.saveVisibleStates(b.list);
        super.onDestroyView();
        b = null;
    }
}
