package app.kuro.anime.ui.details;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import app.kuro.anime.KuroApp;
import app.kuro.anime.R;
import app.kuro.anime.core.Text;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.LibraryEntry;
import app.kuro.anime.data.model.TitleLanguage;
import app.kuro.anime.databinding.ItemDetailsHeaderBinding;
import app.kuro.anime.databinding.ItemDetailsHeaderWideBinding;
import app.kuro.anime.databinding.ViewDetailsBodyBinding;
import app.kuro.anime.databinding.ViewDetailsCoreBinding;
import app.kuro.anime.databinding.ViewInfoRowBinding;
import app.kuro.anime.ui.common.Images;
import app.kuro.anime.ui.common.Labels;
import app.kuro.anime.ui.common.Motion;

/** The hero: artwork, identity, primary actions, synopsis and facts. */
final class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.Holder> {

    static final int VIEW_TYPE = R.layout.item_details_header;

    interface Listener {
        void onWatch();

        void onStatus();

        void onFavorite();

        void onToggleSynopsis();
    }

    static final class Model {
        @Nullable Anime anime;
        @Nullable String placeholderTitle, placeholderCover, placeholderColor;
        @Nullable LibraryEntry entry;
        Set<Integer> watched;
        int nextEpisode = 1;
        boolean loading;
        boolean synopsisExpanded;
    }

    private final boolean wide;
    private final Listener listener;
    private Model model;

    HeaderAdapter(boolean wide, Listener listener) {
        this.wide = wide;
        this.listener = listener;
    }

    void submit(Model m) {
        boolean had = model != null;
        model = m;
        if (had) notifyItemChanged(0); else notifyItemInserted(0);
    }

    @Override public int getItemCount() { return model == null ? 0 : 1; }

    @Override public int getItemViewType(int position) { return VIEW_TYPE; }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        Holder h;
        if (wide) {
            ItemDetailsHeaderWideBinding b = ItemDetailsHeaderWideBinding.inflate(inf, parent, false);
            h = new Holder(b.getRoot(), b.heroBackdrop, b.core, b.body);
        } else {
            ItemDetailsHeaderBinding b = ItemDetailsHeaderBinding.inflate(inf, parent, false);
            h = new Holder(b.getRoot(), b.heroBackdrop, b.core, b.body);
        }
        Motion.pressable(h.core.watch);
        Motion.pressable(h.core.listStatus);
        Motion.pressable(h.core.favorite);
        h.core.watch.setOnClickListener(v -> listener.onWatch());
        h.core.listStatus.setOnClickListener(v -> listener.onStatus());
        h.core.favorite.setOnClickListener(v -> listener.onFavorite());
        h.body.synopsisToggle.setOnClickListener(v -> listener.onToggleSynopsis());
        return h;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Context c = h.itemView.getContext();
        Model m = model;
        Anime a = m.anime;
        TitleLanguage lang = KuroApp.get().settings().titleLanguage();
        String color = a != null ? a.coverColor : m.placeholderColor;
        String cover = a != null ? a.coverUrl : m.placeholderCover;
        String backdrop = a != null && a.bannerUrl != null ? a.bannerUrl : cover;
        if (!Text.isBlank(backdrop) && !backdrop.equals(h.backdrop.getTag())) {
            Images.backdrop(h.backdrop, backdrop, color);
            h.backdrop.setTag(backdrop);
        }
        if (!Text.isBlank(cover) && !cover.equals(h.core.poster.getTag())) {
            Images.poster(h.core.poster, cover, color);
            h.core.poster.setTag(cover);
        }
        h.core.title.setText(a != null ? a.displayTitle(lang) : m.placeholderTitle);
        String sub = a != null ? a.secondaryTitle(lang) : null;
        h.core.subtitle.setText(sub);
        h.core.subtitle.setVisibility(sub == null ? View.GONE : View.VISIBLE);

