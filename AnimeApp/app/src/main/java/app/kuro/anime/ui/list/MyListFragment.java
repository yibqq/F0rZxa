package app.kuro.anime.ui.list;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.KuroApp;
import app.kuro.anime.R;
import app.kuro.anime.core.AppExecutors;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.LibraryEntry;
import app.kuro.anime.data.model.WatchStatus;
import app.kuro.anime.databinding.FragmentMyListBinding;
import app.kuro.anime.ui.common.AnimeCardAdapter;
import app.kuro.anime.ui.common.GridAutoFit;
import app.kuro.anime.ui.common.InsetsHelper;
import app.kuro.anime.ui.common.Labels;
import app.kuro.anime.ui.common.Layouts;
import app.kuro.anime.ui.common.MasterDetail;
import app.kuro.anime.ui.common.NavigationBar;
import app.kuro.anime.ui.common.Navigator;
import app.kuro.anime.ui.common.ScrollableTab;
import app.kuro.anime.ui.common.SpacingDecoration;
import app.kuro.anime.ui.common.TabHost;
import app.kuro.anime.ui.details.StatusDialog;

/** Local library: Watching, Plan to watch, Completed, On hold and Favorites, with progress. */
public class MyListFragment extends Fragment implements ScrollableTab {

    private FragmentMyListBinding b;
    private MyListViewModel vm;
    private AnimeCardAdapter adapter;
    private MasterDetail masterDetail;
    private final List<LibraryEntry> current = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentMyListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        vm = new ViewModelProvider(this).get(MyListViewModel.class);
        InsetsHelper.pad(b.listRoot, InsetsHelper.TOP);
        if (Layouts.usesRail(c)) InsetsHelper.pad(b.main.listGrid, InsetsHelper.BOTTOM);
        masterDetail = new MasterDetail(this, b.pane == null ? null : b.pane.detailContainer,
                b.pane == null ? null : b.pane.detailPlaceholder);

        int spacing = Layouts.dp(c, 12);
        GridAutoFit.attach(b.main.listGrid, Layouts.gridMinWidth(c), spacing);
        b.main.listGrid.addItemDecoration(new SpacingDecoration(spacing));
        adapter = new AnimeCardAdapter(new AnimeCardAdapter.Listener() {
            @Override
            public void onClick(Anime a) {
                vm.selectedKey = a.key();
                if (!masterDetail.show(a)) Navigator.openDetails(requireContext(), a);
            }

            @Override
            public boolean onLongClick(Anime a) {
                LibraryEntry e = find(a);
                StatusDialog.show(requireContext(), e == null ? null : e.status,
                        s -> AppExecutors.disk().execute(() -> KuroApp.get().library().setStatus(a, s)));
                return true;
            }
        }, new AnimeCardAdapter.Extras() {
            @Override
            public String subtitle(Context ctx, Anime a) {
                LibraryEntry e = find(a);
                if (e == null) return null;
                if (e.watchedCount == 0) return Labels.metaLine(ctx, a, true);
                return e.totalEpisodes > 0 ? ctx.getResources().getQuantityString(R.plurals.progress_format, e.totalEpisodes, e.watchedCount, e.totalEpisodes)
                        : ctx.getResources().getQuantityString(R.plurals.progress_format_unknown, e.watchedCount, e.watchedCount);
            }

            @Override
            public float progress(Anime a) {
                LibraryEntry e = find(a);
                if (e == null || e.totalEpisodes <= 0 || e.watchedCount == 0) return -1f;
                return e.watchedCount / (float) e.totalEpisodes;
            }
        }, 0, false);
        b.main.listGrid.setAdapter(adapter);

        List<String> tabs = new ArrayList<>();
        for (WatchStatus s : MyListViewModel.TABS) tabs.add(s == null ? getString(R.string.tab_favorites) : Labels.watchStatus(c, s));
        b.main.listTabs.setItems(tabs);
        b.main.listTabs.setSelectedIndex(vm.tab, false);
        b.main.listTabs.setOnSegmentSelected(i -> {
            vm.setTab(i);
            b.main.listGrid.scrollToPosition(0);
        });

        vm.entries().observe(getViewLifecycleOwner(), this::render);
        vm.counts().observe(getViewLifecycleOwner(), counts -> {
            int n = counts[vm.tab];
            b.main.listCount.setText(getResources().getQuantityString(R.plurals.my_list_count, n, n));
        });
        KuroApp.get().settings().changes().observe(getViewLifecycleOwner(), v -> adapter.notifyDataSetChanged());
        vm.reload();
        if (vm.selectedKey == null) masterDetail.showPlaceholder();
    }

    private void render(List<LibraryEntry> entries) {
        current.clear();
        current.addAll(entries);
        List<Anime> items = new ArrayList<>();
        for (LibraryEntry e : entries) items.add(e.toAnime());
        adapter.submitList(items);
        adapter.notifyItemRangeChanged(0, items.size());
        if (items.isEmpty()) {
            b.main.listState.show(R.drawable.ic_bookmark, getString(R.string.my_list_empty_title),
                    getString(R.string.my_list_empty_body), getString(R.string.go_explore), () -> {
                        if (requireActivity() instanceof TabHost) ((TabHost) requireActivity()).selectTab(NavigationBar.EXPLORE);
                    });
        } else {
            b.main.listState.hide();
        }
        Integer count = items.size();
        b.main.listCount.setText(getResources().getQuantityString(R.plurals.my_list_count, count, count));
    }

    @Nullable
    private LibraryEntry find(Anime a) {
        for (LibraryEntry e : current) if (e.key.equals(a.key())) return e;
        return null;
    }

    @Override
    public void scrollToTop() { if (b != null) b.main.listGrid.smoothScrollToPosition(0); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
