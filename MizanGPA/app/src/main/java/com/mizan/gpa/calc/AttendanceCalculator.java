package com.mizan.gpa.calc;

import com.mizan.gpa.data.AttendanceRecord;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;

/**
 * Absence percentage, allowed absences and deprivation status of a course.
 *
 * <p>Absence % = counted absences / planned class meetings x 100. Counted absences are absences, plus
 * excused absences when the settings say so, plus one absence for every N late arrivals.
 */
public final class AttendanceCalculator {
    private static final double EPS = 1e-9;

    private AttendanceCalculator() {}

    public enum Status { SAFE, WARNING, CRITICAL, DEPRIVED, UNKNOWN }

    public static final class Stats {
        public int present;
        public int absent;
        public int late;
        public int excused;
        /** Absences that count toward deprivation. */
        public int counted;
        /** Planned class meetings in the semester (0 if unknown). */
        public int total;
        /** True when {@link #total} comes from the weekly schedule rather than being set by hand. */
        public boolean totalFromSchedule;
        public double limitPercent;
        /** Maximum absences without being deprived. */
        public int allowed;
        /** Absences left before deprivation (negative once deprived). */
        public int remaining;
        public double percent;
        public Status status = Status.UNKNOWN;

        public boolean hasTotal() {
            return total > 0;
        }

        public int recorded() {
            return present + absent + late + excused;
        }
    }

    public static int plannedSessions(Course c, Semester s) {
        if (c.totalSessions > 0) return c.totalSessions;
        int weeks = s == null ? 15 : s.weeks;
        return c.lectures.size() * weeks;
    }

    public static double limitFor(Course c, Settings st) {
        return c.absenceLimit > 0 ? c.absenceLimit : st.absenceLimit;
    }

    /** Maximum absences allowed out of {@code total} meetings with the given limit percent. */
    public static int allowedAbsences(int total, double limitPercent, boolean deprivedOnReach) {
        if (total <= 0) return 0;
        double threshold = limitPercent / 100.0 * total;
        if (deprivedOnReach) {
            // Deprived when absences >= threshold: allowed is the largest integer strictly below it.
            return Math.max(0, (int) Math.ceil(threshold - EPS) - 1);
        }
        // Deprived when absences > threshold.
        return Math.max(0, (int) Math.floor(threshold + EPS));
    }

    public static Stats compute(Course c, Semester s, Settings st) {
        Stats r = new Stats();
        for (AttendanceRecord rec : c.attendance) {
            int n = Math.max(1, rec.sessions);
            switch (rec.status) {
                case AttendanceRecord.PRESENT: r.present += n; break;
                case AttendanceRecord.LATE: r.late += n; break;
                case AttendanceRecord.EXCUSED: r.excused += n; break;
                case AttendanceRecord.ABSENT:
                default: r.absent += n; break;
            }
        }
        r.counted = r.absent;
        if (st.countExcused) r.counted += r.excused;
        if (st.latesPerAbsence > 0) r.counted += r.late / st.latesPerAbsence;

        r.totalFromSchedule = c.totalSessions <= 0;
        r.total = plannedSessions(c, s);
        r.limitPercent = limitFor(c, st);
        if (r.total <= 0) {
            r.status = Status.UNKNOWN;
            r.percent = Double.NaN;
            return r;
        }
        r.percent = r.counted * 100.0 / r.total;
        r.allowed = allowedAbsences(r.total, r.limitPercent, st.deprivedOnReach);
        r.remaining = r.allowed - r.counted;
        if (r.remaining < 0) r.status = Status.DEPRIVED;
        else if (r.remaining == 0) r.status = Status.CRITICAL;
        else if (r.remaining <= st.warnRemaining) r.status = Status.WARNING;
        else r.status = Status.SAFE;
        return r;
    }

    /** Higher is worse; used to sort and to decide whether to notify. */
    public static int severity(Status s) {
        switch (s) {
            case DEPRIVED: return 3;
            case CRITICAL: return 2;
            case WARNING: return 1;
            default: return 0;
        }
    }
}