        if (a == null) {
            h.core.meta.setText(null);
            h.core.score.setVisibility(View.GONE);
            h.core.actions.setVisibility(View.INVISIBLE);
            h.body.getRoot().setVisibility(View.VISIBLE);
            setBodyVisible(h, false);
            h.body.bodyLoading.setVisibility(m.loading ? View.VISIBLE : View.GONE);
            return;
        }
        h.core.actions.setVisibility(View.VISIBLE);
        h.body.bodyLoading.setVisibility(View.GONE);
        setBodyVisible(h, true);

        List<String> meta = new ArrayList<>();
        String base = Labels.metaLine(c, a, false);
        if (!base.isEmpty()) meta.add(base);
        String status = Labels.status(c, a.status);
        if (status != null) meta.add(status);
        h.core.meta.setText(android.text.TextUtils.join(" · ", meta));
        if (a.averageScore > 0) {
            h.core.score.setText(c.getString(R.string.score_value, a.averageScore));
            h.core.score.setContentDescription(c.getString(R.string.cd_rating, a.averageScore));
            h.core.score.setVisibility(View.VISIBLE);
        } else {
            h.core.score.setVisibility(View.GONE);
        }

        // Accent from artwork (subtle): tint the progress bar only.
        int accent = Images.accentColor(a.coverColor);
        h.core.progressBar.setProgressTintList(ColorStateList.valueOf(accent));

