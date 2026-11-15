package app.kuro.anime.ui.details;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.R;
import app.kuro.anime.core.AppError;
import app.kuro.anime.core.Text;
import app.kuro.anime.data.model.EpisodeChunk;
import app.kuro.anime.databinding.ItemEpisodesHeaderBinding;
import app.kuro.anime.ui.common.Labels;

/** "Episodes" title, range selector for long series (1–100, 101–200…), source credit and states. */
final class EpisodesHeaderAdapter extends RecyclerView.Adapter<EpisodesHeaderAdapter.Holder> {
    static final int VIEW_TYPE = R.layout.item_episodes_header;

    interface Listener {
        void onRange(int index);

        void onRetry();
    }

    static final class Model {
        int total;
        int chunkIndex;
        boolean loading;
        boolean upcoming;
        @Nullable AppError error;
        List<String> sources = new ArrayList<>();
    }

    private final Listener listener;
    private Model model;
    private boolean visible;

    EpisodesHeaderAdapter(Listener listener) { this.listener = listener; }

    void submit(@Nullable Model m) {
        boolean show = m != null;
        model = m;
        if (show && !visible) notifyItemInserted(0);
        else if (!show && visible) notifyItemRemoved(0);
        else if (show) notifyItemChanged(0);
        visible = show;
    }

    @Override public int getItemCount() { return visible ? 1 : 0; }

    @Override public int getItemViewType(int position) { return VIEW_TYPE; }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Holder h = new Holder(ItemEpisodesHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        h.b.epRanges.setOnSegmentSelected(listener::onRange);
        h.b.epRetry.setOnClickListener(v -> listener.onRetry());
        return h;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Context c = h.itemView.getContext();
        Model m = model;
        h.b.epCount.setText(m.total > 0 ? c.getResources().getQuantityString(R.plurals.episodes_count, m.total, m.total) : null);
        int chunks = EpisodeChunk.chunkCount(m.total);
        if (chunks > 1) {
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < chunks; i++) {
                labels.add(EpisodeChunk.firstOf(i) + "–" + EpisodeChunk.lastOf(i, m.total));
            }
            if (h.b.epRanges.getChildCount() != chunks) h.b.epRanges.setItems(labels);
            h.b.epRanges.setSelectedIndex(m.chunkIndex, false);
            h.b.epRangesScroll.setVisibility(View.VISIBLE);
        } else {
            h.b.epRangesScroll.setVisibility(View.GONE);
        }

        String message = null;
        if (m.error != null) message = c.getString(R.string.episodes_error) + " " + Labels.error(c, m.error);
        else if (!m.loading && m.upcoming) message = c.getString(R.string.episodes_not_aired);
        else if (!m.loading && m.total == 0) message = c.getString(R.string.episodes_unavailable);
        h.b.epMessage.setText(message);
        h.b.epMessage.setVisibility(message == null ? View.GONE : View.VISIBLE);
        h.b.epRetry.setVisibility(m.error != null ? View.VISIBLE : View.GONE);

        if (!m.sources.isEmpty() && m.error == null) {
            h.b.epSource.setText(c.getString(R.string.episodes_source, Text.join(m.sources, " · ")));
            h.b.epSource.setVisibility(View.VISIBLE);
        } else {
            h.b.epSource.setVisibility(View.GONE);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemEpisodesHeaderBinding b;

        Holder(ItemEpisodesHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
