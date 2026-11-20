package com.gpacalculator.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.gpacalculator.model.Course;
import com.gpacalculator.model.GradeScale;

import java.util.ArrayList;
import java.util.List;

/** Persists the in-progress calculator so nothing is lost if the app is closed. */
public final class DraftStore {
    private static final String PREFS = "calculator_draft";
    private static final String K_SCALE = "scale";
    private static final String K_COURSES = "courses";
    private static final String K_CUMULATIVE = "cumulative";
    private static final String K_PREV_GPA = "previous_gpa";
    private static final String K_PREV_CREDITS = "previous_credits";

    public static final class Draft {
        public GradeScale scale = GradeScale.FOUR;
        public List<Course> courses = new ArrayList<>();
        public boolean cumulative;
        public String previousGpa = "";
        public String previousCredits = "";
    }

    private final SharedPreferences prefs;

    public DraftStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Draft load() {
        Draft d = new Draft();
        d.scale = GradeScale.fromId(prefs.getString(K_SCALE, GradeScale.FOUR.id()));
        d.courses = CourseCodec.decode(prefs.getString(K_COURSES, ""));
        d.cumulative = prefs.getBoolean(K_CUMULATIVE, false);
        d.previousGpa = prefs.getString(K_PREV_GPA, "");
        d.previousCredits = prefs.getString(K_PREV_CREDITS, "");
        return d;
    }

    public void save(Draft d) {
        prefs.edit()
                .putString(K_SCALE, d.scale.id())
                .putString(K_COURSES, CourseCodec.encode(d.courses))
                .putBoolean(K_CUMULATIVE, d.cumulative)
                .putString(K_PREV_GPA, d.previousGpa)
                .putString(K_PREV_CREDITS, d.previousCredits)
                .apply();
    }
}
