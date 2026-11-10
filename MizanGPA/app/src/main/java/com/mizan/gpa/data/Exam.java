package com.mizan.gpa.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A scheduled exam of a course, with optional reminders of its own. */
public class Exam {
    public static final String MIDTERM = "midterm";
    public static final String FINAL = "final";
    public static final String QUIZ = "quiz";
    public static final String PRACTICAL = "practical";
    public static final String OTHER = "other";
    public static final String[] TYPES = {MIDTERM, FINAL, QUIZ, PRACTICAL, OTHER};

    public String id = Json.newId();
    public String title = "";
    public String type = MIDTERM;
    /** ISO date, yyyy-MM-dd. */
    public String date;
    /** Minutes after midnight. */
    public int start = 9 * 60;
    /** End time in minutes after midnight; not after {@link #start} when unknown. */
    public int end = 0;
    public String room = "";
    public String note = "";
    /** False: use the reminder defaults from the settings. */
    public boolean customReminders = false;
    /** Days before the exam to remind (at the settings' time of day). */
    public final List<Integer> daysBefore = new ArrayList<>();
    /** Minutes before the exam start to remind; 0 = at the start time. */
    public final List<Integer> minutesBefore = new ArrayList<>();

    public boolean hasEnd() {
        return end > start;
    }

    /** Start as a local date-time, or null if the stored date is invalid. */
    public LocalDateTime startDateTime() {
        try {
            return LocalDate.parse(date).atStartOfDay().plusMinutes(start);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** End (or start, when the end is unknown) as a local date-time. */
    public LocalDateTime endDateTime() {
        try {
            return LocalDate.parse(date).atStartOfDay().plusMinutes(hasEnd() ? end : start);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("type", type);
        o.put("date", date);
        o.put("start", start);
        o.put("end", end);
        o.put("room", room);
        o.put("note", note);
        o.put("customReminders", customReminders);
        o.put("daysBefore", new JSONArray(daysBefore));
        o.put("minutesBefore", new JSONArray(minutesBefore));
        return o;
    }

    public static Exam fromJson(JSONObject o) {
        Exam e = new Exam();
        e.id = Json.str(o, "id", Json.newId());
        e.title = Json.str(o, "title", "");
        e.type = Json.str(o, "type", OTHER);
        e.date = Json.str(o, "date", "1970-01-01");
        e.start = Math.max(0, Math.min(24 * 60 - 1, Json.integer(o, "start", 9 * 60)));
        e.end = Json.integer(o, "end", 0);
        e.room = Json.str(o, "room", "");
        e.note = Json.str(o, "note", "");
        e.customReminders = Json.bool(o, "customReminders", false);
        readInts(Json.arr(o, "daysBefore"), e.daysBefore);
        readInts(Json.arr(o, "minutesBefore"), e.minutesBefore);
        return e;
    }

    static void readInts(JSONArray a, List<Integer> into) {
        for (int i = 0; i < a.length(); i++) {
            int v = a.optInt(i, -1);
            if (v >= 0 && !into.contains(v)) into.add(v);
        }
    }
}
