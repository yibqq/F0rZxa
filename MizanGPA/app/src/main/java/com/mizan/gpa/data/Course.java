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
    /** Course Reference Number from the university's registration system (optional, free format). */
    public String crn = "";
    /** Absences the student knows about but not their dates (e.g. before using the app). */
    public int undatedAbsences = 0;

    public final List<Assessment> assessments = new ArrayList<>();
    public final List<AttendanceRecord> attendance = new ArrayList<>();
    public final List<LectureSlot> lectures = new ArrayList<>();
    /** Exams stored inside the course by schema version 1; moved to AppData.tasks when loading. */
    final List<JSONObject> legacyExams = new ArrayList<>();

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

    public enum RecordResult { ADDED, UPDATED, DUPLICATE }

    /** Outcome of {@link #recordAttendance}; enough to undo it. */
    public static final class Recorded {
        public AttendanceRecord record;
        public RecordResult result;
        /** Status before an update. */
        public String previousStatus;
    }

    /**
     * Records attendance for a calendar date. There is at most one record per course per date: an
     * absence that is already recorded is not added again, and a different status replaces the
     * day's existing record (a double lecture is one record with two sessions).
     */
    public Recorded recordAttendance(String date, String status) {
        Recorded out = new Recorded();
        AttendanceRecord sameDay = null;
        for (AttendanceRecord r : attendance) {
            if (!date.equals(r.date)) continue;
            if (sameDay == null || AttendanceRecord.ABSENT.equals(r.status)) sameDay = r;
        }
        if (sameDay == null) {
            AttendanceRecord r = new AttendanceRecord();
            r.date = date;
            r.status = status;
            attendance.add(r);
            out.record = r;
            out.result = RecordResult.ADDED;
        } else if (sameDay.status.equals(status)) {
            out.record = sameDay;
            out.result = RecordResult.DUPLICATE;
        } else {
            out.previousStatus = sameDay.status;
            sameDay.status = status;
            out.record = sameDay;
            out.result = RecordResult.UPDATED;
        }
        return out;
    }

    /** Reverts a {@link #recordAttendance} call. */
    public void undo(Recorded r) {
        if (r.result == RecordResult.ADDED) attendance.remove(r.record);
        else if (r.result == RecordResult.UPDATED) r.record.status = r.previousStatus;
    }

    /** Another record (not {@code except}) on the same date, if any. */
    public AttendanceRecord recordOn(String date, AttendanceRecord except) {
        for (AttendanceRecord r : attendance) if (r != except && date.equals(r.date)) return r;
        return null;
    }

    /**
     * Enforces "one absence per course per date": duplicate absences on the same date are merged
     * into one (keeping the larger session count and the first note). Returns how many were merged.
     */
    public int collapseDuplicateAbsences() {
        int merged = 0;
        java.util.Map<String, AttendanceRecord> firstByDate = new java.util.HashMap<>();
        java.util.Iterator<AttendanceRecord> it = attendance.iterator();
        while (it.hasNext()) {
            AttendanceRecord r = it.next();
            if (!AttendanceRecord.ABSENT.equals(r.status) || r.date == null) continue;
            AttendanceRecord first = firstByDate.get(r.date);
            if (first == null) {
                firstByDate.put(r.date, r);
                continue;
            }
            first.sessions = Math.max(first.sessions, r.sessions);
            if ((first.note == null || first.note.isEmpty()) && r.note != null) first.note = r.note;
            it.remove();
            merged++;
        }
        return merged;
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
        o.put("crn", crn);
        o.put("undatedAbsences", undatedAbsences);
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
        c.crn = Json.str(o, "crn", "").trim();
        c.undatedAbsences = Math.max(0, Json.integer(o, "undatedAbsences", 0));
        JSONArray e = Json.arr(o, "exams");
        for (int i = 0; i < e.length(); i++) if (e.optJSONObject(i) != null) c.legacyExams.add(e.optJSONObject(i));
        c.collapseDuplicateAbsences();
        return c;
    }
}
