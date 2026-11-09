package com.mizan.gpa.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Semester {
    public String id = Json.newId();
    public String name = "";
    /** Teaching weeks; used to estimate the number of lectures from the weekly schedule. */
    public int weeks = 15;
    public final List<Course> courses = new ArrayList<>();

    public Course findCourse(String cid) {
        for (Course c : courses) if (c.id.equals(cid)) return c;
        return null;
    }

    public double totalCredits() {
        double t = 0;
        for (Course c : courses) t += c.credits;
        return t;
    }

    public boolean isCompleted() {
        if (courses.isEmpty()) return false;
        for (Course c : courses) if (!c.isCompleted()) return false;
        return true;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("weeks", weeks);
        JSONArray a = new JSONArray();
        for (Course c : courses) a.put(c.toJson());
        o.put("courses", a);
        return o;
    }

    public static Semester fromJson(JSONObject o) {
        Semester s = new Semester();
        s.id = Json.str(o, "id", Json.newId());
        s.name = Json.str(o, "name", "");
        s.weeks = Math.max(1, Json.integer(o, "weeks", 15));
        JSONArray a = Json.arr(o, "courses");
        for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) != null) s.courses.add(Course.fromJson(a.optJSONObject(i)));
        return s;
    }
}
