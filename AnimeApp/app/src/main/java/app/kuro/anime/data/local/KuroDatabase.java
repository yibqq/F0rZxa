package app.kuro.anime.data.local;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Single on-device database. Everything personal (lists, progress, history) stays local. */
public final class KuroDatabase extends SQLiteOpenHelper {

    private static final String NAME = "kuro.db";
    private static final int VERSION = 1;

    public KuroDatabase(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE http_cache (" +
                "cache_key TEXT PRIMARY KEY NOT NULL," +
                "body TEXT NOT NULL," +
                "fetched_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE library (" +
                "anime_key TEXT PRIMARY KEY NOT NULL," +
                "anilist_id INTEGER NOT NULL DEFAULT 0," +
                "mal_id INTEGER NOT NULL DEFAULT 0," +
                "title_romaji TEXT, title_english TEXT, title_native TEXT," +
                "cover_url TEXT, cover_color TEXT, format TEXT, airing_status TEXT," +
                "total_episodes INTEGER NOT NULL DEFAULT 0," +
                "status TEXT," +
                "favorite INTEGER NOT NULL DEFAULT 0," +
                "added_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_library_mal ON library(mal_id)");
        db.execSQL("CREATE INDEX idx_library_status ON library(status, updated_at)");
        db.execSQL("CREATE TABLE watched (" +
                "anime_key TEXT NOT NULL," +
                "episode INTEGER NOT NULL," +
                "watched_at INTEGER NOT NULL," +
                "PRIMARY KEY (anime_key, episode))");
        db.execSQL("CREATE TABLE search_history (" +
                "query TEXT PRIMARY KEY NOT NULL COLLATE NOCASE," +
                "searched_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first schema. Future migrations go here; never drop user tables.
    }
}
