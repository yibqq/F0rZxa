package app.kuro.anime.ui.tv;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.R;
import app.kuro.anime.data.model.Episode;
import app.kuro.anime.databinding.ItemTvEpisodeBinding;
import app.kuro.anime.ui.common.Images;
import app.kuro.anime.ui.common.Labels;
import app.kuro.anime.ui.common.Motion;

final class TvEpisodeAdapter extends RecyclerView.Adapter<TvEpisodeAdapter.Holder> {
    interface Listener { void onEpisode(Episode e); }

    private final List<Episode> items = new ArrayList<>();
    private final Listener listener;
    private boolean hideSpoilers;

    TvEpisodeAdapter(Listener listener) { this.listener = listener; }

    void submit(List<Episode> list, boolean hideSpoilers) {
        this.hideSpoilers = hideSpoilers;
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Holder h = new Holder(ItemTvEpisodeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        Motion.focusLift(h.itemView, 1.06f, null);
        h.itemView.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onEpisode(items.get(p));
        });
        return h;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Episode e = items.get(position);
        Context c = h.itemView.getContext();
        boolean conceal = hideSpoilers && !e.watched;
        String label = c.getString(R.string.episode_number, e.number);
        String date = Labels.date(c, e.airDate);
        h.b.tveLabel.setText(date == null ? label : label + " · " + date);
        h.b.tveTitle.setText(!conceal && e.title != null ? e.title : label);
        if (e.thumbnailUrl != null && !conceal) {
            Images.poster(h.b.tveThumb, e.thumbnailUrl, null);
            h.b.tveNumber.setVisibility(View.GONE);
        } else {
            Images.clear(h.b.tveThumb);
            h.b.tveNumber.setText(Labels.number(c, e.number));
            h.b.tveNumber.setVisibility(View.VISIBLE);
        }
        h.b.tveThumb.setAlpha(e.watched ? 0.55f : 1f);
        h.b.tveWatched.setVisibility(e.watched ? View.VISIBLE : View.GONE);
        h.itemView.setContentDescription(label + (e.title != null && !conceal ? ", " + e.title : "")
                + (e.watched ? ", " + c.getString(R.string.episode_watched_state) : ""));
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemTvEpisodeBinding b;

        Holder(ItemTvEpisodeBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
