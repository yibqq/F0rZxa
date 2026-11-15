package app.kuro.anime.ui.details;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import app.kuro.anime.R;
import app.kuro.anime.ui.common.Pulse;

final class EpisodeSkeletonAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    static final int VIEW_TYPE = R.layout.item_episode_skeleton;
    private int count;

    void setCount(int c) {
        if (c == count) return;
        int old = count;
        count = c;
        if (old > 0) notifyItemRangeRemoved(0, old);
        if (c > 0) notifyItemRangeInserted(0, c);
    }

    @Override public int getItemViewType(int position) { return VIEW_TYPE; }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_episode_skeleton, parent, false)) {};
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}

    @Override public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder h) { Pulse.start(h.itemView); }

    @Override public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder h) { Pulse.stop(h.itemView); }

    @Override public int getItemCount() { return count; }
}
