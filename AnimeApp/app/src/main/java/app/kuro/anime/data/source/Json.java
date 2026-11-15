package app.kuro.anime.data.source;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

/** Null-safe JSON accessors ({@code optString} returns the string "null" for JSON null). */
public final class Json {
    private Json() {}

    @Nullable
    public static String str(@Nullable JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return null;
        String s = o.optString(key, null);
        return s == null || s.isEmpty() ? null : s;
    }

    public static int integer(@Nullable JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return 0;
        return o.optInt(key, 0);
    }

    public static long lng(@Nullable JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return 0;
        return o.optLong(key, 0);
    }

    public static double dbl(@Nullable JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return 0;
        return o.optDouble(key, 0);
    }

    public static boolean bool(@Nullable JSONObject o, String key) {
        return o != null && o.has(key) && !o.isNull(key) && o.optBoolean(key, false);
    }

    @Nullable
    public static JSONObject obj(@Nullable JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return null;
        return o.optJSONObject(key);
    }

    public static JSONArray arr(@Nullable JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return new JSONArray();
        JSONArray a = o.optJSONArray(key);
        return a == null ? new JSONArray() : a;
    }
}
