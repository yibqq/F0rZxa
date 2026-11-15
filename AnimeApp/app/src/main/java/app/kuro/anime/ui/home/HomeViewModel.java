package app.kuro.anime.ui.home;

import android.os.Parcelable;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.kuro.anime.KuroApp;
import app.kuro.anime.core.AppError;
import app.kuro.anime.core.AppExecutors;
import app.kuro.anime.core.Fetched;
import app.kuro.anime.core.Resource;
import app.kuro.anime.data.model.HomeFeed;
import app.kuro.anime.data.model.LibraryEntry;
import app.kuro.anime.data.model.WatchStatus;
import app.kuro.anime.data.repo.LibraryRepository;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<Resource<HomeFeed>> feed = new MutableLiveData<>();
    private final MutableLiveData<List<LibraryEntry>> continueWatching = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Set<String>> libraryKeys = new MutableLiveData<>(new HashSet<>());
    private final LibraryRepository library = KuroApp.get().library();
    private final Observer<Long> libraryObserver = v -> reloadLibrary();
    /** Horizontal scroll position of each row, kept across rotation. */
    final Map<String, Parcelable> rowStates = new HashMap<>();
    private boolean loading;

    public HomeViewModel() {
        library.changes().observeForever(libraryObserver);
        load(false);
    }

    public LiveData<Resource<HomeFeed>> feed() { return feed; }

    public LiveData<List<LibraryEntry>> continueWatching() { return continueWatching; }

    public LiveData<Set<String>> libraryKeys() { return libraryKeys; }

    public void load(boolean force) {
        if (loading) return;
        loading = true;
        Resource<HomeFeed> prev = feed.getValue();
        feed.setValue(Resource.loading(prev == null ? null : prev.data));
        AppExecutors.io().execute(() -> {
            try {
                Fetched<HomeFeed> f = KuroApp.get().anime().home(force);
                feed.postValue(f.toResource());
            } catch (AppError e) {
                feed.postValue(Resource.error(e, prev == null ? null : prev.data));
            } finally {
                loading = false;
            }
        });
    }

    private void reloadLibrary() {
        AppExecutors.disk().execute(() -> {
            continueWatching.postValue(library.byStatus(WatchStatus.WATCHING));
            Set<String> keys = new HashSet<>();
            for (LibraryEntry e : library.byStatus(null)) keys.add(e.key);
            libraryKeys.postValue(keys);
        });
    }

    @Override
    protected void onCleared() {
        library.changes().removeObserver(libraryObserver);
    }
}
