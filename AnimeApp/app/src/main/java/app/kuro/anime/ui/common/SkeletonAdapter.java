package app.kuro.anime.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import app.kuro.anime.R;

/** Placeholder cards shown while content loads (instead of a spinner). */
public final class SkeletonAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE = R.layout.item_skeleton_card;
    private final int count;
    private final int fixedWidthPx;

    public SkeletonAdapter(int count, int fixedWidthPx) {
        this.count = count;
        this.fixedWidthPx = fixedWidthPx;
    }

    @Override public int getItemViewType(int position) { return VIEW_TYPE; }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_skeleton_card, parent, false);
        if (fixedWidthPx > 0) v.getLayoutParams().width = fixedWidthPx;
        return new RecyclerView.ViewHolder(v) {};
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {}

    @Override public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder h) { Pulse.start(h.itemView); }

    @Override public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder h) { Pulse.stop(h.itemView); }

    @Override public int getItemCount() { return count; }
}
