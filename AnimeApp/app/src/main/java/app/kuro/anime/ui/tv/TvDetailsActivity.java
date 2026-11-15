package app.kuro.anime.ui.tv;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.kuro.anime.KuroApp;
import app.kuro.anime.R;
import app.kuro.anime.core.Resource;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.CharacterRef;
import app.kuro.anime.data.model.Episode;
import app.kuro.anime.data.model.EpisodeChunk;
import app.kuro.anime.data.model.LibraryEntry;
import app.kuro.anime.data.model.Relation;
import app.kuro.anime.data.model.WatchStatus;
import app.kuro.anime.databinding.ActivityTvDetailsBinding;
import app.kuro.anime.databinding.ItemTvRowBinding;
import app.kuro.anime.ui.common.AnimeCardAdapter;
import app.kuro.anime.ui.common.Images;
import app.kuro.anime.ui.common.Labels;
import app.kuro.anime.ui.common.Layouts;
import app.kuro.anime.ui.common.Motion;
import app.kuro.anime.ui.common.Navigator;
import app.kuro.anime.ui.common.SpacingDecoration;
import app.kuro.anime.ui.details.DetailsViewModel;
import app.kuro.anime.ui.details.EpisodeAdapter;
import app.kuro.anime.ui.details.PersonAdapter;
import app.kuro.anime.ui.details.PlaybackLauncher;
import app.kuro.anime.ui.details.StatusDialog;

/** TV title page: backdrop, focusable actions, then episode / character / related rows. */
public class TvDetailsActivity extends AppCompatActivity {

    private ActivityTvDetailsBinding b;
    private DetailsViewModel vm;
    private TvEpisodeAdapter episodes;
    private Anime rowsBuiltFor;
    private boolean initialFocus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityTvDetailsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        vm = new ViewModelProvider(this).get(DetailsViewModel.class);

        b.tvdTitle.setText(vm.placeholderTitle);
        Images.backdrop(b.tvdBackdrop, vm.placeholderCover, vm.placeholderColor);
        for (View v : new View[]{b.tvdWatch, b.tvdStatus, b.tvdFavorite}) Motion.focusLift(v, 1.05f, null);
        b.tvdWatch.setOnClickListener(v -> {
            Anime a = vm.current();
            if (a != null) PlaybackLauncher.play(this, a, vm.nextEpisode(a), null);
        });
        b.tvdStatus.setOnClickListener(v -> {
            LibraryEntry e = vm.entry().getValue();
            StatusDialog.show(this, e == null ? null : e.status, vm::setStatus);
        });
        b.tvdFavorite.setOnClickListener(v -> vm.toggleFavorite());

        episodes = new TvEpisodeAdapter(this::episodeActions);
        b.tvdEpisodes.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        b.tvdEpisodes.addItemDecoration(new SpacingDecoration(Layouts.dp(this, 18)));
        b.tvdEpisodes.setAdapter(episodes);
        b.tvdRanges.setOnSegmentSelected(i -> vm.loadEpisodes(i, false));

