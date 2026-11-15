package app.kuro.anime.ui.tv;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.R;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.databinding.ItemTvRowBinding;
import app.kuro.anime.ui.common.AnimeCardAdapter;
import app.kuro.anime.ui.common.Layouts;
import app.kuro.anime.ui.common.Motion;
import app.kuro.anime.ui.common.SpacingDecoration;

/** Vertical stack of D-pad rows: poster rows and a genre-tile row. */
final class TvRowsAdapter extends RecyclerView.Adapter<TvRowsAdapter.Holder> {

    static final class Row {
        final String key;
        final String title;
        final List<Anime> items;
        final List<String[]> genres; // {key, label}
        final AnimeCardAdapter.Extras extras;

        Row(String key, String title, List<Anime> items, AnimeCardAdapter.Extras extras) {
            this.key = key;
            this.title = title;
            this.items = items;
            this.genres = null;
            this.extras = extras;
        }

        Row(String key, String title, List<String[]> genres) {
            this.key = key;
            this.title = title;
            this.items = null;
            this.genres = genres;
            this.extras = null;
        }
    }

    interface Listener extends AnimeCardAdapter.Listener {
        void onGenre(String key, String label);
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;
    private final RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();

    TvRowsAdapter(Listener listener) {
        this.listener = listener;
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    void submit(List<Row> list) {
        rows.clear();
        rows.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTvRowBinding b = ItemTvRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        Context c = parent.getContext();
        b.tvRowList.setLayoutManager(new LinearLayoutManager(c, RecyclerView.HORIZONTAL, false));
        b.tvRowList.addItemDecoration(new SpacingDecoration(Layouts.dp(c, 18)));
        b.tvRowList.setRecycledViewPool(pool);
        return new Holder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Row r = rows.get(position);
        h.b.tvRowTitle.setText(r.title);
        Context c = h.itemView.getContext();
        if (r.items != null) {
            AnimeCardAdapter a = new AnimeCardAdapter(listener, r.extras, Layouts.px(c, R.dimen.row_card_width), true);
            h.b.tvRowList.setAdapter(a);
            a.submitList(r.items);
        } else {
            h.b.tvRowList.setAdapter(new GenreAdapter(r.genres, listener));
        }
    }

    @Override public int getItemCount() { return rows.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemTvRowBinding b;

        Holder(ItemTvRowBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static final class GenreAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<String[]> genres;
        private final Listener listener;

        GenreAdapter(List<String[]> genres, Listener listener) {
            this.genres = genres;
            this.listener = listener;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            TextView t = new TextView(c, null, 0, R.style.Kuro_Chip);
            t.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Layouts.dp(c, 52)));
            t.setTextSize(16);
            t.setPaddingRelative(Layouts.dp(c, 24), 0, Layouts.dp(c, 24), 0);
            Motion.focusLift(t, 1.08f, null);
            return new RecyclerView.ViewHolder(t) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            String[] g = genres.get(position);
            TextView t = (TextView) h.itemView;
            t.setText(g[1]);
            t.setOnClickListener(v -> listener.onGenre(g[0], g[1]));
        }

        @Override public int getItemCount() { return genres.size(); }
    }
}
