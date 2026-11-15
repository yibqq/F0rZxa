package app.kuro.anime.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import app.kuro.anime.KuroApp;
import app.kuro.anime.R;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.TitleLanguage;
import app.kuro.anime.databinding.ItemAnimeCardBinding;

/** Poster card used by rows and grids everywhere (phones, tablets and TV). */
public class AnimeCardAdapter extends ListAdapter<Anime, AnimeCardAdapter.Holder> {

    public static final int VIEW_TYPE = R.layout.item_anime_card;

    public interface Listener {
        void onClick(Anime anime);

        default boolean onLongClick(Anime anime) { return false; }

        default void onFocused(Anime anime) {}
    }

    /** Optional per-item extras (subtitle override, progress). */
    public interface Extras {
        @Nullable default String subtitle(Context c, Anime a) { return null; }

        /** 0..1, or a negative value for none. */
        default float progress(Anime a) { return -1f; }
    }

    private final Listener listener;
    @Nullable private final Extras extras;
    private final int fixedWidthPx;
    private final boolean tv;

    /** @param fixedWidthPx card width for horizontal rows, or 0 to fill a grid cell. */
    public AnimeCardAdapter(Listener listener, @Nullable Extras extras, int fixedWidthPx, boolean tv) {
        super(DIFF);
        this.listener = listener;
        this.extras = extras;
        this.fixedWidthPx = fixedWidthPx;
        this.tv = tv;
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) { return getItem(position).key().hashCode(); }

    @Override
    public int getItemViewType(int position) { return VIEW_TYPE; }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAnimeCardBinding b = ItemAnimeCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        if (fixedWidthPx > 0) {
            ViewGroup.LayoutParams lp = b.getRoot().getLayoutParams();
            lp.width = fixedWidthPx;
            b.getRoot().setLayoutParams(lp);
        }
        Holder h = new Holder(b);
        Motion.pressable(b.getRoot());
        Motion.focusLift(b.getRoot(), tv ? 1.08f : 1.04f, (v, has) -> {
            int pos = h.getBindingAdapterPosition();
            if (has && pos != RecyclerView.NO_POSITION) listener.onFocused(getItem(pos));
        });
        b.getRoot().setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onClick(getItem(pos));
        });
        b.getRoot().setOnLongClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            return pos != RecyclerView.NO_POSITION && listener.onLongClick(getItem(pos));
        });
        return h;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Anime a = getItem(position);
        Context c = h.itemView.getContext();
        TitleLanguage lang = KuroApp.get().settings().titleLanguage();
        boolean saver = KuroApp.get().settings().dataSaver();
        String title = a.displayTitle(lang);
        h.b.cardTitle.setText(title);
        String sub = extras != null ? extras.subtitle(c, a) : null;
        if (sub == null) sub = Labels.metaLine(c, a, false);
        h.b.cardSubtitle.setText(sub);
        h.b.cardSubtitle.setVisibility(sub == null || sub.isEmpty() ? View.GONE : View.VISIBLE);
        if (a.averageScore > 0) {
            h.b.cardBadge.setText(c.getString(R.string.score_value, a.averageScore));
            h.b.cardBadge.setVisibility(View.VISIBLE);
        } else {
            h.b.cardBadge.setVisibility(View.GONE);
        }
        float p = extras != null ? extras.progress(a) : -1f;
        if (p >= 0) {
            h.b.cardProgress.setVisibility(View.VISIBLE);
            h.b.cardProgress.setProgress(Math.round(Math.min(1f, p) * 1000));
        } else {
            h.b.cardProgress.setVisibility(View.GONE);
        }
        Images.poster(h.b.cardPoster, a.coverFor(saver), a.coverColor);
        StringBuilder cd = new StringBuilder(title);
        if (sub != null && !sub.isEmpty()) cd.append(", ").append(sub);
        if (a.averageScore > 0) cd.append(", ").append(c.getString(R.string.cd_rating, a.averageScore));
        h.itemView.setContentDescription(cd);
    }

    @Override
    public void onViewRecycled(@NonNull Holder h) {
        Images.clear(h.b.cardPoster);
        h.itemView.setScaleX(1f);
        h.itemView.setScaleY(1f);
    }

    public static final class Holder extends RecyclerView.ViewHolder {
        final ItemAnimeCardBinding b;

        Holder(ItemAnimeCardBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final DiffUtil.ItemCallback<Anime> DIFF = new DiffUtil.ItemCallback<Anime>() {
        @Override
        public boolean areItemsTheSame(@NonNull Anime o, @NonNull Anime n) { return o.key().equals(n.key()); }

        @Override
        public boolean areContentsTheSame(@NonNull Anime o, @NonNull Anime n) {
            return Objects.equals(o.titleRomaji, n.titleRomaji) && Objects.equals(o.titleEnglish, n.titleEnglish)
                    && Objects.equals(o.coverUrl, n.coverUrl) && o.averageScore == n.averageScore
                    && Objects.equals(o.format, n.format) && o.seasonYear == n.seasonYear;
        }
    };
}