        // Primary actions
        boolean playable = !a.isUpcoming();
        h.core.watch.setEnabled(playable);
        h.core.watch.setText(playable && a.airedEpisodeCount() > 1
                ? c.getString(R.string.watch_episode, m.nextEpisode) : c.getString(R.string.watch));
        LibraryEntry e = m.entry;
        if (e != null && e.status != null) {
            h.core.listStatus.setText(Labels.watchStatus(c, e.status));
            h.core.listStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0);
        } else {
            h.core.listStatus.setText(R.string.add_to_list);
            h.core.listStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_plus, 0, 0, 0);
        }
        boolean fav = e != null && e.favorite;
        h.core.favorite.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
        h.core.favorite.setContentDescription(c.getString(fav ? R.string.unfavorite : R.string.favorite));
        h.core.favorite.setSelected(false);

        int watchedCount = m.watched == null ? 0 : m.watched.size();
        if (watchedCount > 0) {
            int total = a.episodes > 0 ? a.episodes : 0;
            h.core.progressGroup.setVisibility(View.VISIBLE);
            h.core.progressText.setText(total > 0 ? c.getResources().getQuantityString(R.plurals.progress_format, total, watchedCount, total)
                    : c.getResources().getQuantityString(R.plurals.progress_format_unknown, watchedCount, watchedCount));
            h.core.progressBar.setVisibility(total > 0 ? View.VISIBLE : View.GONE);
            if (total > 0) h.core.progressBar.setProgress(Math.round(1000f * Math.min(1f, watchedCount / (float) total)));
        } else {
            h.core.progressGroup.setVisibility(View.GONE);
        }

        if (a.nextAiring != null && a.nextAiring.airingAtSec * 1000 > System.currentTimeMillis()) {
            h.core.nextAiring.setText(c.getString(R.string.next_episode_in, a.nextAiring.episode,
                    Labels.countdown(c, a.nextAiring.airingAtSec)));
            h.core.nextAiring.setVisibility(View.VISIBLE);
        } else {
            h.core.nextAiring.setVisibility(View.GONE);
        }

        // Genres
        h.body.genres.removeAllViews();
        for (String g : a.genres) {
            TextView chip = new TextView(c, null, 0, R.style.Kuro_Chip);
            chip.setText(Labels.genre(c, g));
            chip.setFocusable(false);
            chip.setClickable(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, c.getResources().getDimensionPixelSize(R.dimen.chip_height));
            lp.setMarginEnd(c.getResources().getDimensionPixelSize(R.dimen.space_s));
            h.body.genres.addView(chip, lp);
        }
        h.body.genresScroll.setVisibility(a.genres.isEmpty() ? View.GONE : View.VISIBLE);

        // Synopsis
        boolean hasSynopsis = !Text.isBlank(a.synopsis);
        h.body.synopsisLabel.setVisibility(hasSynopsis ? View.VISIBLE : View.GONE);
        h.body.synopsis.setVisibility(hasSynopsis ? View.VISIBLE : View.GONE);
        h.body.synopsis.setText(a.synopsis);
        h.body.synopsis.setMaxLines(m.synopsisExpanded ? Integer.MAX_VALUE : 5);
        h.body.synopsisToggle.setText(m.synopsisExpanded ? R.string.read_less : R.string.read_more);
        h.body.synopsisToggle.setVisibility(View.GONE);
        if (hasSynopsis) {
            h.body.synopsis.post(() -> {
                android.text.Layout layout = h.body.synopsis.getLayout();
                boolean truncated = layout != null && layout.getLineCount() > 0
                        && layout.getEllipsisCount(layout.getLineCount() - 1) > 0;
                h.body.synopsisToggle.setVisibility(truncated || m.synopsisExpanded ? View.VISIBLE : View.GONE);
            });
        }

        bindInfo(c, h.body.infoTable, a);
    }

    private static void setBodyVisible(Holder h, boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        h.body.synopsisLabel.setVisibility(v);
        h.body.synopsis.setVisibility(v);
        h.body.synopsisToggle.setVisibility(v);
        h.body.infoLabel.setVisibility(v);
        h.body.infoTable.setVisibility(v);
        h.body.genresScroll.setVisibility(v);
    }

    private static void bindInfo(Context c, LinearLayout table, Anime a) {
        table.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(c);
        addInfo(inf, table, c.getString(R.string.info_format), Labels.format(c, a.format));
        addInfo(inf, table, c.getString(R.string.info_episodes), a.episodes > 0 ? Labels.number(c, a.episodes) : null);
        addInfo(inf, table, c.getString(R.string.info_duration),
                a.duration > 0 ? c.getString(R.string.duration_minutes, a.duration) : null);
        addInfo(inf, table, c.getString(R.string.info_status), Labels.status(c, a.status));
        addInfo(inf, table, c.getString(R.string.info_aired), Labels.dateRange(c, a.startDate, a.endDate));
        String season = a.season != null && a.seasonYear > 0 ? Labels.season(c, a.season) + " " + Labels.number(c, a.seasonYear) : null;
        addInfo(inf, table, c.getString(R.string.info_season), season);
        addInfo(inf, table, c.getString(R.string.info_studios), a.studios.isEmpty() ? null : Text.join(a.studios, ", "));
        addInfo(inf, table, c.getString(R.string.info_source), Labels.source(a.source));
        addInfo(inf, table, c.getString(R.string.info_score), a.averageScore > 0 ? c.getString(R.string.score_value, a.averageScore) : null);
        List<String> alt = new ArrayList<>();
        if (a.titleEnglish != null) alt.add(a.titleEnglish);
        if (a.titleNative != null) alt.add(a.titleNative);
        for (int i = 0; i < Math.min(3, a.synonyms.size()); i++) alt.add(a.synonyms.get(i));
        alt.remove(a.displayTitle(KuroApp.get().settings().titleLanguage()));
        addInfo(inf, table, c.getString(R.string.info_alt_titles), alt.isEmpty() ? null : Text.join(alt, "\n"));
        // Hairline separators between rows
        for (int i = table.getChildCount() - 1; i > 0; i--) {
            View d = new View(c);
            d.setBackgroundColor(ContextCompat.getColor(c, R.color.stroke));
            table.addView(d, i, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        table.setVisibility(table.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private static void addInfo(LayoutInflater inf, LinearLayout table, String key, @Nullable String value) {
        if (Text.isBlank(value)) return;
        ViewInfoRowBinding r = ViewInfoRowBinding.inflate(inf, table, false);
        r.infoKey.setText(key);
        r.infoValue.setText(value);
        table.addView(r.getRoot());
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView backdrop;
        final ViewDetailsCoreBinding core;
        final ViewDetailsBodyBinding body;

        Holder(View root, ImageView backdrop, ViewDetailsCoreBinding core, ViewDetailsBodyBinding body) {
            super(root);
            this.backdrop = backdrop;
            this.core = core;
            this.body = body;
            backdrop.setBackgroundColor(Color.BLACK);
        }
    }
}
