package com.mizan.gpa.calc;

import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeDef;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GPA = sum(grade points x credit hours) / sum(credit hours), over the courses that count in the GPA
 * and have a grade under the chosen mode.
 */
public final class GpaCalculator {
    private GpaCalculator() {}

    public static final class Totals {
        public double points;
        public double credits;
        public int courses;

        public double gpa() {
            return credits > 1e-9 ? points / credits : Double.NaN;
        }

        public Totals plus(Totals o) {
            Totals t = new Totals();
            t.points = points + o.points;
            t.credits = credits + o.credits;
            t.courses = courses + o.courses;
            return t;
        }
    }

    public static Totals semester(Semester s, Settings st, GradeResolver.Mode mode) {
        return semester(s, st, mode, null);
    }

    public static Totals semester(Semester s, Settings st, GradeResolver.Mode mode, Map<String, String> overrides) {
        Totals t = new Totals();
        for (Course c : s.courses) {
            if (!c.countInGpa || c.credits <= 0) continue;
            GradeDef g = GradeResolver.resolve(c, st.scale, st.roundPercent, mode, overrides);
            if (g == null) continue;
            t.points += g.points * c.credits;
            t.credits += c.credits;
            t.courses++;
        }
        return t;
    }

    /** Prior studies entered in the settings, as totals. */
    public static Totals prior(Settings st) {
        Totals t = new Totals();
        if (st.priorCredits > 0) {
            t.credits = st.priorCredits;
            t.points = Math.min(st.priorGpa, st.scale.maxPoints()) * st.priorCredits;
        }
        return t;
    }

    public static Totals cumulative(AppData d, GradeResolver.Mode mode) {
        return cumulative(d, mode, null);
    }

    public static Totals cumulative(AppData d, GradeResolver.Mode mode, Map<String, String> overrides) {
        Totals t = prior(d.settings);
        for (Semester s : d.semesters) t = t.plus(semester(s, d.settings, mode, overrides));
        return t;
    }

    /** Cumulative totals up to and including the given semester. */
    public static Totals cumulativeUpTo(AppData d, Semester upTo, GradeResolver.Mode mode) {
        Totals t = prior(d.settings);
        for (Semester s : d.semesters) {
            t = t.plus(semester(s, d.settings, mode, null));
            if (s == upTo) break;
        }
        return t;
    }

    /** Credit hours of courses in the semester that count in the GPA but have no official grade yet. */
    public static double inProgressCredits(Semester s) {
        double t = 0;
        if (s == null) return 0;
        for (Course c : s.courses) if (c.countInGpa && !c.isCompleted() && c.credits > 0) t += c.credits;
        return t;
    }

    public static final class TrendPoint {
        public Semester semester;
        public double semesterGpa;
        public double cumulativeGpa;
        /** True when the point uses expected grades because official ones are missing. */
        public boolean projected;
    }

    /** Semester and cumulative GPA after each semester (official grades, or expected ones if none yet). */
    public static List<TrendPoint> trend(AppData d) {
        List<TrendPoint> out = new ArrayList<>();
        Totals running = prior(d.settings);
        for (Semester s : d.semesters) {
            Totals official = semester(s, d.settings, GradeResolver.Mode.OFFICIAL);
            Totals expected = semester(s, d.settings, GradeResolver.Mode.EXPECTED);
            boolean projected = expected.credits > official.credits + 1e-9;
            Totals use = projected ? expected : official;
            if (use.credits <= 1e-9) continue;
            running = running.plus(use);
            TrendPoint p = new TrendPoint();
            p.semester = s;
            p.semesterGpa = use.gpa();
            p.cumulativeGpa = running.gpa();
            p.projected = projected;
            out.add(p);
        }
        return out;
    }

    /**
     * GPA needed over {@code newCredits} more credit hours to bring the cumulative GPA to {@code target}.
     * Returns NaN when there are no new credits.
     */
    public static double requiredGpa(Totals current, double target, double newCredits) {
        if (newCredits <= 1e-9) return Double.NaN;
        return (target * (current.credits + newCredits) - current.points) / newCredits;
    }

    /**
     * Minimum credit hours at the maximum grade needed to reach the target.
     * Returns 0 when the target is already reached and +Infinity when it can never be reached.
     */
    public static double creditsNeededAtMax(Totals current, double target, double maxPoints) {
        if (current.credits > 1e-9 && current.gpa() >= target - 1e-9) return 0;
        if (target >= maxPoints - 1e-9) return Double.POSITIVE_INFINITY;
        double needed = (target * current.credits - current.points) / (maxPoints - target);
        return Math.max(0, needed);
    }
}
