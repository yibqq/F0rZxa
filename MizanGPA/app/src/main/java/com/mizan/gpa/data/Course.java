package com.mizan.gpa.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Course {
    public String id = Json.newId();
    public String name = "";
    public String code = "";
    public String instructor = "";
    public double credits = 3;
    /** Official final grade letter, or null while the course is in progress. */
    public String finalGrade;
    /** Grade the student expects (manual), or null to predict it from the recorded marks. */
    public String expectedGrade;
    /** False for pass/fail courses or attempts that the university does not count in the GPA. */
    public boolean countInGpa = true;
    public int color;
    /** Planned number of class meetings in the semester; 0 = derive from the weekly schedule. */
    public int totalSessions = 0;
    /** Absence limit in percent for this course; negative = use the default from settings. */
    public double absenceLimit = -1;
    /** Total marks of the course (normally 100). */
    public double totalMarks = 100;

    public final List<Assessment> assessments = new ArrayList<>();
    public final List<AttendanceRecord> attendance = new ArrayList<>();
    public final List<LectureSlot> lectures = new ArrayList<>();

    public boolean isCompleted() {
        return finalGrade != null;
    }

    public String displayName() {
        if (name != null && !name.trim().isEmpty()) return name.trim();
        return code == null ? "" : code.trim();
    }

    public Assessment findAssessment(String aid) {
        for (Assessment a : assessments) if (a.id.equals(aid)) return a;
        return null;
    }

    public AttendanceRecord findRecord(String rid) {
        for (AttendanceRecord r : attendance) if (r.id.equals(rid)) return r;
        return null;
    }

    public LectureSlot findSlot(String sid) {
        for (LectureSlot s : lectures) if (s.id.equals(sid)) return s;
        return null;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("code", code);
        o.put("instructor", instructor);
        o.put("credits", credits);
        Json.put(o, "finalGrade", finalGrade);
        Json.put(o, "expectedGrade", expectedGrade);
        o.put("countInGpa", countInGpa);
        o.put("color", color);
        o.put("totalSessions", totalSessions);
        o.put("absenceLimit", absenceLimit);
        o.put("totalMarks", totalMarks);
        JSONArray a = new JSONArray();
        for (Assessment x : assessments) a.put(x.toJson());
        o.put("assessments", a);
        JSONArray r = new JSONArray();
        for (AttendanceRecord x : attendance) r.put(x.toJson());
        o.put("attendance", r);
        JSONArray l = new JSONArray();
        for (LectureSlot x : lectures) l.put(x.toJson());
        o.put("lectures", l);
        return o;
    }

    public static Course fromJson(JSONObject o) {
        Course c = new Course();
        c.id = Json.str(o, "id", Json.newId());
        c.name = Json.str(o, "name", "");
        c.code = Json.str(o, "code", "");
        c.instructor = Json.str(o, "instructor", "");
        c.credits = Math.max(0, Json.num(o, "credits", 3));
        c.finalGrade = Json.strOrNull(o, "finalGrade");
        c.expectedGrade = Json.strOrNull(o, "expectedGrade");
        c.countInGpa = Json.bool(o, "countInGpa", true);
        c.color = Json.integer(o, "color", 0);
        c.totalSessions = Math.max(0, Json.integer(o, "totalSessions", 0));
        c.absenceLimit = Json.num(o, "absenceLimit", -1);
        c.totalMarks = Json.num(o, "totalMarks", 100);
        if (c.totalMarks <= 0) c.totalMarks = 100;
        JSONArray a = Json.arr(o, "assessments");
        for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) != null) c.assessments.add(Assessment.fromJson(a.optJSONObject(i)));
        JSONArray r = Json.arr(o, "attendance");
        for (int i = 0; i < r.length(); i++) if (r.optJSONObject(i) != null) c.attendance.add(AttendanceRecord.fromJson(r.optJSONObject(i)));
        JSONArray l = Json.arr(o, "lectures");
        for (int i = 0; i < l.length(); i++) if (l.optJSONObject(i) != null) c.lectures.add(LectureSlot.fromJson(l.optJSONObject(i)));
        return c;
    }
}
