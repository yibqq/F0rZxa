package app.kuro.anime.ui.details;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import java.util.HashSet;
import java.util.Set;

import app.kuro.anime.KuroApp;
import app.kuro.anime.core.AppError;
import app.kuro.anime.core.AppExecutors;
import app.kuro.anime.core.Fetched;
import app.kuro.anime.core.Resource;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.EpisodeChunk;
import app.kuro.anime.data.model.LibraryEntry;
import app.kuro.anime.data.model.WatchStatus;
import app.kuro.anime.data.repo.LibraryRepository;
import app.kuro.anime.ui.common.Navigator;

/** State for a title page; shared by the touch and TV details screens. */
public class DetailsViewModel extends ViewModel {

    public final int anilistId;
    public final int malId;
    @Nullable public final String placeholderTitle;
    @Nullable public final String placeholderCover;
    @Nullable public final String placeholderColor;

    private final MutableLiveData<Resource<Anime>> anime = new MutableLiveData<>();
    private final MutableLiveData<LibraryEntry> entry = new MutableLiveData<>();
    private final MutableLiveData<Set<Integer>> watched = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<Resource<EpisodeChunk>> episodes = new MutableLiveData<>();
    private final MutableLiveData<Integer> completedEvent = new MutableLiveData<>();
    private final LibraryRepository library = KuroApp.get().library();
    private final Observer<Long> libraryObserver = v -> reloadLibrary();
    private int chunkIndex = -1;
    private int episodesGeneration;
    public boolean synopsisExpanded;

    public DetailsViewModel(SavedStateHandle state) {
        Integer al = state.get(Navigator.EXTRA_ANILIST_ID);
        Integer mal = state.get(Navigator.EXTRA_MAL_ID);
        anilistId = al == null ? 0 : al;
        malId = mal == null ? 0 : mal;
        placeholderTitle = state.get(Navigator.EXTRA_TITLE);
        placeholderCover = state.get(Navigator.EXTRA_COVER);
        placeholderColor = state.get(Navigator.EXTRA_COLOR);
        library.changes().observeForever(libraryObserver);
        load(false);
    }

    public LiveData<Resource<Anime>> anime() { return anime; }
    public LiveData<LibraryEntry> entry() { return entry; }
    public LiveData<Set<Integer>> watched() { return watched; }
    public LiveData<Resource<EpisodeChunk>> episodes() { return episodes; }
    /** Emits when the last episode was marked watched, so the UI can confirm completion. */
    public LiveData<Integer> completedEvent() { return completedEvent; }

    public int chunkIndex() { return Math.max(0, chunkIndex); }

    @Nullable
    public Anime current() {
        Resource<Anime> r = anime.getValue();
        return r == null ? null : r.data;
    }

    public void load(boolean force) {
        if (anilistId <= 0 && malId <= 0) {
            anime.setValue(Resource.error(new AppError(AppError.Kind.NOT_FOUND, "No ID"), null));
            return;
        }
        Anime prev = current();
        anime.setValue(Resource.loading(prev));
        AppExecutors.io().execute(() -> {
            try {
                Fetched<Anime> f = KuroApp.get().anime().details(anilistId, malId, force);
                anime.postValue(f.toResource());
                AppExecutors.main(() -> {
                    reloadLibrary();
                    int start = chunkIndex >= 0 ? chunkIndex : initialChunk(f.value);
                    loadEpisodes(start, force);
                });
            } catch (AppError e) {
                anime.postValue(Resource.error(e, prev));
            }
        });
    }

    /** Opens the chunk containing the user's next unwatched episode. */
    private int initialChunk(Anime a) {
        LibraryEntry e = entry.getValue();
        if (e == null || e.watchedCount == 0) return 0;
        return Math.max(0, (nextEpisode(a) - 1) / EpisodeChunk.SIZE);
    }

    public void loadEpisodes(int index, boolean force) {
        Anime a = current();
        if (a == null) return;
        chunkIndex = index;
        final int gen = ++episodesGeneration;
        Resource<EpisodeChunk> prev = episodes.getValue();
        episodes.setValue(Resource.loading(prev == null ? null : prev.data));
        AppExecutors.io().execute(() -> {
            try {
                Fetched<EpisodeChunk> f = KuroApp.get().episodes().loadChunk(a, index, force);
                if (gen == episodesGeneration) episodes.postValue(f.toResource());
            } catch (AppError e) {
                if (gen == episodesGeneration) episodes.postValue(Resource.error(e, null));
            }
        });
    }

    /** First episode not yet watched (1 if none, capped at the last aired episode). */
    public int nextEpisode(Anime a) {
        Set<Integer> w = watched.getValue();
        int aired = Math.max(1, a.airedEpisodeCount());
        for (int n = 1; n <= aired; n++) if (w == null || !w.contains(n)) return n;
        return aired;
    }

    public void setWatched(int from, int to, boolean value) {
        Anime a = current();
        if (a == null) return;
        AppExecutors.disk().execute(() -> {
            boolean complete = library.setWatched(a, from, to, value);
            if (complete) {
                LibraryEntry e = library.find(a);
                if (e == null || e.status != WatchStatus.COMPLETED) completedEvent.postValue(to);
            }
        });
    }

    public void setStatus(@Nullable WatchStatus status) {
        Anime a = current();
        if (a == null) return;
        AppExecutors.disk().execute(() -> library.setStatus(a, status));
    }

    public void toggleFavorite() {
        Anime a = current();
        if (a == null) return;
        LibraryEntry e = entry.getValue();
        boolean fav = e == null || !e.favorite;
        AppExecutors.disk().execute(() -> library.setFavorite(a, fav));
    }

    public void consumeCompletedEvent() { completedEvent.setValue(null); }

    private void reloadLibrary() {
        Anime a = current();
        if (a == null && anilistId <= 0) return;
        AppExecutors.disk().execute(() -> {
            LibraryEntry e;
            String key;
            if (a != null) {
                e = library.find(a);
                key = e != null ? e.key : a.key();
            } else {
                key = app.kuro.anime.data.model.AnimeKey.of(anilistId, malId);
                e = library.get(key);
            }
            entry.postValue(e);
            watched.postValue(library.watchedEpisodes(key));
        });
    }

    @Override
    protected void onCleared() {
        library.changes().removeObserver(libraryObserver);
    }
}
