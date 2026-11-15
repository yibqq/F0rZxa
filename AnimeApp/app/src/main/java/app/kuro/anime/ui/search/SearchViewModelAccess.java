package app.kuro.anime.ui.search;

import app.kuro.anime.ui.common.PagedAnimeList;

/** Public facade over {@link SearchViewModel} for the TV search screen. */
public class SearchViewModelAccess extends SearchViewModel {
    public PagedAnimeList results() { return results; }

    public String text() { return text; }

    public void search(String q) { super.search(q); }

    public void commit() { commitToHistory(); }
}
