package app.kuro.anime.ui.details;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.kuro.anime.KuroApp;
import app.kuro.anime.R;
import app.kuro.anime.core.Resource;
import app.kuro.anime.core.WindowSizeClass;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.CharacterRef;
import app.kuro.anime.data.model.Episode;
import app.kuro.anime.data.model.EpisodeChunk;
import app.kuro.anime.data.model.LibraryEntry;
import app.kuro.anime.data.model.Relation;
import app.kuro.anime.data.model.StaffRef;
import app.kuro.anime.data.model.WatchStatus;
import app.kuro.anime.databinding.FragmentDetailsBinding;
import app.kuro.anime.ui.common.AnimeCardAdapter;
import app.kuro.anime.ui.common.InsetsHelper;
import app.kuro.anime.ui.common.Labels;
import app.kuro.anime.ui.common.Layouts;
import app.kuro.anime.ui.common.Navigator;

/**
 * Title page. Used full-screen (DetailsActivity) and embedded as the detail pane of
 * master–detail layouts on expanded windows.
 */
public class DetailsFragment extends Fragment {

    public static final String ARG_EMBEDDED = "embedded";

    private FragmentDetailsBinding b;
    private DetailsViewModel vm;
    private HeaderAdapter header;
    private EpisodesHeaderAdapter episodesHeader;
    private EpisodeSkeletonAdapter episodeSkeleton;
    private EpisodeAdapter episodes;
    private RowsAdapter rows;
    private boolean embedded;
    private Anime rowsBuiltFor;

