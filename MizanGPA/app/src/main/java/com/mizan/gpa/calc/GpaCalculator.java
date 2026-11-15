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

    /** State of the manually entered cumulative GPA baseline. */
    public enum Baseline {
        /** No baseline entered. */
        NONE,
        /** GPA and credit hours are valid; the baseline is weighted into the cumulative GPA. */
        VALID,
        /** A GPA without credit hours: shown for reference, never used in calculations. */
        NO_CREDITS,
        /** The GPA is outside the current grading scale (e.g. 4.5 on a 4-point scale). */
        OUT_OF_SCALE
    }

    public static Baseline baseline(Settings st) {
        boolean hasGpa = st.priorGpa > 0;
        boolean hasCredits = st.priorCredits > 0;
        if (!hasGpa && !hasCredits) return Baseline.NONE;
        if (!hasCredits) return Baseline.NO_CREDITS;
        if (st.priorGpa < 0 || st.priorGpa > st.scale.maxPoints() + 1e-9) return Baseline.OUT_OF_SCALE;
        return Baseline.VALID;
    }

    /** The baseline as totals; empty unless it is {@link Baseline#VALID}. Never estimated or clamped. */
    public static Totals prior(Settings st) {
        Totals t = new Totals();
        if (baseline(st) == Baseline.VALID) {
            t.credits = st.priorCredits;
            t.points = st.priorGpa * st.priorCredits;
        }
        return t;
    }

    /**
     * Whether a semester is counted in the cumulative GPA. Semesters marked as already included in a
     * valid baseline are skipped so they are not counted twice; with no valid baseline they count.
     */
    public static boolean countsInCumulative(AppData d, Semester s) {
        return !(s.inBaseline && baseline(d.settings) == Baseline.VALID);
    }

    public static Totals cumulative(AppData d, GradeResolver.Mode mode) {
        return cumulative(d, mode, null);
    }

    public static Totals cumulative(AppData d, GradeResolver.Mode mode, Map<String, String> overrides) {
        Totals t = prior(d.settings);
        for (Semester s : d.semesters) if (countsInCumulative(d, s)) t = t.plus(semester(s, d.settings, mode, overrides));
        return t;
    }

    /** Cumulative totals up to and including the given semester. */
    public static Totals cumulativeUpTo(AppData d, Semester upTo, GradeResolver.Mode mode) {
        Totals t = prior(d.settings);
        for (Semester s : d.semesters) {
            if (countsInCumulative(d, s)) t = t.plus(semester(s, d.settings, mode, null));
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
            // A semester inside the baseline is already part of the starting cumulative GPA.
            if (countsInCumulative(d, s)) running = running.plus(use);
            TrendPoint p = new TrendPoint();
            p.semester = s;
            p.semesterGpa = use.gpa();
            p.cumulativeGpa = running.gpa();
            p.projected = projected;
            out.add(p);
        }
        return out;
    }

    /** One row of the GPA calculator: credit hours and a letter grade (either may be missing). */
    public static final class Entry {
        public final double credits;
        public final String letter;

        public Entry(double credits, String letter) {
            this.credits = credits;
            this.letter = letter;
        }
    }

    /** Result of the calculator: totals of the usable rows and how many rows were skipped. */
    public static final class Calculation {
        public final Totals totals = new Totals();
        /** Rows without a grade, without credit hours, or with a grade not in the scale. */
        public int ignored;
    }

    /**
     * Weighted GPA of calculator rows. Rows with missing or invalid data are skipped and counted,
     * never guessed. Uses the same grade-to-points mapping as saved courses.
     */
    public static Calculation calculate(List<Entry> rows, com.mizan.gpa.data.GradeScale scale) {
        Calculation c = new Calculation();
        for (Entry e : rows) {
            com.mizan.gpa.data.GradeDef g = scale.find(e.letter);
            if (g == null || Double.isNaN(e.credits) || e.credits <= 0) {
                c.ignored++;
                continue;
            }
            c.totals.points += g.points * e.credits;
            c.totals.credits += e.credits;
            c.totals.courses++;
        }
        return c;
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
