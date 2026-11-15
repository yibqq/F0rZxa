package app.kuro.anime.ui.tv;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.KuroApp;
import app.kuro.anime.R;
import app.kuro.anime.core.AppExecutors;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.LibraryEntry;
import app.kuro.anime.data.model.WatchStatus;
import app.kuro.anime.databinding.FragmentTvHomeBinding;
import app.kuro.anime.ui.common.Labels;
import app.kuro.anime.ui.common.Navigator;
import app.kuro.anime.ui.home.HomeFragment;

/** My List on TV: one row per status plus Favorites. */
public class TvLibraryFragment extends Fragment {
    private FragmentTvHomeBinding b;
    private TvRowsAdapter adapter;
    private final Observer<Long> observer = v -> reload();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentTvHomeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.tvHeroTitle.setText(R.string.my_list_title);
        adapter = new TvRowsAdapter(new TvRowsAdapter.Listener() {
            @Override public void onClick(Anime a) { Navigator.openDetails(requireContext(), a); }

            @Override public void onGenre(String key, String label) {}
        });
        b.tvRows.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.tvRows.setAdapter(adapter);
        KuroApp.get().library().changes().observe(getViewLifecycleOwner(), observer);
    }

    private void reload() {
        final android.content.Context c = requireContext().getApplicationContext();
        final String favTitle = getString(R.string.tab_favorites);
        AppExecutors.disk().execute(() -> {
            List<TvRowsAdapter.Row> rows = new ArrayList<>();
            int total = 0;
            for (WatchStatus s : WatchStatus.values()) {
                List<LibraryEntry> entries = KuroApp.get().library().byStatus(s);
                total += entries.size();
                if (!entries.isEmpty()) rows.add(row(s.name(), Labels.watchStatus(c, s), entries));
            }
            List<LibraryEntry> favs = KuroApp.get().library().favorites();
            if (!favs.isEmpty()) rows.add(row("fav", favTitle, favs));
            final int titled = total;
            final int count = total + favs.size();
            AppExecutors.main(() -> {
                if (b == null) return;
                adapter.submit(rows);
                if (count == 0) {
                    b.tvHeroMeta.setText(R.string.my_list_empty_body);
                } else {
                    b.tvHeroMeta.setText(getResources().getQuantityString(R.plurals.my_list_count, titled, titled));
                }
            });
        });
    }

    private TvRowsAdapter.Row row(String key, String title, List<LibraryEntry> entries) {
        List<Anime> items = new ArrayList<>();
        for (LibraryEntry e : entries) items.add(e.toAnime());
        return new TvRowsAdapter.Row(key, title, items, HomeFragment.progressExtras(entries));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
