package app.kuro.anime.ui.search;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.KuroApp;
import app.kuro.anime.core.AppExecutors;
import app.kuro.anime.data.model.AnimeQuery;
import app.kuro.anime.ui.common.PagedAnimeList;

public class SearchViewModel extends ViewModel {
    static final String[] FORMATS = {null, "TV", "MOVIE", "OVA", "ONA", "SPECIAL"};

    final PagedAnimeList results = new PagedAnimeList();
    private final MutableLiveData<List<String>> history = new MutableLiveData<>(new ArrayList<>());
    String text = "";
    int formatIndex;
    /** Key of the title shown in the detail pane (expanded layouts). */
    String selectedKey;

    public SearchViewModel() { refreshHistory(); }

    LiveData<List<String>> history() { return history; }

    /** Runs the query; anything shorter than 2 characters clears the results. */
    void search(String raw) {
        text = raw == null ? "" : raw.trim();
        if (text.length() < 2) {
            results.clear();
            return;
        }
        AnimeQuery q = new AnimeQuery();
        q.search = text;
        q.sort = AnimeQuery.Sort.RELEVANCE;
        q.format = FORMATS[formatIndex];
        q.perPage = 24;
        results.submit(q, false);
    }

    void commitToHistory() {
        final String q = text;
        if (q.length() < 2) return;
        AppExecutors.disk().execute(() -> {
            KuroApp.get().searchHistory().add(q);
            history.postValue(KuroApp.get().searchHistory().recent());
        });
    }

    void removeHistory(String q) {
        AppExecutors.disk().execute(() -> {
            KuroApp.get().searchHistory().remove(q);
            history.postValue(KuroApp.get().searchHistory().recent());
        });
    }

    void clearHistory() {
        AppExecutors.disk().execute(() -> {
            KuroApp.get().searchHistory().clear();
            history.postValue(new ArrayList<>());
        });
    }

    void refreshHistory() {
        AppExecutors.disk().execute(() -> history.postValue(KuroApp.get().searchHistory().recent()));
    }
}
