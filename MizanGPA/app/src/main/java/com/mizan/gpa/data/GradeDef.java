package com.mizan.gpa.data;

import org.json.JSONException;
import org.json.JSONObject;

/** One letter grade of a grading scale: its grade points and the minimum percentage that earns it. */
public class GradeDef {
    public String letter;
    public double points;
    public double minPercent;

    public GradeDef() {}

    public GradeDef(String letter, double points, double minPercent) {
        this.letter = letter;
        this.points = points;
        this.minPercent = minPercent;
    }

    public GradeDef copy() {
        return new GradeDef(letter, points, minPercent);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("letter", letter);
        o.put("points", points);
        o.put("minPercent", minPercent);
        return o;
    }

    public static GradeDef fromJson(JSONObject o) {
        return new GradeDef(Json.str(o, "letter", "?"), Json.num(o, "points", 0), Json.num(o, "minPercent", 0));
    }
}
