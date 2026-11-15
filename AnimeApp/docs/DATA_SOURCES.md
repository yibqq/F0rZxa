# Data sources — evaluation and choices

Kuro uses **three keyless public APIs**, each for what it does best, behind one unified data
layer (`data/model`, `data/source/*`, `data/repo`). No API secret ships in the app.

## Comparison (checked November 2026)

| | AniList GraphQL | Kitsu (JSON:API) | Jikan v4 (unofficial MAL) | TMDB |
|---|---|---|---|---|
| Key / auth | None for public data | None | None | **API key required** |
| Rate limit | 90 req/min nominal, **currently 30/min** (seen in `X-RateLimit-Limit`) | Not published; we self-limit to 60/min | 3 req/s, 60 req/min | ~50 req/s per key |
| Catalog coverage | Excellent; trending, seasonal, airing, upcoming, score sorting, genre/year/season/format/status filters in one query language | Good, weaker filtering | Good (mirrors MAL) | Anime mixed into general TV; no anime-specific filters |
| Artwork | High-res covers (`extraLarge`), wide **banners**, a dominant colour per title | Posters & covers | MAL posters only | Excellent backdrops |
| Characters / staff / relations / recommendations | Yes, one request | Partial | Yes, separate requests | Cast only |
| Airing schedule | Exact `nextAiringEpisode` with timestamp | No | Broadcast day only | Air dates |
| **Episode titles & dates** | No (only partner "streaming episodes") | **Yes** | Yes (+ filler/recap flags) | Yes |
| **Real episode thumbnails** | Only partner stills via `streamingEpisodes`, incomplete | **Yes, per episode** | No | Yes, but needs key + TV-show mapping |
| Cross-IDs | `idMal` on every title | `mappings` table: AniList ↔ MAL ↔ Kitsu | MAL IDs only | Needs external matching |
| Terms | Free for non-commercial use; commercial use needs permission from AniList | Free API; attribution expected | Unofficial, free, best-effort; MAL content rights apply | Attribution + key terms |
| Stability observed | Stable; Cloudflare blocks generic scripting user agents (the app sends its own UA) | Stable | Frequently slow / unreachable from data-centre networks (it was unreachable from our build machine) | Stable |

## What each source is used for

1. **AniList — primary catalog.** Home sections (one aliased GraphQL request), Explore filters,
   search, details (synopsis, studios, relations, characters with Japanese VAs, staff,
   recommendations), next-episode countdowns, banners and cover colours. Adult titles are
   excluded in every query (`isAdult: false`) and rejected again in the mapper.
2. **Kitsu — episodes.** Titles, air dates and **real episode thumbnails**. The Kitsu ID is resolved
   through Kitsu's own `mappings` endpoint (AniList ID first, then MAL ID) — never by title. As an
   extra guard the premiere years must agree (±1 year).
3. **Jikan — fallback.** (a) Episode titles/dates/filler/recap flags when Kitsu has no titles for a
   page; (b) catalog fallback (top, airing, upcoming, seasonal, filtered search) when AniList itself
   is failing. Fallback results are flagged in the UI ("Showing results from a backup source").
4. **AniList `streamingEpisodes`** — last-resort thumbnails, used only when the title parses exactly
   as `Episode N - …` and that number is unique.

**Not used: TMDB.** It needs an API key, which must not be embedded in an APK. If you want TMDB
backdrops later, put a small proxy server in front of it and add an adapter.

## Identity, de-duplication and fallback rules

- Local identity = `al:<anilistId>`, or `mal:<malId>` for titles only seen through Jikan.
- Lists are de-duplicated by that key. When a MAL-only library entry later gains its AniList ID,
  the row and its episode progress are **migrated** to the canonical key (tested).
- Records from different sources are **never merged on title similarity**. Episodes are merged
  strictly by episode number within the requested range, and only empty fields are filled.
- Paging never mixes sources: only page 1 may fall back; later pages show "tap to retry".
- Order of preference: fresh cache → AniList → Jikan (only if AniList is failing, not when offline
  or "not found") → stale cache.

## Caching, limits and errors

- Every response body is cached in SQLite **after it parses successfully** (a bad response can't
  poison the cache). TTLs: home 30 min, browse 1 h, search 30 min, details 6 h, episodes 12 h, ID
  mappings 7 days. Offline, any previously loaded screen still works and is labelled as saved data.
- A per-host limiter keeps each source under its limit (AniList 28/min, Jikan 50/min with 400 ms
  spacing, Kitsu 60/min). HTTP 429 honours `Retry-After` for all callers. 5xx and network failures
  are retried with back-off (bounded to 3 attempts).
- Images use Glide's memory + disk cache, downsampled to the view size; "Data saver" switches to
  smaller covers.

## What the data cannot provide

- Episode thumbnails don't exist for every series: Kitsu coverage is good for popular shows and
  thin for older or obscure ones. Missing thumbnails show the episode number — never the cover.
- Episode titles may be English only (no Arabic titles exist in these sources).
- No source provides streaming links; playback is a separate integration (see
  `PLAYBACK_INTEGRATION.md`).
- Before a commercial release, confirm AniList's commercial-use terms and add any attribution
  each source requests (Settings → Data sources already lists all three).
