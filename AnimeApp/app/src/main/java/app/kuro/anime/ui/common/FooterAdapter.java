package app.kuro.anime.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import app.kuro.anime.R;
import app.kuro.anime.databinding.ItemFooterBinding;

/** "Loading more…" / "Tap to retry" row appended below paged grids. */
public final class FooterAdapter extends RecyclerView.Adapter<FooterAdapter.Holder> {
    public static final int VIEW_TYPE = R.layout.item_footer;

    public enum State { HIDDEN, LOADING, ERROR }

    private State state = State.HIDDEN;
    private final Runnable retry;

    public FooterAdapter(Runnable retry) { this.retry = retry; }

    public void setState(State s) {
        if (s == state) return;
        boolean wasShown = state != State.HIDDEN;
        state = s;
        boolean shown = s != State.HIDDEN;
        if (wasShown && !shown) notifyItemRemoved(0);
        else if (!wasShown && shown) notifyItemInserted(0);
        else if (shown) notifyItemChanged(0);
    }

    @Override public int getItemViewType(int position) { return VIEW_TYPE; }

    @Override public int getItemCount() { return state == State.HIDDEN ? 0 : 1; }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemFooterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        h.b.footerProgress.setVisibility(state == State.LOADING ? View.VISIBLE : View.GONE);
        h.b.footerRetry.setVisibility(state == State.ERROR ? View.VISIBLE : View.GONE);
        h.b.footerRetry.setOnClickListener(v -> retry.run());
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemFooterBinding b;

        Holder(ItemFooterBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
