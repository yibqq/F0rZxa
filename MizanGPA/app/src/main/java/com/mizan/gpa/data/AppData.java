package com.mizan.gpa.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Everything the app stores, serialized to a single JSON document. */
public class AppData {
    /**
     * 2: tasks (reminders, assignments, exams) are stored at the top level instead of exams inside
     * courses; courses have a CRN and undated absences; semesters can be part of the GPA baseline;
     * settings have the interface mode.
     */
    public static final int VERSION = 2;
    public static final String FORMAT = "mizan-gpa";

    public Settings settings = new Settings();
    public final List<Semester> semesters = new ArrayList<>();
    public final List<Task> tasks = new ArrayList<>();

    public Task findTask(String id) {
        for (Task t : tasks) if (t.id.equals(id)) return t;
        return null;
    }

    public List<Task> tasksOf(String courseId) {
        List<Task> out = new ArrayList<>();
        for (Task t : tasks) if (courseId.equals(t.courseId)) out.add(t);
        return out;
    }

    /** Removes a course with its tasks. */
    public void removeCourse(Semester s, Course c) {
        s.courses.remove(c);
        tasks.removeIf(t -> c.id.equals(t.courseId));
    }

    /** Removes a semester with its courses' tasks. */
    public void removeSemester(Semester s) {
        for (Course c : s.courses) tasks.removeIf(t -> c.id.equals(t.courseId));
        semesters.remove(s);
        if (s.id.equals(settings.currentSemesterId)) settings.currentSemesterId = null;
    }

    // ------------------------------------------------------------------ CRN

    /** CRN in a comparable form: trimmed, spaces removed, Arabic-Indic digits as Western digits, upper case. */
    public static String normalizeCrn(String crn) {
        if (crn == null) return "";
        StringBuilder b = new StringBuilder();
        for (char ch : crn.trim().toCharArray()) {
            if (Character.isWhitespace(ch)) continue;
            if (ch >= '\u0660' && ch <= '\u0669') b.append((char) ('0' + ch - '\u0660'));
            else if (ch >= '\u06F0' && ch <= '\u06F9') b.append((char) ('0' + ch - '\u06F0'));
            else b.append(Character.toUpperCase(ch));
        }
        return b.toString();
    }

    public enum CrnProblem { NONE, WRONG_LENGTH, NOT_DIGITS, INVALID_CHARACTERS, TOO_LONG }

    /**
     * Checks a CRN against the university's rule when one is configured (exactly {@code digits}
     * digits); otherwise only rejects characters that cannot be part of any CRN. An empty CRN is fine.
     */
    public static CrnProblem checkCrn(String crn, int digits) {
        String c = normalizeCrn(crn);
        if (c.isEmpty()) return CrnProblem.NONE;
        if (digits > 0) {
            for (char ch : c.toCharArray()) if (ch < '0' || ch > '9') return CrnProblem.NOT_DIGITS;
            return c.length() == digits ? CrnProblem.NONE : CrnProblem.WRONG_LENGTH;
        }
        if (c.length() > 20) return CrnProblem.TOO_LONG;
        for (char ch : c.toCharArray()) {
            if (!Character.isLetterOrDigit(ch) && ch != '-' && ch != '_') return CrnProblem.INVALID_CHARACTERS;
        }
        return CrnProblem.NONE;
    }

    /**
     * The most recent saved course with this CRN (in any semester), to offer reusing its details.
     * Only the student's own records are searched; nothing is looked up online or invented.
     */
    public Course findCourseByCrn(String crn, Course except) {
        String c = normalizeCrn(crn);
        if (c.isEmpty()) return null;
        for (int i = semesters.size() - 1; i >= 0; i--) {
            for (Course course : semesters.get(i).courses) {
                if (course != except && c.equals(normalizeCrn(course.crn))) return course;
            }
        }
        return null;
    }

    /** Another course of the same semester with this CRN (duplicate enrollment), if any. */
    public static Course crnConflict(Semester s, String crn, Course except) {
        String c = normalizeCrn(crn);
        if (c.isEmpty() || s == null) return null;
        for (Course course : s.courses) if (course != except && c.equals(normalizeCrn(course.crn))) return course;
        return null;
    }

    /** Re-checks invariants before saving (one absence per course per date). */
    public void normalize() {
        for (Semester s : semesters) for (Course c : s.courses) c.collapseDuplicateAbsences();
    }

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
        JSONArray t = new JSONArray();
        for (Task x : tasks) t.put(x.toJson());
        o.put("tasks", t);
        return o;
    }

    public static AppData fromJson(JSONObject o) throws JSONException {
        if (o == null) throw new JSONException("Empty document");
        if (!o.has("semesters") && !o.has("settings")) throw new JSONException("Not a Mizan data file");
        AppData d = new AppData();
        d.settings = Settings.fromJson(o.optJSONObject("settings"));
        JSONArray a = Json.arr(o, "semesters");
        for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) != null) d.semesters.add(Semester.fromJson(a.optJSONObject(i)));
        JSONArray t = Json.arr(o, "tasks");
        for (int i = 0; i < t.length(); i++) if (t.optJSONObject(i) != null) d.tasks.add(Task.fromJson(t.optJSONObject(i)));
        migrate(d, o);
        return d;
    }

    /** Upgrades data written by older versions; never drops user data. */
    private static void migrate(AppData d, JSONObject o) {
        int version = Json.integer(o, "version", 1);
        // v1 -> v2: exams lived inside courses.
        for (Semester s : d.semesters) {
            for (Course c : s.courses) {
                for (JSONObject e : c.legacyExams) {
                    String id = Json.str(e, "id", null);
                    if (id == null || d.findTask(id) == null) d.tasks.add(Task.fromLegacyExam(e, c.id));
                }
                c.legacyExams.clear();
            }
        }
        // Users updating from version 1 already know the app: skip onboarding and keep the full interface.
        JSONObject st = o.optJSONObject("settings");
        boolean hadMode = st != null && st.has("appMode");
        if (version < 2 && !hadMode && !d.semesters.isEmpty()) {
            d.settings.onboarded = true;
            d.settings.appMode = Settings.MODE_STANDARD;
        }
    }

    public static AppData parse(String json) throws JSONException {
        return fromJson(new JSONObject(json));
    }

    public String serialize(int indent) throws JSONException {
        return indent > 0 ? toJson().toString(indent) : toJson().toString();
    }
}
