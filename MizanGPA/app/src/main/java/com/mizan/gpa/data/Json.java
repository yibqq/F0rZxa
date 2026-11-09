package com.mizan.gpa.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** Small helpers around org.json that treat missing or null values consistently. */
public final class Json {
    private Json() {}

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String str(JSONObject o, String key, String def) {
        if (o == null || !o.has(key) || o.isNull(key)) return def;
        return o.optString(key, def);
    }

    public static String strOrNull(JSONObject o, String key) {
        String s = str(o, key, null);
        return (s == null || s.isEmpty()) ? null : s;
    }

    public static double num(JSONObject o, String key, double def) {
        if (o == null || !o.has(key) || o.isNull(key)) return def;
        double v = o.optDouble(key, def);
        return Double.isNaN(v) || Double.isInfinite(v) ? def : v;
    }

    public static int integer(JSONObject o, String key, int def) {
        if (o == null || !o.has(key) || o.isNull(key)) return def;
        return o.optInt(key, def);
    }

    public static boolean bool(JSONObject o, String key, boolean def) {
        if (o == null || !o.has(key) || o.isNull(key)) return def;
        return o.optBoolean(key, def);
    }

    public static JSONArray arr(JSONObject o, String key) {
        JSONArray a = o == null ? null : o.optJSONArray(key);
        return a == null ? new JSONArray() : a;
    }

    /** Puts a value, writing JSON null for null strings and NaN numbers. */
    public static void put(JSONObject o, String key, Object value) throws JSONException {
        if (value == null) {
            o.put(key, JSONObject.NULL);
        } else if (value instanceof Double && (((Double) value).isNaN() || ((Double) value).isInfinite())) {
            o.put(key, JSONObject.NULL);
        } else {
            o.put(key, value);
        }
    }
}
