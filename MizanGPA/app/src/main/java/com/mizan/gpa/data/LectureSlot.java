package com.mizan.gpa.data;

import org.json.JSONException;
import org.json.JSONObject;

/** A weekly class meeting. {@code day} uses java.util.Calendar constants (SUNDAY = 1 ... SATURDAY = 7). */
public class LectureSlot {
    public static final String LECTURE = "lecture";
    public static final String LAB = "lab";
    public static final String TUTORIAL = "tutorial";
    public static final String[] KINDS = {LECTURE, LAB, TUTORIAL};

    public String id = Json.newId();
    public int day = 1;
    /** Minutes after midnight. */
    public int start = 8 * 60;
    public int end = 9 * 60 + 30;
    public String room = "";
    public String kind = LECTURE;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("day", day);
        o.put("start", start);
        o.put("end", end);
        o.put("room", room);
        o.put("kind", kind);
        return o;
    }

    public static LectureSlot fromJson(JSONObject o) {
        LectureSlot s = new LectureSlot();
        s.id = Json.str(o, "id", Json.newId());
        s.day = Math.max(1, Math.min(7, Json.integer(o, "day", 1)));
        s.start = Json.integer(o, "start", 8 * 60);
        s.end = Json.integer(o, "end", s.start + 90);
        s.room = Json.str(o, "room", "");
        s.kind = Json.str(o, "kind", LECTURE);
        return s;
    }
}
