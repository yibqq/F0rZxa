package com.mizan.gpa.data;

import org.json.JSONException;
import org.json.JSONObject;

/** Attendance for one class meeting. {@code sessions} lets a long lab count as more than one lecture. */
public class AttendanceRecord {
    public static final String PRESENT = "present";
    public static final String ABSENT = "absent";
    public static final String LATE = "late";
    public static final String EXCUSED = "excused";
    public static final String[] STATUSES = {PRESENT, ABSENT, LATE, EXCUSED};

    public String id = Json.newId();
    /** ISO date, yyyy-MM-dd. */
    public String date;
    public String status = ABSENT;
    public int sessions = 1;
    public String note = "";

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("date", date);
        o.put("status", status);
        o.put("sessions", sessions);
        o.put("note", note);
        return o;
    }

    public static AttendanceRecord fromJson(JSONObject o) {
        AttendanceRecord r = new AttendanceRecord();
        r.id = Json.str(o, "id", Json.newId());
        r.date = Json.str(o, "date", "1970-01-01");
        r.status = Json.str(o, "status", ABSENT);
        r.sessions = Math.max(1, Json.integer(o, "sessions", 1));
        r.note = Json.str(o, "note", "");
        return r;
    }
}
