package app.kuro.anime.ui.home;

import android.content.Context;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.kuro.anime.R;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.databinding.ItemFeaturedCarouselBinding;
import app.kuro.anime.databinding.ItemHomeSectionBinding;
import app.kuro.anime.ui.common.AnimeCardAdapter;
import app.kuro.anime.ui.common.Layouts;
import app.kuro.anime.ui.common.SkeletonAdapter;
import app.kuro.anime.ui.common.SpacingDecoration;

/** Vertical list of home rows: hero carousel, then horizontal poster rows. */
final class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final int FEATURED = 1, SECTION = 2, SKELETON_FEATURED = 3, SKELETON_SECTION = 4;

    static final class Row {
        final int type;
        final String key;
        final String title;
        final List<Anime> items;
        final AnimeCardAdapter.Extras extras;

        Row(int type, String key, String title, List<Anime> items, AnimeCardAdapter.Extras extras) {
            this.type = type;
            this.key = key;
            this.title = title;
            this.items = items;
            this.extras = extras;
        }
    }

    interface Listener extends FeaturedAdapter.Listener {
        void onSeeAll(Row row);
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;
    private final Map<String, Parcelable> rowStates;
    private final RecyclerView.RecycledViewPool cardPool = new RecyclerView.RecycledViewPool();
    private Set<String> libraryKeys;

    HomeAdapter(Listener listener, Map<String, Parcelable> rowStates) {
        this.listener = listener;
        this.rowStates = rowStates;
        cardPool.setMaxRecycledViews(AnimeCardAdapter.VIEW_TYPE, 40);
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    void submit(List<Row> newRows, Set<String> keys) {
        rows.clear();
        rows.addAll(newRows);
        libraryKeys = keys;
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int position) { return rows.get(position).type; }

    @Override public int getItemCount() { return rows.size(); }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case FEATURED: return new FeaturedHolder(ItemFeaturedCarouselBinding.inflate(inf, parent, false), listener);
            case SKELETON_FEATURED:
                View v = inf.inflate(R.layout.item_skeleton_featured, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            case SKELETON_SECTION:
            case SECTION:
            default:
                return new SectionHolder(ItemHomeSectionBinding.inflate(inf, parent, false), cardPool);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof FeaturedHolder) {
            ((FeaturedHolder) holder).bind(row, libraryKeys);
        } else if (holder instanceof SectionHolder) {
            SectionHolder h = (SectionHolder) holder;
            h.bind(row, listener, row.type == SKELETON_SECTION);
            Parcelable saved = rowStates.get(row.key);
            RecyclerView.LayoutManager lm = h.b.sectionList.getLayoutManager();
            if (lm != null) {
                if (saved != null) lm.onRestoreInstanceState(saved);
                else lm.scrollToPosition(0);
            }
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof SectionHolder) saveState((SectionHolder) holder);
    }

    void saveVisibleStates(RecyclerView parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            RecyclerView.ViewHolder h = parent.getChildViewHolder(parent.getChildAt(i));
            if (h instanceof SectionHolder) saveState((SectionHolder) h);
        }
    }

    private void saveState(SectionHolder h) {
        if (h.key != null && h.b.sectionList.getLayoutManager() != null) {
            rowStates.put(h.key, h.b.sectionList.getLayoutManager().onSaveInstanceState());
        }
    }

    static final class SectionHolder extends RecyclerView.ViewHolder {
        final ItemHomeSectionBinding b;
        String key;

        SectionHolder(ItemHomeSectionBinding b, RecyclerView.RecycledViewPool pool) {
            super(b.getRoot());
            this.b = b;
            Context c = b.getRoot().getContext();
            LinearLayoutManager lm = new LinearLayoutManager(c, RecyclerView.HORIZONTAL, false);
            lm.setInitialPrefetchItemCount(6);
            b.sectionList.setLayoutManager(lm);
            b.sectionList.setRecycledViewPool(pool);
            b.sectionList.setHasFixedSize(true);
            b.sectionList.addItemDecoration(new SpacingDecoration(Layouts.dp(c, 12)));
        }

        void bind(Row row, Listener listener, boolean skeleton) {
            key = row.key;
            Context c = itemView.getContext();
            int width = Layouts.px(c, R.dimen.row_card_width);
            b.sectionTitle.setText(row.title);
            if (skeleton) {
                b.sectionMore.setVisibility(View.INVISIBLE);
                b.sectionList.setAdapter(new SkeletonAdapter(8, width));
                return;
            }
            b.sectionMore.setVisibility(View.VISIBLE);
            b.sectionMore.setOnClickListener(v -> listener.onSeeAll(row));
            b.sectionMore.setContentDescription(c.getString(R.string.see_all) + ", " + row.title);
            AnimeCardAdapter adapter = new AnimeCardAdapter(a -> listener.onOpen(a), row.extras, width, false);
            b.sectionList.setAdapter(adapter);
            adapter.submitList(row.items);
        }
    }

    static final class FeaturedHolder extends RecyclerView.ViewHolder {
        final ItemFeaturedCarouselBinding b;
        final FeaturedAdapter adapter;
        int count;

        FeaturedHolder(ItemFeaturedCarouselBinding b, FeaturedAdapter.Listener listener) {
            super(b.getRoot());
            this.b = b;
            adapter = new FeaturedAdapter(listener);
            b.featuredPager.setLayoutManager(new LinearLayoutManager(b.getRoot().getContext(), RecyclerView.HORIZONTAL, false));
            b.featuredPager.setAdapter(adapter);
            new PagerSnapHelper().attachToRecyclerView(b.featuredPager);
            b.featuredPager.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                    if (state == RecyclerView.SCROLL_STATE_IDLE) updateDots();
                }
            });
            // Make each page exactly as wide as the carousel.
            b.featuredPager.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(@NonNull View view) {
                    ViewGroup.LayoutParams lp = view.getLayoutParams();
                    if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        view.setLayoutParams(lp);
                    }
                }

                @Override public void onChildViewDetachedFromWindow(@NonNull View view) {}
            });
        }

        void bind(Row row, Set<String> keys) {
            adapter.submit(row.items, keys);
            count = row.items.size();
            buildDots();
            updateDots();
        }

        private void buildDots() {
            LinearLayout dots = b.featuredDots;
            dots.removeAllViews();
            if (count < 2) return;
            Context c = dots.getContext();
            int size = Layouts.dp(c, 6);
            for (int i = 0; i < count; i++) {
                ImageView d = new ImageView(c);
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                g.setColor(0xFFFFFFFF);
                d.setImageDrawable(g);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMarginStart(size / 2);
                lp.setMarginEnd(size / 2);
                dots.addView(d, lp);
            }
        }

        private void updateDots() {
            LinearLayoutManager lm = (LinearLayoutManager) b.featuredPager.getLayoutManager();
            int current = lm == null ? 0 : Math.max(0, lm.findFirstCompletelyVisibleItemPosition());
            for (int i = 0; i < b.featuredDots.getChildCount(); i++) {
                b.featuredDots.getChildAt(i).setAlpha(i == current ? 1f : 0.35f);
            }
        }
    }
}
