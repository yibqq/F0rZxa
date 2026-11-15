package app.kuro.anime.data.source.anilist;

/** GraphQL documents. Validated against the live AniList schema. Adult titles are always excluded. */
final class AniListQueries {
    private AniListQueries() {}

    static final String CARD = "fragment card on Media {"
            + " id idMal"
            + " title { romaji english native }"
            + " coverImage { extraLarge large color }"
            + " bannerImage format status episodes duration season seasonYear averageScore popularity genres"
            + " startDate { year month day }"
            + " nextAiringEpisode { episode airingAt }"
            + " studios(isMain: true) { nodes { name } }"
            + " }";

    private static final String MEDIA_ARGS = "type: ANIME, isAdult: false";

    static final String HOME = "query ($season: MediaSeason, $year: Int, $perPage: Int) {"
            + " trending: Page(perPage: $perPage) { media(" + MEDIA_ARGS + ", sort: [TRENDING_DESC, POPULARITY_DESC]) { ...card } }"
            + " seasonal: Page(perPage: $perPage) { media(" + MEDIA_ARGS + ", season: $season, seasonYear: $year, sort: [POPULARITY_DESC]) { ...card } }"
            + " airing: Page(perPage: $perPage) { media(" + MEDIA_ARGS + ", status: RELEASING, sort: [POPULARITY_DESC]) { ...card } }"
            + " top: Page(perPage: $perPage) { media(" + MEDIA_ARGS + ", sort: [SCORE_DESC]) { ...card } }"
            + " upcoming: Page(perPage: $perPage) { media(" + MEDIA_ARGS + ", status: NOT_YET_RELEASED, sort: [POPULARITY_DESC]) { ...card } }"
            + " popular: Page(perPage: $perPage) { media(" + MEDIA_ARGS + ", sort: [POPULARITY_DESC]) { ...card } }"
            + " } " + CARD;

    static final String BROWSE = "query ($page: Int, $perPage: Int, $sort: [MediaSort], $search: String,"
            + " $genre: String, $season: MediaSeason, $seasonYear: Int, $format: MediaFormat, $status: MediaStatus) {"
            + " Page(page: $page, perPage: $perPage) {"
            + " pageInfo { hasNextPage currentPage }"
            + " media(" + MEDIA_ARGS + ", sort: $sort, search: $search, genre: $genre, season: $season,"
            + " seasonYear: $seasonYear, format: $format, status: $status) { ...card }"
            + " } } " + CARD;

    static final String DETAILS = "query ($id: Int, $idMal: Int) {"
            + " Media(id: $id, idMal: $idMal, type: ANIME) {"
            + " ...card synonyms isAdult"
            + " description(asHtml: false)"
            + " source(version: 3) meanScore"
            + " endDate { year month day }"
            + " relations { edges { relationType(version: 2) node { ...card type } } }"
            + " characters(sort: [ROLE, RELEVANCE, ID], perPage: 18) { edges { role"
            + " node { id name { full native } image { large } }"
            + " voiceActors(language: JAPANESE, sort: [RELEVANCE, ID]) { id name { full } image { large } } } }"
            + " staff(sort: [RELEVANCE, ID], perPage: 12) { edges { role node { id name { full } image { large } } } }"
            + " recommendations(sort: [RATING_DESC, ID], perPage: 16) { nodes { mediaRecommendation { ...card } } }"
            + " streamingEpisodes { title thumbnail site }"
            + " } } " + CARD;
}
