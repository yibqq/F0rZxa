package app.kuro.anime.ui.browse;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import app.kuro.anime.data.model.AnimeQuery;
import app.kuro.anime.ui.common.Navigator;
import app.kuro.anime.ui.common.PagedAnimeList;

/** Full, paginated list behind a home row's "See all" (or a TV genre tile). */
public class BrowseViewModel extends ViewModel {
    public final PagedAnimeList list = new PagedAnimeList();
    public final String title;

    public BrowseViewModel(SavedStateHandle state) {
        title = state.get(Navigator.EXTRA_TITLE);
        AnimeQuery q = state.get(Navigator.EXTRA_QUERY);
        if (q == null) q = new AnimeQuery();
        list.submit(q, false);
    }
}
