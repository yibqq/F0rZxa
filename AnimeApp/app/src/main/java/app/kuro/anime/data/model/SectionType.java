package app.kuro.anime.data.model;

/** Home sections. Each maps to a reusable browse query so "See all" shows the same list. */
public enum SectionType {
    TRENDING, SEASONAL, AIRING, TOP_RATED, UPCOMING, POPULAR;

    public AnimeQuery toQuery(Season.Current now) {
        AnimeQuery q = new AnimeQuery();
        switch (this) {
            case TRENDING: q.sort = AnimeQuery.Sort.TRENDING; break;
            case SEASONAL:
                q.sort = AnimeQuery.Sort.POPULARITY;
                q.season = now.season;
                q.year = now.year;
                break;
            case AIRING:
                q.sort = AnimeQuery.Sort.POPULARITY;
                q.status = "RELEASING";
                break;
            case TOP_RATED: q.sort = AnimeQuery.Sort.SCORE; break;
            case UPCOMING:
                q.sort = AnimeQuery.Sort.POPULARITY;
                q.status = "NOT_YET_RELEASED";
                break;
            case POPULAR: q.sort = AnimeQuery.Sort.POPULARITY; break;
        }
        return q;
    }
}
