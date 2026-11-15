package app.kuro.anime.ui.details;

import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import app.kuro.anime.R;
import app.kuro.anime.data.model.Episode;
import app.kuro.anime.databinding.ItemEpisodeBinding;
import app.kuro.anime.ui.common.Images;
import app.kuro.anime.ui.common.Labels;

/** Episode rows: real thumbnail when one exists (otherwise the number), title, date, watched toggle. */
public final class EpisodeAdapter extends ListAdapter<Episode, EpisodeAdapter.Holder> {
    public static final int VIEW_TYPE = R.layout.item_episode;

    public interface Listener {
        void onEpisodeClick(Episode e);

        void onToggleWatched(Episode e);
    }

    private final Listener listener;
    private boolean hideSpoilers;

    public EpisodeAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setHideSpoilers(boolean hide) {
        if (hide != hideSpoilers) {
            hideSpoilers = hide;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    @Override public int getItemViewType(int position) { return VIEW_TYPE; }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Holder h = new Holder(ItemEpisodeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        h.itemView.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onEpisodeClick(getItem(p));
        });
        h.b.epToggle.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onToggleWatched(getItem(p));
        });
        return h;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Episode e = getItem(position);
        Context c = h.itemView.getContext();
        boolean conceal = hideSpoilers && !e.watched;
        String label = c.getString(R.string.episode_number, e.number);
        h.b.epLabel.setText(label);
        String title = conceal ? null : e.title;
        h.b.epTitle.setText(title != null ? title : label);
        h.b.epTitle.setTextColor(ContextCompat.getColor(c, e.watched ? R.color.text_secondary : R.color.text_primary));
        String date = Labels.date(c, e.airDate);
        h.b.epDate.setText(date);
        h.b.epDate.setVisibility(date == null ? View.GONE : View.VISIBLE);
        if (e.filler || e.recap) {
            h.b.epBadge.setText(e.recap ? R.string.episode_recap : R.string.episode_filler);
            h.b.epBadge.setVisibility(View.VISIBLE);
        } else {
            h.b.epBadge.setVisibility(View.GONE);
        }

        if (e.thumbnailUrl != null && !(conceal && Build.VERSION.SDK_INT < Build.VERSION_CODES.S)) {
            Images.poster(h.b.epThumb, e.thumbnailUrl, null);
            h.b.epThumb.setContentDescription(c.getString(R.string.cd_episode_thumb, e.number));
            h.b.epNumberBig.setVisibility(View.GONE);
        } else {
            Images.clear(h.b.epThumb);
            h.b.epNumberBig.setText(Labels.number(c, e.number));
            h.b.epNumberBig.setVisibility(View.VISIBLE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            h.b.epThumb.setRenderEffect(conceal && e.thumbnailUrl != null
                    ? RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP) : null);
        }
        h.b.epThumb.setAlpha(e.watched ? 0.55f : 1f);
        h.b.epWatchedOverlay.setVisibility(e.watched ? View.VISIBLE : View.GONE);
        h.b.epToggle.setImageResource(e.watched ? R.drawable.ic_check_circle : R.drawable.ic_circle);
        h.b.epToggle.setImageTintList(ContextCompat.getColorStateList(c, e.watched ? R.color.text_primary : R.color.text_tertiary));
        h.b.epToggle.setContentDescription(c.getString(e.watched ? R.string.mark_unwatched : R.string.mark_watched) + ", " + label);
        StringBuilder cd = new StringBuilder(label);
        if (title != null) cd.append(", ").append(title);
        if (date != null) cd.append(", ").append(date);
        if (e.watched) cd.append(", ").append(c.getString(R.string.episode_watched_state));
        h.itemView.setContentDescription(cd);
    }

    @Override
    public void onViewRecycled(@NonNull Holder h) { Images.clear(h.b.epThumb); }

    /** Copies episodes and applies watched flags so DiffUtil sees changes. */
    public static List<Episode> withWatched(List<Episode> source, java.util.Set<Integer> watched) {
        List<Episode> out = new ArrayList<>(source.size());
        for (Episode s : source) {
            Episode e = new Episode(s.number);
            e.title = s.title;
            e.titleAlt = s.titleAlt;
            e.airDate = s.airDate;
            e.thumbnailUrl = s.thumbnailUrl;
            e.lengthMinutes = s.lengthMinutes;
            e.filler = s.filler;
            e.recap = s.recap;
            e.watched = watched != null && watched.contains(s.number);
            out.add(e);
        }
        return out;
    }

    public static final class Holder extends RecyclerView.ViewHolder {
        final ItemEpisodeBinding b;

        Holder(ItemEpisodeBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final DiffUtil.ItemCallback<Episode> DIFF = new DiffUtil.ItemCallback<Episode>() {
        @Override public boolean areItemsTheSame(@NonNull Episode o, @NonNull Episode n) { return o.number == n.number; }

        @Override
        public boolean areContentsTheSame(@NonNull Episode o, @NonNull Episode n) {
            return o.watched == n.watched && Objects.equals(o.title, n.title)
                    && Objects.equals(o.thumbnailUrl, n.thumbnailUrl) && o.filler == n.filler && o.recap == n.recap
                    && (o.airDate == null) == (n.airDate == null);
        }
    };
}
