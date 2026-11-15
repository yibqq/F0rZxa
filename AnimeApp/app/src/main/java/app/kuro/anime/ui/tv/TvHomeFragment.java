package app.kuro.anime.ui.tv;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.KuroApp;
import app.kuro.anime.R;
import app.kuro.anime.core.Debouncer;
import app.kuro.anime.core.Resource;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.AnimeQuery;
import app.kuro.anime.data.model.HomeFeed;
import app.kuro.anime.data.model.LibraryEntry;
import app.kuro.anime.databinding.FragmentTvHomeBinding;
import app.kuro.anime.ui.common.Labels;
import app.kuro.anime.ui.common.Navigator;
import app.kuro.anime.ui.home.HomeViewModel;
import app.kuro.anime.ui.home.HomeFragment;

/** 10-foot home: a hero that reflects the focused title above rows navigated with the D-pad. */
public class TvHomeFragment extends Fragment {

    private FragmentTvHomeBinding b;
    private HomeViewModel vm;
    private TvRowsAdapter adapter;
    private final Debouncer heroDebounce = new Debouncer(220);
    private boolean focusedOnce;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentTvHomeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(HomeViewModel.class);
        adapter = new TvRowsAdapter(new TvRowsAdapter.Listener() {
            @Override public void onClick(Anime a) { Navigator.openDetails(requireContext(), a); }

            @Override public void onFocused(Anime a) { heroDebounce.submit(() -> showHero(a)); }

            @Override
            public void onGenre(String key, String label) {
                AnimeQuery q = new AnimeQuery();
                q.genre = key;
                q.sort = AnimeQuery.Sort.POPULARITY;
                Navigator.openBrowse(requireContext(), label, q);
            }
        });
        b.tvRows.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.tvRows.setAdapter(adapter);
        vm.feed().observe(getViewLifecycleOwner(), r -> render());
        vm.continueWatching().observe(getViewLifecycleOwner(), l -> render());
    }

    private void render() {
        Resource<HomeFeed> r = vm.feed().getValue();
        if (r == null || b == null) return;
        Context c = requireContext();
        if (r.data == null) {
            if (r.isError()) {
                b.tvState.setVisibility(View.VISIBLE);
                ((LinearLayout.LayoutParams) b.tvState.getLayoutParams()).weight = 0.70f;
                b.tvRows.setVisibility(View.GONE);
                b.tvState.showError(r.error, () -> vm.load(true));
                b.tvState.actionButton().requestFocus();
            } else {
                b.tvHeroTitle.setText(R.string.state_loading);
            }
            return;
        }
        b.tvState.setVisibility(View.GONE);
        b.tvRows.setVisibility(View.VISIBLE);
        List<TvRowsAdapter.Row> rows = new ArrayList<>();
        List<LibraryEntry> watching = vm.continueWatching().getValue();
        if (watching != null && !watching.isEmpty()) {
            List<Anime> items = new ArrayList<>();
            for (LibraryEntry e : watching) items.add(e.toAnime());
            rows.add(new TvRowsAdapter.Row("continue", c.getString(R.string.section_continue), items, null));
        }
        for (HomeFeed.Section s : r.data.sections) {
            rows.add(new TvRowsAdapter.Row(s.type.name(), HomeFragment.sectionTitle(c, s.type), s.items, null));
        }
        rows.add(new TvRowsAdapter.Row("genres", c.getString(R.string.tv_browse_genres), genres(c)));
        adapter.submit(rows);
        if (!r.data.sections.isEmpty() && !r.data.sections.get(0).items.isEmpty() && b.tvHeroTitle.getTag() == null) {
            showHero(r.data.sections.get(0).items.get(0));
        }
        if (!focusedOnce) {
            focusedOnce = true;
            TvFocus.request(b.tvRows);
        }
    }

    static List<String[]> genres(Context c) {
        String[] keys = c.getResources().getStringArray(R.array.genre_keys);
        String[] labels = c.getResources().getStringArray(R.array.genre_labels);
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) out.add(new String[]{keys[i], labels[i]});
        return out;
    }

    private void showHero(Anime a) {
        if (b == null) return;
        Context c = requireContext();
        b.tvHeroTitle.setTag(a.key());
        b.tvHeroTitle.setText(a.displayTitle(KuroApp.get().settings().titleLanguage()));
        List<String> parts = new ArrayList<>();
        String meta = Labels.metaLine(c, a, true);
        if (!meta.isEmpty()) parts.add(meta);
        if (a.averageScore > 0) parts.add(c.getString(R.string.score_value, a.averageScore));
        for (int i = 0; i < Math.min(3, a.genres.size()); i++) parts.add(Labels.genre(c, a.genres.get(i)));
        b.tvHeroMeta.setText(android.text.TextUtils.join(" · ", parts));
        if (getActivity() instanceof TvBackdropHost) {
            ((TvBackdropHost) getActivity()).showBackdrop(a.bannerUrl != null ? a.bannerUrl : a.coverUrl, a.coverColor);
        }
    }

    @Override
    public void onDestroyView() {
        heroDebounce.cancel();
        super.onDestroyView();
        b = null;
    }
}
