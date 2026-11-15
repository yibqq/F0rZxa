package app.kuro.anime.data.model;

import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.Objects;

/** Source-independent catalog query (filters, sort, text search). */
public final class AnimeQuery implements Serializable {

    public enum Sort { POPULARITY, TRENDING, SCORE, NEWEST, TITLE, RELEVANCE }

    public Sort sort = Sort.POPULARITY;
    @Nullable public String search;
    @Nullable public String genre;
    public int year;              // 0 = any
    @Nullable public String season;
    @Nullable public String format;
    @Nullable public String status;
    public int perPage = 30;

    public AnimeQuery copy() {
        AnimeQuery q = new AnimeQuery();
        q.sort = sort;
        q.search = search;
        q.genre = genre;
        q.year = year;
        q.season = season;
        q.format = format;
        q.status = status;
        q.perPage = perPage;
        return q;
    }

    public boolean hasFilters() {
        return genre != null || year > 0 || season != null || format != null || status != null;
    }

    public String cacheKey(int page) {
        return "q|" + sort + "|" + (search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT)) + "|" + genre + "|" + year
                + "|" + season + "|" + format + "|" + status + "|" + perPage + "|p" + page;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnimeQuery)) return false;
        AnimeQuery q = (AnimeQuery) o;
        return year == q.year && perPage == q.perPage && sort == q.sort && Objects.equals(search, q.search)
                && Objects.equals(genre, q.genre) && Objects.equals(season, q.season)
                && Objects.equals(format, q.format) && Objects.equals(status, q.status);
    }

    @Override
    public int hashCode() { return Objects.hash(sort, search, genre, year, season, format, status, perPage); }
}
