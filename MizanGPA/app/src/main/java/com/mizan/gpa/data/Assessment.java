package com.mizan.gpa.data;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A graded piece of work (quiz, midterm, assignment, project, final...).
 * {@code score} is out of {@code outOf}; {@code weight} is how many marks of the course total it is worth.
 * A NaN score means "not graded yet".
 */
public class Assessment {
    public static final String QUIZ = "quiz";
    public static final String MIDTERM = "midterm";
    public static final String ASSIGNMENT = "assignment";
    public static final String PROJECT = "project";
    public static final String LAB = "lab";
    public static final String PARTICIPATION = "participation";
    public static final String FINAL = "final";
    public static final String OTHER = "other";
    public static final String[] TYPES = {QUIZ, MIDTERM, ASSIGNMENT, PROJECT, LAB, PARTICIPATION, FINAL, OTHER};

    public String id = Json.newId();
    public String name = "";
    public String type = QUIZ;
    public double score = Double.NaN;
    public double outOf = 10;
    public double weight = 10;

    public boolean isGraded() {
        return !Double.isNaN(score);
    }

    /** Marks this assessment contributes to the course total. */
    public double contribution() {
        if (!isGraded() || outOf <= 0) return 0;
        return score / outOf * weight;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("type", type);
        Json.put(o, "score", isGraded() ? score : null);
        o.put("outOf", outOf);
        o.put("weight", weight);
        return o;
    }

    public static Assessment fromJson(JSONObject o) {
        Assessment a = new Assessment();
        a.id = Json.str(o, "id", Json.newId());
        a.name = Json.str(o, "name", "");
        a.type = Json.str(o, "type", OTHER);
        a.score = Json.num(o, "score", Double.NaN);
        a.outOf = Json.num(o, "outOf", 10);
        a.weight = Json.num(o, "weight", a.outOf);
        return a;
    }
}
