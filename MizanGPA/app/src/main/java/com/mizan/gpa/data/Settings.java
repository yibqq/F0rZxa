package com.mizan.gpa.data;

import org.json.JSONException;
import org.json.JSONObject;

public class Settings {
    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public GradeScale scale = GradeScale.preset(GradeScale.PRESET_SA5);
    /** Round course percentages half-up to whole numbers before converting to letters. */
    public boolean roundPercent = false;

    /** Cumulative GPA and credit hours from earlier studies that are not entered as semesters. */
    public double priorGpa = 0;
    public double priorCredits = 0;
    public double targetGpa = 0;

    /** Default maximum absence percentage before deprivation. */
    public double absenceLimit = 25;
    /** True: deprived on reaching the limit (>=). False: only when exceeding it (>). */
    public boolean deprivedOnReach = true;
    /** Warn when this many absences (or fewer) remain. */
    public int warnRemaining = 2;
    public boolean countExcused = false;
    /** Number of late arrivals that count as one absence; 0 = lateness is not counted. */
    public int latesPerAbsence = 0;
    public boolean attendanceAlerts = true;

    public boolean remindersEnabled = true;
    public int reminderMinutes = 15;

    public boolean examReminders = true;
    /** Default exam reminders: days before (sent at {@link #examDayTime}) and minutes before the start. */
    public final java.util.List<Integer> examDaysBefore = new java.util.ArrayList<>(java.util.Arrays.asList(1));
    public final java.util.List<Integer> examMinutesBefore = new java.util.ArrayList<>(java.util.Arrays.asList(60));
    /** Time of day (minutes after midnight) for the "days before" exam reminders. */
    public int examDayTime = 20 * 60;
    /** When reminders were last delivered, so ones missed while the phone was off can be shown later. */
    public long lastReminderCheck = 0;

    public int themeMode = THEME_SYSTEM;
    public boolean dynamicColor = false;

    public String studentName = "";
    public String university = "";
    public String currentSemesterId;
    public int defaultWeeks = 15;
    public boolean onboarded = false;
    public boolean notificationsAsked = false;

    /** Font id (see Fonts): a bundled font, "system", or "file:<family>" for an imported font. */
    public String fontId = "tajawal";
    /** Text size multiplier applied on top of the system font size. */
    public float fontScale = 1f;
    /** Color palette id (see Themes). */
    public String colorTheme = "teal";
    /** Pure black backgrounds in dark mode. */
    public boolean pureBlack = false;
    /** Schedule tab layout: "list" or "grid". */
    public String scheduleView = "grid";

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("scale", scale.toJson());
        o.put("roundPercent", roundPercent);
        o.put("priorGpa", priorGpa);
        o.put("priorCredits", priorCredits);
        o.put("targetGpa", targetGpa);
        o.put("absenceLimit", absenceLimit);
        o.put("deprivedOnReach", deprivedOnReach);
        o.put("warnRemaining", warnRemaining);
        o.put("countExcused", countExcused);
        o.put("latesPerAbsence", latesPerAbsence);
        o.put("attendanceAlerts", attendanceAlerts);
        o.put("remindersEnabled", remindersEnabled);
        o.put("reminderMinutes", reminderMinutes);
        o.put("examReminders", examReminders);
        o.put("examDaysBefore", new org.json.JSONArray(examDaysBefore));
        o.put("examMinutesBefore", new org.json.JSONArray(examMinutesBefore));
        o.put("examDayTime", examDayTime);
        o.put("lastReminderCheck", lastReminderCheck);
        o.put("themeMode", themeMode);
        o.put("dynamicColor", dynamicColor);
        o.put("studentName", studentName);
        o.put("university", university);
        Json.put(o, "currentSemesterId", currentSemesterId);
        o.put("defaultWeeks", defaultWeeks);
        o.put("onboarded", onboarded);
        o.put("notificationsAsked", notificationsAsked);
        o.put("fontId", fontId);
        o.put("fontScale", (double) fontScale);
        o.put("colorTheme", colorTheme);
        o.put("pureBlack", pureBlack);
        o.put("scheduleView", scheduleView);
        return o;
    }

    public static Settings fromJson(JSONObject o) {
        Settings s = new Settings();
        if (o == null) return s;
        s.scale = GradeScale.fromJson(o.optJSONObject("scale"));
        s.roundPercent = Json.bool(o, "roundPercent", false);
        s.priorGpa = Math.max(0, Json.num(o, "priorGpa", 0));
        s.priorCredits = Math.max(0, Json.num(o, "priorCredits", 0));
        s.targetGpa = Math.max(0, Json.num(o, "targetGpa", 0));
        s.absenceLimit = clamp(Json.num(o, "absenceLimit", 25), 1, 100);
        s.deprivedOnReach = Json.bool(o, "deprivedOnReach", true);
        s.warnRemaining = Math.max(0, Json.integer(o, "warnRemaining", 2));
        s.countExcused = Json.bool(o, "countExcused", false);
        s.latesPerAbsence = Math.max(0, Json.integer(o, "latesPerAbsence", 0));
        s.attendanceAlerts = Json.bool(o, "attendanceAlerts", true);
        s.remindersEnabled = Json.bool(o, "remindersEnabled", true);
        s.reminderMinutes = Math.max(0, Json.integer(o, "reminderMinutes", 15));
        s.examReminders = Json.bool(o, "examReminders", true);
        if (o.has("examDaysBefore")) {
            s.examDaysBefore.clear();
            Exam.readInts(Json.arr(o, "examDaysBefore"), s.examDaysBefore);
        }
        if (o.has("examMinutesBefore")) {
            s.examMinutesBefore.clear();
            Exam.readInts(Json.arr(o, "examMinutesBefore"), s.examMinutesBefore);
        }
        s.examDayTime = Math.max(0, Math.min(24 * 60 - 1, Json.integer(o, "examDayTime", 20 * 60)));
        s.lastReminderCheck = (long) Json.num(o, "lastReminderCheck", 0);
        s.themeMode = Json.integer(o, "themeMode", THEME_SYSTEM);
        s.dynamicColor = Json.bool(o, "dynamicColor", false);
        s.studentName = Json.str(o, "studentName", "");
        s.university = Json.str(o, "university", "");
        s.currentSemesterId = Json.strOrNull(o, "currentSemesterId");
        s.defaultWeeks = Math.max(1, Json.integer(o, "defaultWeeks", 15));
        s.onboarded = Json.bool(o, "onboarded", false);
        s.notificationsAsked = Json.bool(o, "notificationsAsked", false);
        s.fontId = Json.str(o, "fontId", "tajawal");
        s.fontScale = (float) clamp(Json.num(o, "fontScale", 1), 0.8, 1.4);
        s.colorTheme = Json.str(o, "colorTheme", "teal");
        s.pureBlack = Json.bool(o, "pureBlack", false);
        s.scheduleView = Json.str(o, "scheduleView", "grid");
        return s;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
