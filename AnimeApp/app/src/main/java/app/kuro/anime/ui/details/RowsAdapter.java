package app.kuro.anime.ui.details;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.R;
import app.kuro.anime.databinding.ItemDetailsRowBinding;
import app.kuro.anime.ui.common.Layouts;
import app.kuro.anime.ui.common.SpacingDecoration;

/** Titled horizontal rows below the episodes: related titles, characters, staff, recommendations. */
final class RowsAdapter extends RecyclerView.Adapter<RowsAdapter.Holder> {
    static final int VIEW_TYPE = R.layout.item_details_row;

    static final class Row {
        final String title;
        final RecyclerView.Adapter<?> adapter;

        Row(String title, RecyclerView.Adapter<?> adapter) {
            this.title = title;
            this.adapter = adapter;
        }
    }

    private final List<Row> rows = new ArrayList<>();

    void submit(List<Row> list) {
        rows.clear();
        rows.addAll(list);
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int position) { return VIEW_TYPE; }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDetailsRowBinding b = ItemDetailsRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        Context c = parent.getContext();
        b.rowList.setLayoutManager(new LinearLayoutManager(c, RecyclerView.HORIZONTAL, false));
        b.rowList.addItemDecoration(new SpacingDecoration(Layouts.dp(c, 12)));
        return new Holder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Row r = rows.get(position);
        h.b.rowTitle.setText(r.title);
        if (h.b.rowList.getAdapter() != r.adapter) h.b.rowList.setAdapter(r.adapter);
    }

    @Override public int getItemCount() { return rows.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemDetailsRowBinding b;

        Holder(ItemDetailsRowBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
