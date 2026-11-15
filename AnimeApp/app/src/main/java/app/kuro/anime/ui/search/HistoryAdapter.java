package app.kuro.anime.ui.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.R;
import app.kuro.anime.databinding.ItemHistoryBinding;

final class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {
    interface Listener {
        void onPick(String q);

        void onRemove(String q);
    }

    private final List<String> items = new ArrayList<>();
    private final Listener listener;

    HistoryAdapter(Listener listener) { this.listener = listener; }

    void submit(List<String> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Holder h = new Holder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        h.itemView.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onPick(items.get(p));
        });
        h.b.historyRemove.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onRemove(items.get(p));
        });
        return h;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        String q = items.get(position);
        h.b.historyText.setText(q);
        h.b.historyRemove.setContentDescription(h.itemView.getContext().getString(R.string.search_remove_item, q));
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemHistoryBinding b;

        Holder(ItemHistoryBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
