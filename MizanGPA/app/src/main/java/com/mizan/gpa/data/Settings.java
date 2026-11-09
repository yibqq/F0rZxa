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

    public int themeMode = THEME_SYSTEM;
    public boolean dynamicColor = false;

    public String studentName = "";
    public String university = "";
    public String currentSemesterId;
    public int defaultWeeks = 15;
    public boolean onboarded = false;
    public boolean notificationsAsked = false;

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
        o.put("themeMode", themeMode);
        o.put("dynamicColor", dynamicColor);
        o.put("studentName", studentName);
        o.put("university", university);
        Json.put(o, "currentSemesterId", currentSemesterId);
        o.put("defaultWeeks", defaultWeeks);
        o.put("onboarded", onboarded);
        o.put("notificationsAsked", notificationsAsked);
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
        s.themeMode = Json.integer(o, "themeMode", THEME_SYSTEM);
        s.dynamicColor = Json.bool(o, "dynamicColor", false);
        s.studentName = Json.str(o, "studentName", "");
        s.university = Json.str(o, "university", "");
        s.currentSemesterId = Json.strOrNull(o, "currentSemesterId");
        s.defaultWeeks = Math.max(1, Json.integer(o, "defaultWeeks", 15));
        s.onboarded = Json.bool(o, "onboarded", false);
        s.notificationsAsked = Json.bool(o, "notificationsAsked", false);
        return s;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
