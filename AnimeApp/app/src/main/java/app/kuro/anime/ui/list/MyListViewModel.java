package app.kuro.anime.ui.list;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import java.util.List;

import app.kuro.anime.KuroApp;
import app.kuro.anime.core.AppExecutors;
import app.kuro.anime.data.model.LibraryEntry;
import app.kuro.anime.data.model.WatchStatus;
import app.kuro.anime.data.repo.LibraryRepository;

public class MyListViewModel extends ViewModel {
    /** Tabs: the four watch statuses followed by Favorites. */
    static final WatchStatus[] TABS = {WatchStatus.WATCHING, WatchStatus.PLANNING, WatchStatus.COMPLETED,
            WatchStatus.PAUSED, null};

    private final LibraryRepository library = KuroApp.get().library();
    private final MutableLiveData<List<LibraryEntry>> entries = new MutableLiveData<>();
    private final MutableLiveData<int[]> counts = new MutableLiveData<>(new int[TABS.length]);
    private final Observer<Long> observer = v -> reload();
    int tab;
    String selectedKey;

    public MyListViewModel() { library.changes().observeForever(observer); }

    LiveData<List<LibraryEntry>> entries() { return entries; }

    LiveData<int[]> counts() { return counts; }

    void setTab(int t) {
        tab = t;
        reload();
    }

    void reload() {
        final int t = tab;
        AppExecutors.disk().execute(() -> {
            WatchStatus s = TABS[t];
            entries.postValue(s == null ? library.favorites() : library.byStatus(s));
            int[] c = new int[TABS.length];
            for (int i = 0; i < TABS.length; i++) c[i] = library.count(TABS[i]);
            counts.postValue(c);
        });
    }

    @Override
    protected void onCleared() { library.changes().removeObserver(observer); }
}
