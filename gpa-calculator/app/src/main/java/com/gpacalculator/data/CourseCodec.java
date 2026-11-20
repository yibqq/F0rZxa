package com.gpacalculator.data;

import com.gpacalculator.model.Course;
import com.gpacalculator.model.Grade;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** JSON encoding for course lists. Malformed entries are skipped rather than failing the whole list. */
final class CourseCodec {
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String CREDITS = "credits";
    private static final String GRADE = "grade";

    private CourseCodec() {
    }

    static String encode(List<Course> courses) {
        JSONArray array = new JSONArray();
        try {
            for (Course c : courses) {
                JSONObject o = new JSONObject();
                o.put(ID, c.id);
                o.put(NAME, c.name);
                o.put(CREDITS, c.credits);
                o.put(GRADE, c.grade.code());
                array.put(o);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return array.toString();
    }

    static List<Course> decode(String json) {
        List<Course> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                Grade grade = Grade.fromCode(o.optString(GRADE, ""));
                int credits = o.optInt(CREDITS, -1);
                if (grade == null || !Course.isValidCredits(credits)) continue;
                out.add(new Course(o.optLong(ID, i + 1), o.optString(NAME, ""), credits, grade));
            }
        } catch (JSONException ignored) {
            // Corrupt payload: return whatever could be read.
        }
        return out;
    }
}
