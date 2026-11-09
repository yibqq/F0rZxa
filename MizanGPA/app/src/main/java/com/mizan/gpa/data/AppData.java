package com.mizan.gpa.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Everything the app stores, serialized to a single JSON document. */
public class AppData {
    public static final int VERSION = 1;
    public static final String FORMAT = "mizan-gpa";

    public Settings settings = new Settings();
    public final List<Semester> semesters = new ArrayList<>();

    public Semester findSemester(String id) {
        if (id == null) return null;
        for (Semester s : semesters) if (s.id.equals(id)) return s;
        return null;
    }

    /** The semester the user marked as current, or the latest one. */
    public Semester currentSemester() {
        Semester s = findSemester(settings.currentSemesterId);
        if (s != null) return s;
        return semesters.isEmpty() ? null : semesters.get(semesters.size() - 1);
    }

    public Semester semesterOf(String courseId) {
        for (Semester s : semesters) if (s.findCourse(courseId) != null) return s;
        return null;
    }

    public Course findCourse(String courseId) {
        for (Semester s : semesters) {
            Course c = s.findCourse(courseId);
            if (c != null) return c;
        }
        return null;
    }

    public List<Course> allCourses() {
        List<Course> out = new ArrayList<>();
        for (Semester s : semesters) out.addAll(s.courses);
        return out;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("format", FORMAT);
        o.put("version", VERSION);
        o.put("settings", settings.toJson());
        JSONArray a = new JSONArray();
        for (Semester s : semesters) a.put(s.toJson());
        o.put("semesters", a);
        return o;
    }

    public static AppData fromJson(JSONObject o) throws JSONException {
        if (o == null) throw new JSONException("Empty document");
        if (!o.has("semesters") && !o.has("settings")) throw new JSONException("Not a Mizan data file");
        AppData d = new AppData();
        d.settings = Settings.fromJson(o.optJSONObject("settings"));
        JSONArray a = Json.arr(o, "semesters");
        for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) != null) d.semesters.add(Semester.fromJson(a.optJSONObject(i)));
        return d;
    }

    public static AppData parse(String json) throws JSONException {
        return fromJson(new JSONObject(json));
    }

    public String serialize(int indent) throws JSONException {
        return indent > 0 ? toJson().toString(indent) : toJson().toString();
    }
}
