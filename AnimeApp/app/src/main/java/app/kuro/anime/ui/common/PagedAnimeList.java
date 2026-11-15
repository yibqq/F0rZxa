package app.kuro.anime.ui.common;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.kuro.anime.KuroApp;
import app.kuro.anime.core.AppError;
import app.kuro.anime.core.AppExecutors;
import app.kuro.anime.core.Fetched;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.AnimePage;
import app.kuro.anime.data.model.AnimeQuery;

/**
 * Incremental (infinite-scroll) loading of a catalog query. Lives inside a ViewModel so items,
 * page and scroll position survive configuration changes. Stale responses from an older query
 * are discarded via a generation counter.
 */
public final class PagedAnimeList {

    public static final class State {
        public final List<Anime> items;
        public final boolean loadingFirst;
        public final boolean loadingMore;
        public final boolean endReached;
        @Nullable public final AppError error;      // first page failed
        @Nullable public final AppError moreError;  // a later page failed
        public final boolean stale;
        public final boolean fallback;

        State(List<Anime> items, boolean loadingFirst, boolean loadingMore, boolean endReached,
              @Nullable AppError error, @Nullable AppError moreError, boolean stale, boolean fallback) {
            this.items = items;
            this.loadingFirst = loadingFirst;
            this.loadingMore = loadingMore;
            this.endReached = endReached;
            this.error = error;
            this.moreError = moreError;
            this.stale = stale;
            this.fallback = fallback;
        }

        public boolean isEmptyResult() { return !loadingFirst && error == null && items.isEmpty(); }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>();
    @Nullable private AnimeQuery query;
    private final List<Anime> items = new ArrayList<>();
    private final Set<String> keys = new HashSet<>();
    private int nextPage = 1;
    private boolean endReached;
    private boolean loading;
    private int generation;
    private boolean stale, fallback;
    @Nullable private AppError moreError;

    public LiveData<State> state() { return state; }

    @Nullable public AnimeQuery query() { return query; }

    /** Starts over with a new query (or refreshes the current one when {@code force}). */
    public void submit(AnimeQuery q, boolean force) {
        if (!force && q.equals(query) && (state.getValue() != null && state.getValue().error == null)) return;
        query = q.copy();
        generation++;
        items.clear();
        keys.clear();
        nextPage = 1;
        endReached = false;
        loading = false;
        moreError = null;
        stale = fallback = false;
        load(force);
    }

    public void clear() {
        query = null;
        generation++;
        items.clear();
        keys.clear();
        loading = false;
        state.setValue(new State(Collections.emptyList(), false, false, true, null, null, false, false));
    }

    public void loadMore() {
        if (query == null || loading || endReached || moreError != null) return;
        load(false);
    }

    public void retry() {
        if (query == null) return;
        if (items.isEmpty()) submit(query, true);
        else {
            moreError = null;
            load(false);
        }
    }

    private void load(boolean force) {
        final AnimeQuery q = query;
        final int page = nextPage;
        final int gen = generation;
        loading = true;
        publish(null);
        AppExecutors.io().execute(() -> {
            try {
                Fetched<AnimePage> f = KuroApp.get().anime().browse(q, page, force);
                AppExecutors.main(() -> {
                    if (gen != generation) return;
                    loading = false;
                    if (page == 1) {
                        stale = f.stale;
                        fallback = f.fallback;
                    }
                    for (Anime a : f.value.items) if (keys.add(a.key())) items.add(a);
                    nextPage = page + 1;
                    endReached = !f.value.hasNext || f.value.items.isEmpty();
                    publish(null);
                });
            } catch (AppError e) {
                AppExecutors.main(() -> {
                    if (gen != generation) return;
                    loading = false;
                    if (page == 1) publish(e);
                    else {
                        moreError = e;
                        publish(null);
                    }
                });
            }
        });
    }

    private void publish(@Nullable AppError firstError) {
        boolean first = items.isEmpty();
        state.setValue(new State(new ArrayList<>(items), loading && first, loading && !first, endReached,
                firstError, moreError, stale, fallback));
    }
}