    public static DetailsFragment newInstance(Anime a, boolean embedded) {
        DetailsFragment f = new DetailsFragment();
        Bundle args = new Bundle();
        args.putInt(Navigator.EXTRA_ANILIST_ID, a.anilistId);
        args.putInt(Navigator.EXTRA_MAL_ID, a.malId);
        args.putString(Navigator.EXTRA_TITLE, a.displayTitle(KuroApp.get().settings().titleLanguage()));
        args.putString(Navigator.EXTRA_COVER, a.coverUrl);
        args.putString(Navigator.EXTRA_COLOR, a.coverColor);
        args.putBoolean(ARG_EMBEDDED, embedded);
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentDetailsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        embedded = getArguments() != null && getArguments().getBoolean(ARG_EMBEDDED);
        vm = new ViewModelProvider(this).get(DetailsViewModel.class);
        boolean wide = !embedded && WindowSizeClass.of(c) == WindowSizeClass.EXPANDED;

        b.detailsBack.setVisibility(embedded ? View.GONE : View.VISIBLE);
        b.detailsBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        // Embedded panes already sit below the status bar (the host pads it).
        if (!embedded) InsetsHelper.pad(b.detailsTopbar, InsetsHelper.TOP | InsetsHelper.START | InsetsHelper.END);
        if (!embedded || Layouts.usesRail(c)) InsetsHelper.pad(b.detailsList, InsetsHelper.BOTTOM);
        if (!embedded) InsetsHelper.pad(b.detailsState, InsetsHelper.TOP | InsetsHelper.BOTTOM);

        header = new HeaderAdapter(wide, new HeaderAdapter.Listener() {
            @Override public void onWatch() { watch(-1); }

            @Override public void onStatus() { pickStatus(); }

            @Override public void onFavorite() { vm.toggleFavorite(); }

            @Override
            public void onToggleSynopsis() {
                vm.synopsisExpanded = !vm.synopsisExpanded;
                renderHeader();
            }
        });
        episodesHeader = new EpisodesHeaderAdapter(new EpisodesHeaderAdapter.Listener() {
            @Override public void onRange(int index) { vm.loadEpisodes(index, false); }

            @Override public void onRetry() { vm.loadEpisodes(vm.chunkIndex(), true); }
        });
        episodeSkeleton = new EpisodeSkeletonAdapter();
        episodes = new EpisodeAdapter(new EpisodeAdapter.Listener() {
            @Override public void onEpisodeClick(Episode e) { episodeActions(e); }

            @Override public void onToggleWatched(Episode e) { vm.setWatched(e.number, e.number, !e.watched); }
        });
        rows = new RowsAdapter();

        ConcatAdapter concat = new ConcatAdapter(new ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build(),
                header, episodesHeader, episodeSkeleton, episodes, rows);
        int span = wide ? 2 : 1;
        GridLayoutManager glm = new GridLayoutManager(c, span);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int type = concat.getItemViewType(position);
                return type == EpisodeAdapter.VIEW_TYPE || type == EpisodeSkeletonAdapter.VIEW_TYPE ? 1 : span;
            }
        });
        b.detailsList.setLayoutManager(glm);
        b.detailsList.setAdapter(concat);
        b.detailsList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) { updateTopBar(); }
        });

        vm.anime().observe(getViewLifecycleOwner(), r -> render());
        vm.entry().observe(getViewLifecycleOwner(), e -> renderHeader());
        vm.watched().observe(getViewLifecycleOwner(), w -> {
            renderHeader();
            renderEpisodes();
        });
        vm.episodes().observe(getViewLifecycleOwner(), r -> renderEpisodes());
        vm.completedEvent().observe(getViewLifecycleOwner(), ep -> {
            if (ep == null) return;
            vm.consumeCompletedEvent();
            vm.setStatus(WatchStatus.COMPLETED);
            Toast.makeText(c, R.string.series_completed_toast, Toast.LENGTH_SHORT).show();
        });
        KuroApp.get().settings().changes().observe(getViewLifecycleOwner(), v -> {
            episodes.setHideSpoilers(KuroApp.get().settings().hideSpoilers());
            renderHeader();
        });
        episodes.setHideSpoilers(KuroApp.get().settings().hideSpoilers());
    }

    private void render() {
        Resource<Anime> r = vm.anime().getValue();
        if (r == null) return;
        if (r.data == null && r.isError()) {
            header.submit(model(true));
            b.detailsState.showError(r.error, () -> vm.load(true));
            return;
        }
        b.detailsState.hide();
        renderHeader();
        Anime a = r.data;
        if (a != null) {
            b.detailsBarTitle.setText(a.displayTitle(KuroApp.get().settings().titleLanguage()));
            if (a != rowsBuiltFor) {
                rowsBuiltFor = a;
                buildRows(a);
            }
        } else {
            b.detailsBarTitle.setText(vm.placeholderTitle);
        }
    }

    private HeaderAdapter.Model model(boolean loading) {
        HeaderAdapter.Model m = new HeaderAdapter.Model();
        m.anime = vm.current();
        m.placeholderTitle = vm.placeholderTitle;
        m.placeholderCover = vm.placeholderCover;
        m.placeholderColor = vm.placeholderColor;
        m.entry = vm.entry().getValue();
        m.watched = vm.watched().getValue();
        m.loading = loading;
        m.synopsisExpanded = vm.synopsisExpanded;
        if (m.anime != null) m.nextEpisode = vm.nextEpisode(m.anime);
        return m;
    }

    private void renderHeader() {
        Resource<Anime> r = vm.anime().getValue();
        header.submit(model(r == null || r.isLoading()));
    }

    private void renderEpisodes() {
        Anime a = vm.current();
        Resource<EpisodeChunk> r = vm.episodes().getValue();
        if (a == null) {
            episodesHeader.submit(null);
            return;
        }
        EpisodesHeaderAdapter.Model m = new EpisodesHeaderAdapter.Model();
        m.upcoming = a.isUpcoming();
        m.chunkIndex = vm.chunkIndex();
        m.loading = r == null || r.isLoading();
        if (r != null && r.isError()) m.error = r.error;
        EpisodeChunk chunk = r == null ? null : r.data;
        m.total = chunk != null ? chunk.total : a.airedEpisodeCount();
        if (chunk != null) m.sources = chunk.sources;
        episodesHeader.submit(m);
        boolean showSkeleton = m.loading && !m.upcoming;
        episodeSkeleton.setCount(showSkeleton ? 4 : 0);
        if (chunk != null && !showSkeleton && m.error == null) {
            Set<Integer> w = vm.watched().getValue();
            episodes.submitList(EpisodeAdapter.withWatched(chunk.episodes, w == null ? new HashSet<>() : w));
        } else if (showSkeleton || m.error != null) {
            episodes.submitList(null);
        }
    }

    private void buildRows(Anime a) {
        Context c = requireContext();
        int cardWidth = Layouts.px(c, R.dimen.row_card_width);
        List<RowsAdapter.Row> list = new ArrayList<>();
        if (!a.relations.isEmpty()) {
            List<Anime> rel = new ArrayList<>();
            for (Relation r : a.relations) rel.add(r.anime);
            AnimeCardAdapter ad = new AnimeCardAdapter(this::open, new AnimeCardAdapter.Extras() {
                @Override
                public String subtitle(Context ctx, Anime x) {
                    for (Relation r : a.relations) if (r.anime == x) return Labels.relation(ctx, r.type);
                    return null;
                }
            }, cardWidth, false);
            ad.submitList(rel);
            list.add(new RowsAdapter.Row(c.getString(R.string.relations), ad));
        }
        if (!a.characters.isEmpty()) {
            List<PersonAdapter.Person> people = new ArrayList<>();
            for (CharacterRef ch : a.characters) {
                String role = Labels.role(c, ch.role);
                if (ch.voiceActorName != null) role = role == null ? ch.voiceActorName : role + " · " + ch.voiceActorName;
                people.add(new PersonAdapter.Person(ch.name, role, ch.imageUrl));
            }
            list.add(new RowsAdapter.Row(c.getString(R.string.characters), new PersonAdapter(people)));
        }
        if (!a.staff.isEmpty()) {
            List<PersonAdapter.Person> people = new ArrayList<>();
            for (StaffRef s : a.staff) people.add(new PersonAdapter.Person(s.name, s.role, s.imageUrl));
            list.add(new RowsAdapter.Row(c.getString(R.string.staff), new PersonAdapter(people)));
        }
        if (!a.recommendations.isEmpty()) {
            AnimeCardAdapter ad = new AnimeCardAdapter(this::open, null, cardWidth, false);
            ad.submitList(a.recommendations);
            list.add(new RowsAdapter.Row(c.getString(R.string.recommendations), ad));
        }
        rows.submit(list);
    }

    private void open(Anime a) { Navigator.openDetails(requireContext(), a); }

    private void pickStatus() {
        LibraryEntry e = vm.entry().getValue();
        StatusDialog.show(requireContext(), e == null ? null : e.status, vm::setStatus);
    }

    private void watch(int episode) {
        Anime a = vm.current();
        if (a == null) return;
        int ep = episode > 0 ? episode : vm.nextEpisode(a);
        String title = null;
        for (Episode e : episodes.getCurrentList()) if (e.number == ep) title = e.title;
        PlaybackLauncher.play(requireActivity(), a, ep, title);
    }

    private void episodeActions(Episode e) {
        Context c = requireContext();
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        options.add(c.getString(R.string.watch_episode, e.number));
        actions.add(() -> watch(e.number));
        options.add(c.getString(e.watched ? R.string.mark_unwatched : R.string.mark_watched));
        actions.add(() -> vm.setWatched(e.number, e.number, !e.watched));
        if (e.number > 1) {
            options.add(c.getString(R.string.mark_up_to, e.number));
            actions.add(() -> vm.setWatched(1, e.number, true));
        }
        new AlertDialog.Builder(c, R.style.Kuro_Dialog)
                .setTitle(e.title != null && !(KuroApp.get().settings().hideSpoilers() && !e.watched)
                        ? c.getString(R.string.episode_number, e.number) + " · " + e.title
                        : c.getString(R.string.episode_number, e.number))
                .setItems(options.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void updateTopBar() {
        if (b == null) return;
        GridLayoutManager lm = (GridLayoutManager) b.detailsList.getLayoutManager();
        float progress = 1f;
        if (lm != null && lm.findFirstVisibleItemPosition() == 0) {
            View first = lm.findViewByPosition(0);
            if (first != null) {
                float threshold = Layouts.px(requireContext(), R.dimen.details_banner_height) * 0.6f;
                progress = Math.min(1f, Math.max(0f, -first.getTop() / threshold));
            }
        }
        b.detailsTopbar.setBackgroundColor((Math.round(progress * 0xFF) << 24));
        b.detailsBarTitle.setAlpha(progress < 0.85f ? 0f : (progress - 0.85f) / 0.15f);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
        rowsBuiltFor = null;
    }
}
