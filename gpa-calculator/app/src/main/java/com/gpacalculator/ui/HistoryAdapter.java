package com.gpacalculator.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import com.gpacalculator.R;
import com.gpacalculator.logic.GpaCalculator;
import com.gpacalculator.model.GradeScale;
import com.gpacalculator.model.HistoryRecord;
import com.gpacalculator.ui.util.Formatters;

import java.util.ArrayList;
import java.util.List;

/** Large-title header followed by one card per saved calculation. */
final class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    static final int TYPE_HEADER = 0;
    static final int TYPE_RECORD = 1;

    interface OnRecordClickListener {
        void onRecordClick(HistoryRecord record);
    }

    private final Context context;
    private final OnRecordClickListener listener;
    private List<HistoryRecord> items = new ArrayList<>();

    HistoryAdapter(Context context, OnRecordClickListener listener) {
        this.context = context;
        this.listener = listener;
        setHasStableIds(true);
    }

    int recordCount() {
        return items.size();
    }

    HistoryRecord recordAt(int adapterPosition) {
        int i = adapterPosition - 1;
        return i >= 0 && i < items.size() ? items.get(i) : null;
    }

    void removeAt(int adapterPosition) {
        int i = adapterPosition - 1;
        if (i < 0 || i >= items.size()) return;
        List<HistoryRecord> next = new ArrayList<>(items);
        next.remove(i);
        items = next;
        notifyItemRemoved(adapterPosition);
    }

    void submit(List<HistoryRecord> next) {
        List<HistoryRecord> old = items;
        List<HistoryRecord> fresh = new ArrayList<>(next);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return old.size();
            }

            @Override
            public int getNewListSize() {
                return fresh.size();
            }

            @Override
            public boolean areItemsTheSame(int o, int n) {
                return old.get(o).id == fresh.get(n).id;
            }

            @Override
            public boolean areContentsTheSame(int o, int n) {
                return old.get(o).sameContent(fresh.get(n));
            }
        });
        items = fresh;
        diff.dispatchUpdatesTo(new ListUpdateCallback() {
            @Override
            public void onInserted(int position, int count) {
                notifyItemRangeInserted(position + 1, count);
            }

            @Override
            public void onRemoved(int position, int count) {
                notifyItemRangeRemoved(position + 1, count);
            }

            @Override
            public void onMoved(int fromPosition, int toPosition) {
                notifyItemMoved(fromPosition + 1, toPosition + 1);
            }

            @Override
            public void onChanged(int position, int count, Object payload) {
                notifyItemRangeChanged(position + 1, count, payload);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_RECORD;
    }

    @Override
    public long getItemId(int position) {
        return position == 0 ? Long.MIN_VALUE : items.get(position - 1).id;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new RecyclerView.ViewHolder(inflater.inflate(R.layout.item_history_header, parent, false)) {
            };
        }
        return new RecordHolder(inflater.inflate(R.layout.item_history, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof RecordHolder) {
            ((RecordHolder) holder).bind(items.get(position - 1));
        }
    }

    final class RecordHolder extends RecyclerView.ViewHolder {
        final View card;
        private final View row;
        private final TextView gpa;
        private final TextView scale;
        private final TextView date;
        private final TextView summary;
        private final TextView cumulative;
        private HistoryRecord record;

        RecordHolder(View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.history_card);
            row = itemView.findViewById(R.id.history_row);
            gpa = itemView.findViewById(R.id.history_gpa);
            scale = itemView.findViewById(R.id.history_scale);
            date = itemView.findViewById(R.id.history_date);
            summary = itemView.findViewById(R.id.history_summary);
            cumulative = itemView.findViewById(R.id.history_cumulative);
            row.setOnClickListener(v -> {
                if (record != null) listener.onRecordClick(record);
            });
        }

        void bind(HistoryRecord r) {
            record = r;
            gpa.setText(GpaCalculator.format(r.semesterGpa));
            scale.setText(context.getString(R.string.history_scale,
                    context.getString(r.scale == GradeScale.FIVE ? R.string.scale_5 : R.string.scale_4)));
            date.setText(Formatters.relativeDate(context, r.createdAt));
            summary.setText(context.getString(R.string.pair_dot,
                    Formatters.courses(context, r.courses.size()),
                    Formatters.creditHours(context, r.semesterCredits)));
            if (r.hasCumulative()) {
                cumulative.setText(context.getString(R.string.history_cumulative,
                        GpaCalculator.format(r.cumulativeGpa)));
                cumulative.setVisibility(View.VISIBLE);
            } else {
                cumulative.setVisibility(View.GONE);
            }
            row.setContentDescription(gpa.getText() + " " + scale.getText() + ", " + date.getText()
                    + ", " + summary.getText()
                    + (r.hasCumulative() ? ", " + cumulative.getText() : ""));
        }
    }
}
