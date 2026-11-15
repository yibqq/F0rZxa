package app.kuro.anime.data.repo;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.kuro.anime.data.local.KuroDatabase;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.LibraryEntry;
import app.kuro.anime.data.model.WatchStatus;

/**
 * Local lists, favourites and episode progress. No account or server involved.
 * Writers must call from a background thread; {@link #changes()} notifies observers.
 */
public final class LibraryRepository {

    private final KuroDatabase db;
    private final MutableLiveData<Long> changes = new MutableLiveData<>(0L);

    public LibraryRepository(KuroDatabase db) { this.db = db; }

    public LiveData<Long> changes() { return changes; }

    private void notifyChanged() { changes.postValue(System.nanoTime()); }

    @Nullable
    public LibraryEntry get(String key) {
        try (Cursor c = db.getReadableDatabase().rawQuery(SELECT + " WHERE l.anime_key = ?", new String[]{key})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    /** Finds an entry for this anime by its primary key or, for older records, by its MAL ID. */
    @Nullable
    public LibraryEntry find(Anime a) {
        LibraryEntry e = get(a.key());
        if (e == null && a.malId > 0) {
            try (Cursor c = db.getReadableDatabase().rawQuery(SELECT + " WHERE l.mal_id = ? LIMIT 1",
                    new String[]{String.valueOf(a.malId)})) {
                if (c.moveToFirst()) e = read(c);
            }
        }
        return e;
    }

    public List<LibraryEntry> byStatus(@Nullable WatchStatus status) {
        String where = status == null ? " WHERE l.status IS NOT NULL" : " WHERE l.status = ?";
        String[] args = status == null ? null : new String[]{status.name()};
        return query(SELECT + where + " ORDER BY l.updated_at DESC", args);
    }

    public List<LibraryEntry> favorites() {
        return query(SELECT + " WHERE l.favorite = 1 ORDER BY l.updated_at DESC", null);
    }

    public int count(@Nullable WatchStatus status) {
        String sql = status == null ? "SELECT COUNT(*) FROM library WHERE favorite = 1"
                : "SELECT COUNT(*) FROM library WHERE status = '" + status.name() + "'";
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public void setStatus(Anime a, @Nullable WatchStatus status) {
        SQLiteDatabase w = db.getWritableDatabase();
        w.beginTransaction();
        try {
            LibraryEntry existing = migrateIfNeeded(w, a);
            ContentValues v = metadata(a);
            v.put("status", status == null ? null : status.name());
            upsert(w, a.key(), v, existing);
            pruneOrphan(w, a.key());
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
        notifyChanged();
    }

    public void setFavorite(Anime a, boolean favorite) {
        SQLiteDatabase w = db.getWritableDatabase();
        w.beginTransaction();
        try {
            LibraryEntry existing = migrateIfNeeded(w, a);
            ContentValues v = metadata(a);
            v.put("favorite", favorite ? 1 : 0);
            upsert(w, a.key(), v, existing);
            pruneOrphan(w, a.key());
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
        notifyChanged();
    }

    public void remove(String key) {
        SQLiteDatabase w = db.getWritableDatabase();
        w.delete("library", "anime_key = ?", new String[]{key});
        w.delete("watched", "anime_key = ?", new String[]{key});
        notifyChanged();
    }

    public Set<Integer> watchedEpisodes(String key) {
        Set<Integer> out = new HashSet<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT episode FROM watched WHERE anime_key = ?", new String[]{key})) {
            while (c.moveToNext()) out.add(c.getInt(0));
        }
        return out;
    }

    /**
     * Marks episodes watched/unwatched. Watching something adds the title as "Watching" if it isn't
     * in the list yet. Returns true when every known episode is now watched (caller may offer completion).
     */
    public boolean setWatched(Anime a, int fromEpisode, int toEpisode, boolean watched) {
        SQLiteDatabase w = db.getWritableDatabase();
        boolean complete;
        w.beginTransaction();
        try {
            LibraryEntry existing = migrateIfNeeded(w, a);
            String key = a.key();
            long now = System.currentTimeMillis();
            for (int ep = fromEpisode; ep <= toEpisode; ep++) {
                if (watched) {
                    ContentValues v = new ContentValues();
                    v.put("anime_key", key);
                    v.put("episode", ep);
                    v.put("watched_at", now);
                    w.insertWithOnConflict("watched", null, v, SQLiteDatabase.CONFLICT_IGNORE);
                } else {
                    w.delete("watched", "anime_key = ? AND episode = ?", new String[]{key, String.valueOf(ep)});
                }
            }
            ContentValues v = metadata(a);
            if (watched && (existing == null || existing.status == null)) v.put("status", WatchStatus.WATCHING.name());
            upsert(w, key, v, existing);
            int count = countWatched(w, key);
            complete = watched && a.episodes > 0 && count >= a.episodes && !a.isAiring();
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
        notifyChanged();
        return complete;
    }

    public void clearAll() {
        SQLiteDatabase w = db.getWritableDatabase();
        w.delete("watched", null, null);
        w.delete("library", null, null);
        notifyChanged();
    }

    // --- internals -------------------------------------------------------------------------

    private static final String SELECT = "SELECT l.anime_key, l.anilist_id, l.mal_id, l.title_romaji, l.title_english,"
            + " l.title_native, l.cover_url, l.cover_color, l.format, l.airing_status, l.total_episodes, l.status,"
            + " l.favorite, l.added_at, l.updated_at,"
            + " (SELECT COUNT(*) FROM watched w WHERE w.anime_key = l.anime_key) AS watched_count"
            + " FROM library l";

    private List<LibraryEntry> query(String sql, @Nullable String[] args) {
        List<LibraryEntry> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    private static LibraryEntry read(Cursor c) {
        LibraryEntry e = new LibraryEntry();
        e.key = c.getString(0);
        e.anilistId = c.getInt(1);
        e.malId = c.getInt(2);
        e.titleRomaji = c.getString(3);
        e.titleEnglish = c.getString(4);
        e.titleNative = c.getString(5);
        e.coverUrl = c.getString(6);
        e.coverColor = c.getString(7);
        e.format = c.getString(8);
        e.airingStatus = c.getString(9);
        e.totalEpisodes = c.getInt(10);
        e.status = WatchStatus.parse(c.getString(11));
        e.favorite = c.getInt(12) == 1;
        e.addedAt = c.getLong(13);
        e.updatedAt = c.getLong(14);
        e.watchedCount = c.getInt(15);
        return e;
    }

    private static ContentValues metadata(Anime a) {
        ContentValues v = new ContentValues();
        v.put("anilist_id", a.anilistId);
        v.put("mal_id", a.malId);
        if (a.titleRomaji != null) v.put("title_romaji", a.titleRomaji);
        if (a.titleEnglish != null) v.put("title_english", a.titleEnglish);
        if (a.titleNative != null) v.put("title_native", a.titleNative);
        if (a.coverUrl != null) v.put("cover_url", a.coverUrl);
        if (a.coverColor != null) v.put("cover_color", a.coverColor);
        if (a.format != null) v.put("format", a.format);
        if (a.status != null) v.put("airing_status", a.status);
        if (a.episodes > 0) v.put("total_episodes", a.episodes);
        v.put("updated_at", System.currentTimeMillis());
        return v;
    }

    private static void upsert(SQLiteDatabase w, String key, ContentValues v, @Nullable LibraryEntry existing) {
        if (existing != null) {
            w.update("library", v, "anime_key = ?", new String[]{key});
        } else {
            v.put("anime_key", key);
            v.put("added_at", System.currentTimeMillis());
            w.insert("library", null, v);
        }
    }

    /** Removes a row that no longer has a status, favourite flag or progress. */
    private static void pruneOrphan(SQLiteDatabase w, String key) {
        w.execSQL("DELETE FROM library WHERE anime_key = ? AND status IS NULL AND favorite = 0"
                + " AND NOT EXISTS (SELECT 1 FROM watched WHERE anime_key = ?)", new Object[]{key, key});
    }

    private static int countWatched(SQLiteDatabase w, String key) {
        try (Cursor c = w.rawQuery("SELECT COUNT(*) FROM watched WHERE anime_key = ?", new String[]{key})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /**
     * If this title was saved earlier under its MAL-only key (e.g. from the fallback source) and we now
     * know its AniList ID, move the row and its progress to the canonical key so nothing is duplicated.
     */
    @Nullable
    private LibraryEntry migrateIfNeeded(SQLiteDatabase w, Anime a) {
        String key = a.key();
        LibraryEntry current = get(key);
        if (current != null || a.anilistId <= 0 || a.malId <= 0) return current;
        String legacy = "mal:" + a.malId;
        LibraryEntry old = get(legacy);
        if (old == null) return null;
        ContentValues k = new ContentValues();
        k.put("anime_key", key);
        k.put("anilist_id", a.anilistId);
        w.update("library", k, "anime_key = ?", new String[]{legacy});
        ContentValues wk = new ContentValues();
        wk.put("anime_key", key);
        w.updateWithOnConflict("watched", wk, "anime_key = ?", new String[]{legacy}, SQLiteDatabase.CONFLICT_IGNORE);
        w.delete("watched", "anime_key = ?", new String[]{legacy});
        return get(key);
    }
}
