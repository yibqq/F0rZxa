package app.kuro.anime.data.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

/** Persistent cache of raw API responses, so previously loaded screens work offline. */
public final class ResponseCache {

    public static final class Entry {
        public final String body;
        public final long fetchedAt;

        Entry(String body, long fetchedAt) {
            this.body = body;
            this.fetchedAt = fetchedAt;
        }

        public boolean isFresh(long ttlMs) { return System.currentTimeMillis() - fetchedAt < ttlMs; }
    }

    private static final int MAX_ROWS = 600;
    private final KuroDatabase db;
    private int writesSincePrune;

    public ResponseCache(KuroDatabase db) { this.db = db; }

    @Nullable
    public Entry get(String key) {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT body, fetched_at FROM http_cache WHERE cache_key = ?", new String[]{key})) {
            if (c.moveToFirst()) return new Entry(c.getString(0), c.getLong(1));
        }
        return null;
    }

    public void put(String key, String body) {
        ContentValues v = new ContentValues();
        v.put("cache_key", key);
        v.put("body", body);
        v.put("fetched_at", System.currentTimeMillis());
        db.getWritableDatabase().insertWithOnConflict("http_cache", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        if (++writesSincePrune >= 50) {
            writesSincePrune = 0;
            prune();
        }
    }

    public void remove(String key) {
        db.getWritableDatabase().delete("http_cache", "cache_key = ?", new String[]{key});
    }

    /** Keeps the newest rows; old catalog pages are cheap to re-download. */
    public void prune() {
        db.getWritableDatabase().execSQL("DELETE FROM http_cache WHERE cache_key NOT IN " +
                "(SELECT cache_key FROM http_cache ORDER BY fetched_at DESC LIMIT " + MAX_ROWS + ")");
    }

    public void clear() { db.getWritableDatabase().delete("http_cache", null, null); }

    public long sizeBytes() {
        return DatabaseUtils.longForQuery(db.getReadableDatabase(),
                "SELECT COALESCE(SUM(LENGTH(body)), 0) FROM http_cache", null);
    }
}
