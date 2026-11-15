package app.kuro.anime.data.repo;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.data.local.KuroDatabase;

/** Recent searches, stored only on this device. */
public final class SearchHistoryRepository {
    private static final int MAX = 20;
    private final KuroDatabase db;

    public SearchHistoryRepository(KuroDatabase db) { this.db = db; }

    public List<String> recent() {
        List<String> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT query FROM search_history ORDER BY searched_at DESC LIMIT " + MAX, null)) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    public void add(String query) {
        String q = query.trim();
        if (q.length() < 2) return;
        ContentValues v = new ContentValues();
        v.put("query", q);
        v.put("searched_at", System.currentTimeMillis());
        SQLiteDatabase w = db.getWritableDatabase();
        w.insertWithOnConflict("search_history", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        w.execSQL("DELETE FROM search_history WHERE query NOT IN (SELECT query FROM search_history"
                + " ORDER BY searched_at DESC LIMIT " + MAX + ")");
    }

    public void remove(String query) {
        db.getWritableDatabase().delete("search_history", "query = ?", new String[]{query});
    }

    public void clear() { db.getWritableDatabase().delete("search_history", null, null); }
}
