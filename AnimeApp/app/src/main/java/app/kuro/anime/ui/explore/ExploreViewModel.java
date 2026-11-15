package app.kuro.anime.ui.explore;

import androidx.lifecycle.ViewModel;

import app.kuro.anime.data.model.AnimeQuery;
import app.kuro.anime.ui.common.PagedAnimeList;

public class ExploreViewModel extends ViewModel {
    final PagedAnimeList list = new PagedAnimeList();
    AnimeQuery query = new AnimeQuery();

    public ExploreViewModel() {
        query.perPage = 30;
        list.submit(query, false);
    }

    void apply(AnimeQuery q) {
        query = q;
        list.submit(q, false);
    }
}
