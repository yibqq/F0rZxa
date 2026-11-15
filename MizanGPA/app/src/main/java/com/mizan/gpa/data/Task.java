package com.mizan.gpa.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An entry of "Reminders & Tasks": an exam, an assignment, a practical/lab assessment or a general
 * reminder. The date and the time are both optional; an entry without a date never has reminders.
 */
public class Task {
    public static final String EXAM = "exam";
    public static final String ASSIGNMENT = "assignment";
    public static final String PRACTICAL = "practical";
    public static final String GENERAL = "general";
    public static final String[] CATEGORIES = {EXAM, ASSIGNMENT, PRACTICAL, GENERAL};

    /** Exam kinds (only used by exams). */
    public static final String KIND_MIDTERM = "midterm";
    public static final String KIND_FINAL = "final";
    public static final String KIND_QUIZ = "quiz";
    public static final String KIND_OTHER = "other";
    public static final String[] KINDS = {KIND_MIDTERM, KIND_FINAL, KIND_QUIZ, KIND_OTHER};

    public static final int NO_TIME = -1;

    public String id = Json.newId();
    public String title = "";
    public String category = GENERAL;
    public String kind = KIND_OTHER;
    /** Linked course, or null for an entry that belongs to no course. */
    public String courseId;
    /** ISO date (yyyy-MM-dd) or null for an undated entry. */
    public String date;
    /** Minutes after midnight, or {@link #NO_TIME}. */
    public int time = NO_TIME;
    /** End time in minutes after midnight, or {@link #NO_TIME}. */
    public int endTime = NO_TIME;
    public String room = "";
    public String notes = "";
    public boolean done = false;
    /** False: use the reminder defaults from the settings. */
    public boolean customReminders = false;
    /** Days before the date to remind (at the settings' time of day). */
    public final List<Integer> daysBefore = new ArrayList<>();
    /** Minutes before the time to remind; 0 = at the time. Needs a time. */
    public final List<Integer> minutesBefore = new ArrayList<>();

    /** Exams and practical assessments happen at a time; assignments and reminders are due by a time. */
    public boolean isEvent() {
        return EXAM.equals(category) || PRACTICAL.equals(category);
    }

    public LocalDate localDate() {
        if (date == null) return null;
        try {
            return LocalDate.parse(date);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public boolean hasDate() {
        return localDate() != null;
    }

    public boolean hasTime() {
        return hasDate() && time >= 0 && time < 24 * 60;
    }

    public boolean hasEndTime() {
        return hasTime() && endTime > time && endTime < 24 * 60;
    }

    /** Start (or due) moment: date + time, or the start of the day for a date-only entry. */
    public LocalDateTime start() {
        LocalDate d = localDate();
        if (d == null) return null;
        return d.atStartOfDay().plusMinutes(hasTime() ? time : 0);
    }

    /**
     * When the entry is over: the end time, the start time, or — for a date-only entry — the end of
     * its day. After this an exam is finished and an open assignment is overdue.
     */
    public LocalDateTime end() {
        LocalDate d = localDate();
        if (d == null) return null;
        if (hasEndTime()) return d.atStartOfDay().plusMinutes(endTime);
        if (hasTime()) return d.atStartOfDay().plusMinutes(time);
        return d.plusDays(1).atStartOfDay().minusMinutes(1);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("category", category);
        o.put("kind", kind);
        Json.put(o, "courseId", courseId);
        Json.put(o, "date", date);
        o.put("time", time);
        o.put("endTime", endTime);
        o.put("room", room);
        o.put("notes", notes);
        o.put("done", done);
        o.put("customReminders", customReminders);
        o.put("daysBefore", new JSONArray(daysBefore));
        o.put("minutesBefore", new JSONArray(minutesBefore));
        return o;
    }

    public static Task fromJson(JSONObject o) {
        Task t = new Task();
        t.id = Json.str(o, "id", Json.newId());
        t.title = Json.str(o, "title", "");
        t.category = Json.str(o, "category", GENERAL);
        boolean known = false;
        for (String c : CATEGORIES) known |= c.equals(t.category);
        if (!known) t.category = GENERAL;
        t.kind = Json.str(o, "kind", KIND_OTHER);
        t.courseId = Json.strOrNull(o, "courseId");
        t.date = Json.strOrNull(o, "date");
        if (t.date != null && t.localDate() == null) t.date = null;
        t.time = Json.integer(o, "time", NO_TIME);
        if (t.time < 0 || t.time >= 24 * 60) t.time = NO_TIME;
        t.endTime = Json.integer(o, "endTime", NO_TIME);
        if (t.endTime < 0 || t.endTime >= 24 * 60) t.endTime = NO_TIME;
        t.room = Json.str(o, "room", "");
        t.notes = Json.str(o, "notes", "");
        t.done = Json.bool(o, "done", false);
        t.customReminders = Json.bool(o, "customReminders", false);
        readOffsets(Json.arr(o, "daysBefore"), t.daysBefore);
        readOffsets(Json.arr(o, "minutesBefore"), t.minutesBefore);
        return t;
    }

    /** Converts an exam saved by schema version 1 (inside a course) into a task. */
    static Task fromLegacyExam(JSONObject o, String courseId) {
        Task t = new Task();
        t.id = Json.str(o, "id", Json.newId());
        t.title = Json.str(o, "title", "");
        String type = Json.str(o, "type", "other");
        t.category = "practical".equals(type) ? PRACTICAL : EXAM;
        t.kind = "midterm".equals(type) || "final".equals(type) || "quiz".equals(type) ? type : KIND_OTHER;
        t.courseId = courseId;
        t.date = Json.strOrNull(o, "date");
        if (t.date != null && t.localDate() == null) t.date = null;
        int start = Json.integer(o, "start", 9 * 60);
        t.time = start >= 0 && start < 24 * 60 ? start : NO_TIME;
        int end = Json.integer(o, "end", 0);
        t.endTime = end > start && end < 24 * 60 ? end : NO_TIME;
        t.room = Json.str(o, "room", "");
        t.notes = Json.str(o, "note", "");
        t.customReminders = Json.bool(o, "customReminders", false);
        readOffsets(Json.arr(o, "daysBefore"), t.daysBefore);
        readOffsets(Json.arr(o, "minutesBefore"), t.minutesBefore);
        return t;
    }

    static void readOffsets(JSONArray a, List<Integer> into) {
        for (int i = 0; i < a.length(); i++) {
            int v = a.optInt(i, -1);
            if (v >= 0 && !into.contains(v)) into.add(v);
        }
    }
}
