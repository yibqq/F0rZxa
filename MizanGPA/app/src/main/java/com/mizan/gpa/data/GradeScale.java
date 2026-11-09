package com.mizan.gpa.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A grading scale: an ordered list of letter grades (highest first).
 * The maximum GPA of the scale is the highest grade-point value (e.g. 4.00 or 5.00).
 */
public class GradeScale {
    public static final String PRESET_SA5 = "sa5";
    public static final String PRESET_SA4 = "sa4";
    public static final String PRESET_US4 = "us4";
    public static final String PRESET_CUSTOM = "custom";

    public String preset = PRESET_SA5;
    public final List<GradeDef> grades = new ArrayList<>();

    /** Sorts grades from the highest minimum percentage to the lowest. */
    public void sort() {
        Collections.sort(grades, (a, b) -> {
            int c = Double.compare(b.minPercent, a.minPercent);
            return c != 0 ? c : Double.compare(b.points, a.points);
        });
    }

    public double maxPoints() {
        double max = 0;
        for (GradeDef g : grades) max = Math.max(max, g.points);
        return max;
    }

    public double minPoints() {
        if (grades.isEmpty()) return 0;
        double min = Double.MAX_VALUE;
        for (GradeDef g : grades) min = Math.min(min, g.points);
        return min;
    }

    public GradeDef find(String letter) {
        if (letter == null) return null;
        for (GradeDef g : grades) if (g.letter.equals(letter)) return g;
        for (GradeDef g : grades) if (g.letter.equalsIgnoreCase(letter.trim())) return g;
        return null;
    }

    public GradeDef top() {
        return grades.isEmpty() ? null : grades.get(0);
    }

    /** The lowest (failing) grade of the scale. */
    public GradeDef bottom() {
        return grades.isEmpty() ? null : grades.get(grades.size() - 1);
    }

    public boolean isFailing(GradeDef g) {
        return g != null && grades.size() > 1 && g == bottom();
    }

    /**
     * Converts a percentage to a letter grade.
     *
     * @param round when true the percentage is rounded half-up to a whole number first
     *              (as many universities do), so 89.5 counts as 90.
     */
    public GradeDef forPercent(double percent, boolean round) {
        if (grades.isEmpty() || Double.isNaN(percent)) return null;
        double p = round ? Math.floor(percent + 0.5) : percent;
        for (GradeDef g : grades) {
            if (p >= g.minPercent - 1e-9) return g;
        }
        return bottom();
    }

    /** The percentage needed to earn the grade, taking rounding into account. */
    public static double effectiveMin(GradeDef g, boolean round) {
        return round ? g.minPercent - 0.5 : g.minPercent;
    }

    public String[] letters() {
        String[] out = new String[grades.size()];
        for (int i = 0; i < grades.size(); i++) out[i] = grades.get(i).letter;
        return out;
    }

    public GradeScale copy() {
        GradeScale s = new GradeScale();
        s.preset = preset;
        for (GradeDef g : grades) s.grades.add(g.copy());
        return s;
    }

    public static GradeScale preset(String id) {
        GradeScale s = new GradeScale();
        s.preset = id;
        switch (id) {
            case PRESET_SA4:
                s.add("A+", 4.00, 95).add("A", 3.75, 90).add("B+", 3.50, 85).add("B", 3.00, 80)
                        .add("C+", 2.50, 75).add("C", 2.00, 70).add("D+", 1.50, 65).add("D", 1.00, 60)
                        .add("F", 0.00, 0);
                break;
            case PRESET_US4:
                s.add("A", 4.00, 93).add("A-", 3.70, 90).add("B+", 3.30, 87).add("B", 3.00, 83)
                        .add("B-", 2.70, 80).add("C+", 2.30, 77).add("C", 2.00, 73).add("C-", 1.70, 70)
                        .add("D+", 1.30, 67).add("D", 1.00, 60).add("F", 0.00, 0);
                break;
            case PRESET_SA5:
            default:
                s.preset = id.equals(PRESET_CUSTOM) ? PRESET_CUSTOM : PRESET_SA5;
                s.add("A+", 5.00, 95).add("A", 4.75, 90).add("B+", 4.50, 85).add("B", 4.00, 80)
                        .add("C+", 3.50, 75).add("C", 3.00, 70).add("D+", 2.50, 65).add("D", 2.00, 60)
                        .add("F", 1.00, 0);
                break;
        }
        return s;
    }

    private GradeScale add(String letter, double points, double min) {
        grades.add(new GradeDef(letter, points, min));
        return this;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("preset", preset);
        JSONArray a = new JSONArray();
        for (GradeDef g : grades) a.put(g.toJson());
        o.put("grades", a);
        return o;
    }

    public static GradeScale fromJson(JSONObject o) {
        if (o == null) return preset(PRESET_SA5);
        GradeScale s = new GradeScale();
        s.preset = Json.str(o, "preset", PRESET_CUSTOM);
        JSONArray a = Json.arr(o, "grades");
        for (int i = 0; i < a.length(); i++) {
            JSONObject g = a.optJSONObject(i);
            if (g != null) s.grades.add(GradeDef.fromJson(g));
        }
        if (s.grades.isEmpty()) return preset(PRESET_SA5);
        s.sort();
        return s;
    }
}