        vm.anime().observe(this, r -> render());
        vm.entry().observe(this, e -> renderActions());
        vm.watched().observe(this, w -> {
            renderActions();
            renderEpisodes();
        });
        vm.episodes().observe(this, r -> renderEpisodes());
        vm.completedEvent().observe(this, ep -> {
            if (ep == null) return;
            vm.consumeCompletedEvent();
            vm.setStatus(WatchStatus.COMPLETED);
            Toast.makeText(this, R.string.series_completed_toast, Toast.LENGTH_SHORT).show();
        });
    }

    private void render() {
        Resource<Anime> r = vm.anime().getValue();
        if (r == null) return;
        b.tvdLoading.setVisibility(r.isLoading() && r.data == null ? View.VISIBLE : View.GONE);
        if (r.data == null) {
            if (r.isError()) {
                b.tvdState.showError(r.error, () -> vm.load(true));
                b.tvdState.actionButton().requestFocus();
            }
            return;
        }
        b.tvdState.hide();
        Anime a = r.data;
        Context c = this;
        b.tvdTitle.setText(a.displayTitle(KuroApp.get().settings().titleLanguage()));
        List<String> parts = new ArrayList<>();
        String meta = Labels.metaLine(c, a, true);
        if (!meta.isEmpty()) parts.add(meta);
        String status = Labels.status(c, a.status);
        if (status != null) parts.add(status);
        if (a.averageScore > 0) parts.add(getString(R.string.score_value, a.averageScore));
        for (int i = 0; i < Math.min(4, a.genres.size()); i++) parts.add(Labels.genre(c, a.genres.get(i)));
        b.tvdMeta.setText(android.text.TextUtils.join(" · ", parts));
        b.tvdSynopsis.setText(a.synopsis);
        b.tvdSynopsis.setVisibility(a.synopsis == null ? View.GONE : View.VISIBLE);
        if (a.nextAiring != null && a.nextAiring.airingAtSec * 1000 > System.currentTimeMillis()) {
            b.tvdNext.setText(getString(R.string.next_episode_in, a.nextAiring.episode, Labels.countdown(c, a.nextAiring.airingAtSec)));
            b.tvdNext.setVisibility(View.VISIBLE);
        } else {
            b.tvdNext.setVisibility(View.GONE);
        }
        Images.backdrop(b.tvdBackdrop, a.bannerUrl != null ? a.bannerUrl : a.coverUrl, a.coverColor);
        renderActions();
        if (a != rowsBuiltFor) {
            rowsBuiltFor = a;
            buildRows(a);
        }
        if (!initialFocus) {
            initialFocus = true;
            TvFocus.request(b.tvdWatch);
        }
    }

    private void renderActions() {
        Anime a = vm.current();
        if (a == null) return;
        b.tvdWatch.setEnabled(!a.isUpcoming());
        b.tvdWatch.setText(a.airedEpisodeCount() > 1 ? getString(R.string.watch_episode, vm.nextEpisode(a)) : getString(R.string.watch));
        LibraryEntry e = vm.entry().getValue();
        if (e != null && e.status != null) {
            b.tvdStatus.setText(Labels.watchStatus(this, e.status));
            b.tvdStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0);
        } else {
            b.tvdStatus.setText(R.string.add_to_list);
            b.tvdStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_plus, 0, 0, 0);
        }
        boolean fav = e != null && e.favorite;
        b.tvdFavorite.setCompoundDrawablesRelativeWithIntrinsicBounds(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart, 0, 0, 0);
        b.tvdFavorite.setText(fav ? R.string.unfavorite : R.string.favorite);
    }

    private void renderEpisodes() {
        Anime a = vm.current();
        Resource<EpisodeChunk> r = vm.episodes().getValue();
        if (a == null || r == null) return;
        b.tvdEpisodesGroup.setVisibility(View.VISIBLE);
        EpisodeChunk chunk = r.data;
        int total = chunk != null ? chunk.total : a.airedEpisodeCount();
        int chunks = EpisodeChunk.chunkCount(total);
        if (chunks > 1 && b.tvdRanges.getChildCount() != chunks) {
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < chunks; i++) labels.add(EpisodeChunk.firstOf(i) + "–" + EpisodeChunk.lastOf(i, total));
            b.tvdRanges.setItems(labels);
        }
        b.tvdRanges.setVisibility(chunks > 1 ? View.VISIBLE : View.GONE);
        b.tvdRanges.setSelectedIndex(vm.chunkIndex(), false);
        String message = null;
        if (r.isError()) message = getString(R.string.episodes_error) + " " + Labels.error(this, r.error);
        else if (a.isUpcoming()) message = getString(R.string.episodes_not_aired);
        else if (!r.isLoading() && total == 0) message = getString(R.string.episodes_unavailable);
        b.tvdEpMessage.setText(message);
        b.tvdEpMessage.setVisibility(message == null ? View.GONE : View.VISIBLE);
        if (chunk != null && !r.isLoading()) {
            Set<Integer> w = vm.watched().getValue();
            episodes.submit(EpisodeAdapter.withWatched(chunk.episodes, w == null ? new HashSet<>() : w),
                    KuroApp.get().settings().hideSpoilers());
        }
    }

    private void episodeActions(Episode e) {
        Anime a = vm.current();
        if (a == null) return;
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        options.add(getString(R.string.watch_episode, e.number));
        actions.add(() -> PlaybackLauncher.play(this, a, e.number, e.title));
        options.add(getString(e.watched ? R.string.mark_unwatched : R.string.mark_watched));
        actions.add(() -> vm.setWatched(e.number, e.number, !e.watched));
        if (e.number > 1) {
            options.add(getString(R.string.mark_up_to, e.number));
            actions.add(() -> vm.setWatched(1, e.number, true));
        }
        new AlertDialog.Builder(this, R.style.Kuro_Dialog)
                .setTitle(getString(R.string.episode_number, e.number))
                .setItems(options.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void buildRows(Anime a) {
        b.tvdRows.removeAllViews();
        int cardWidth = Layouts.px(this, R.dimen.row_card_width);
        if (!a.characters.isEmpty()) {
            List<PersonAdapter.Person> people = new ArrayList<>();
            for (CharacterRef ch : a.characters) people.add(new PersonAdapter.Person(ch.name, Labels.role(this, ch.role), ch.imageUrl));
            addRow(getString(R.string.characters), new PersonAdapter(people));
        }
        if (!a.relations.isEmpty()) {
            List<Anime> rel = new ArrayList<>();
            for (Relation r : a.relations) rel.add(r.anime);
            AnimeCardAdapter ad = new AnimeCardAdapter(x -> Navigator.openDetails(this, x), new AnimeCardAdapter.Extras() {
                @Override
                public String subtitle(Context ctx, Anime x) {
                    for (Relation r : a.relations) if (r.anime == x) return Labels.relation(ctx, r.type);
                    return null;
                }
            }, cardWidth, true);
            ad.submitList(rel);
            addRow(getString(R.string.relations), ad);
        }
        if (!a.recommendations.isEmpty()) {
            AnimeCardAdapter ad = new AnimeCardAdapter(x -> Navigator.openDetails(this, x), null, cardWidth, true);
            ad.submitList(a.recommendations);
            addRow(getString(R.string.recommendations), ad);
        }
    }

    private void addRow(String title, RecyclerView.Adapter<?> adapter) {
        ItemTvRowBinding row = ItemTvRowBinding.inflate(LayoutInflater.from(this), b.tvdRows, false);
        row.tvRowTitle.setText(title);
        row.tvRowTitle.setPaddingRelative(Layouts.px(this, R.dimen.gutter), 0, Layouts.px(this, R.dimen.gutter), 0);
        row.tvRowList.setPaddingRelative(Layouts.px(this, R.dimen.gutter), row.tvRowList.getPaddingTop(),
                Layouts.px(this, R.dimen.gutter), row.tvRowList.getPaddingBottom());
        row.tvRowList.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        row.tvRowList.addItemDecoration(new SpacingDecoration(Layouts.dp(this, 18)));
        row.tvRowList.setAdapter(adapter);
        b.tvdRows.addView(row.getRoot());
    }
}
