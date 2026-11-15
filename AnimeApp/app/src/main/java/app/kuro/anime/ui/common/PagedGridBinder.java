package app.kuro.anime.ui.common;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import app.kuro.anime.KuroApp;
import app.kuro.anime.R;

/**
 * Wires a {@link PagedAnimeList} to a responsive grid: skeletons on first load, infinite scroll,
 * a retry footer, empty/error/offline states and the "saved data" banner.
 */
public final class PagedGridBinder {

    public interface EmptyState { void show(StateView view); }

    private final RecyclerView rv;
    private final StateView stateView;
    @Nullable private final TextView banner;
    private final PagedAnimeList list;
    private final AnimeCardAdapter cards;
    private final FooterAdapter footer;
    private final SkeletonAdapter skeleton;
    private final ConcatAdapter content;
    private final EmptyState empty;
    private final GridLayoutManager glm;
    private boolean showingSkeleton;

    public PagedGridBinder(LifecycleOwner owner, RecyclerView rv, StateView stateView, @Nullable TextView banner,
                           PagedAnimeList list, AnimeCardAdapter.Listener listener, EmptyState empty, boolean tv) {
        this.rv = rv;
        this.stateView = stateView;
        this.banner = banner;
        this.list = list;
        this.empty = empty;
        Context c = rv.getContext();
        int spacing = Layouts.dp(c, 12);
        glm = GridAutoFit.attach(rv, Layouts.gridMinWidth(c), spacing);
        cards = new AnimeCardAdapter(listener, null, 0, tv);
        footer = new FooterAdapter(list::retry);
        skeleton = new SkeletonAdapter(12, 0);
        content = new ConcatAdapter(new ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build(), cards, footer);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                RecyclerView.Adapter<?> a = rv.getAdapter();
                if (a == content && content.getItemViewType(position) == FooterAdapter.VIEW_TYPE) return glm.getSpanCount();
                return 1;
            }
        });
        rv.addItemDecoration(new SpacingDecoration(spacing));
        rv.setAdapter(content);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView r, int dx, int dy) {
                if (dy <= 0 && dx == 0) return;
                int last = glm.findLastVisibleItemPosition();
                if (last >= cards.getItemCount() - glm.getSpanCount() * 3) list.loadMore();
            }
        });
        list.state().observe(owner, this::render);
        KuroApp.get().settings().changes().observe(owner, v -> cards.notifyDataSetChanged());
    }

    private void render(@Nullable PagedAnimeList.State s) {
        if (s == null) return;
        boolean skeletonNow = s.loadingFirst;
        if (skeletonNow != showingSkeleton) {
            showingSkeleton = skeletonNow;
            rv.setAdapter(skeletonNow ? skeleton : content);
        }
        if (s.error != null && s.items.isEmpty()) {
            cards.submitList(null);
            stateView.showError(s.error, list::retry);
        } else if (s.isEmptyResult() && list.query() != null) {
            cards.submitList(null);
            empty.show(stateView);
        } else {
            stateView.hide();
            cards.submitList(s.items, () -> {
                // A short first page may not fill the screen; keep loading until it scrolls.
                if (!s.endReached && !s.loadingMore && rv.getAdapter() == content && !rv.canScrollVertically(1)) {
                    list.loadMore();
                }
            });
        }
        footer.setState(s.moreError != null ? FooterAdapter.State.ERROR
                : s.loadingMore ? FooterAdapter.State.LOADING : FooterAdapter.State.HIDDEN);
        if (banner != null) {
            boolean show = !s.items.isEmpty() && (s.stale || s.fallback);
            banner.setText(s.fallback ? R.string.state_fallback_banner : R.string.state_stale_banner);
            banner.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public void scrollToTop() { rv.smoothScrollToPosition(0); }

    public void refreshSpans() {
        int width = rv.getWidth() - rv.getPaddingLeft() - rv.getPaddingRight();
        glm.setSpanCount(GridAutoFit.spansFor(width, Layouts.gridMinWidth(rv.getContext()), Layouts.dp(rv.getContext(), 12)));
    }
}
