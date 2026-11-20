package com.gpacalculator.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.gpacalculator.model.Course;
import com.gpacalculator.model.GradeScale;
import com.gpacalculator.model.HistoryRecord;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** SQLite storage for saved calculations. All methods must be called off the main thread. */
final class HistoryDatabase extends SQLiteOpenHelper {
    private static final String NAME = "history.db";
    private static final int VERSION = 1;

    private static final String TABLE = "records";
    private static final String C_ID = "_id";
    private static final String C_CREATED = "created_at";
    private static final String C_SCALE = "scale";
    private static final String C_SEM_GPA = "semester_gpa";
    private static final String C_SEM_CREDITS = "semester_credits";
    private static final String C_PREV_GPA = "previous_gpa";
    private static final String C_PREV_CREDITS = "previous_credits";
    private static final String C_CUM_GPA = "cumulative_gpa";
    private static final String C_COURSES = "courses";

    HistoryDatabase(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + C_CREATED + " INTEGER NOT NULL, "
                + C_SCALE + " TEXT NOT NULL, "
                + C_SEM_GPA + " TEXT NOT NULL, "
                + C_SEM_CREDITS + " INTEGER NOT NULL, "
                + C_PREV_GPA + " TEXT, "
                + C_PREV_CREDITS + " INTEGER, "
                + C_CUM_GPA + " TEXT, "
                + C_COURSES + " TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_records_created ON " + TABLE + " (" + C_CREATED + " DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the only schema so far. Future migrations go here; never drop user data.
    }

    List<HistoryRecord> queryAll() {
        List<HistoryRecord> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE, null, null, null, null, null,
                C_CREATED + " DESC, " + C_ID + " DESC")) {
            while (c.moveToNext()) {
                HistoryRecord r = read(c);
                if (r != null) out.add(r);
            }
        }
        return out;
    }

    HistoryRecord query(long id) {
        try (Cursor c = getReadableDatabase().query(TABLE, null, C_ID + " = ?",
                new String[]{Long.toString(id)}, null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    /** Inserts the record, reusing its id when non-zero (used to undo a delete). Returns the row id. */
    long insert(HistoryRecord r) {
        ContentValues v = new ContentValues();
        if (r.id > 0) v.put(C_ID, r.id);
        v.put(C_CREATED, r.createdAt);
        v.put(C_SCALE, r.scale.id());
        v.put(C_SEM_GPA, r.semesterGpa.toPlainString());
        v.put(C_SEM_CREDITS, r.semesterCredits);
        if (r.hasCumulative()) {
            v.put(C_PREV_GPA, r.previousGpa.toPlainString());
            v.put(C_PREV_CREDITS, r.previousCredits);
            v.put(C_CUM_GPA, r.cumulativeGpa.toPlainString());
        } else {
            v.putNull(C_PREV_GPA);
            v.putNull(C_PREV_CREDITS);
            v.putNull(C_CUM_GPA);
        }
        v.put(C_COURSES, CourseCodec.encode(r.courses));
        return getWritableDatabase().insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void delete(long id) {
        getWritableDatabase().delete(TABLE, C_ID + " = ?", new String[]{Long.toString(id)});
    }

    private static HistoryRecord read(Cursor c) {
        try {
            long id = c.getLong(c.getColumnIndexOrThrow(C_ID));
            long created = c.getLong(c.getColumnIndexOrThrow(C_CREATED));
            GradeScale scale = GradeScale.fromId(c.getString(c.getColumnIndexOrThrow(C_SCALE)));
            BigDecimal semGpa = new BigDecimal(c.getString(c.getColumnIndexOrThrow(C_SEM_GPA)));
            int semCredits = c.getInt(c.getColumnIndexOrThrow(C_SEM_CREDITS));
            List<Course> courses = CourseCodec.decode(c.getString(c.getColumnIndexOrThrow(C_COURSES)));

            int prevGpaIdx = c.getColumnIndexOrThrow(C_PREV_GPA);
            int prevCreditsIdx = c.getColumnIndexOrThrow(C_PREV_CREDITS);
            int cumIdx = c.getColumnIndexOrThrow(C_CUM_GPA);
            BigDecimal prevGpa = c.isNull(prevGpaIdx) ? null : new BigDecimal(c.getString(prevGpaIdx));
            Integer prevCredits = c.isNull(prevCreditsIdx) ? null : c.getInt(prevCreditsIdx);
            BigDecimal cum = c.isNull(cumIdx) ? null : new BigDecimal(c.getString(cumIdx));

            return new HistoryRecord(id, created, scale, courses, semGpa, semCredits, prevGpa, prevCredits, cum);
        } catch (RuntimeException e) {
            // A single unreadable row must not hide the rest of the history.
            return null;
        }
    }
}
