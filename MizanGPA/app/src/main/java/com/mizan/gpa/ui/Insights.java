package com.mizan.gpa.ui;

import android.content.Context;

import com.mizan.gpa.R;
import com.mizan.gpa.calc.AttendanceCalculator;
import com.mizan.gpa.calc.GpaCalculator;
import com.mizan.gpa.calc.GradeResolver;
import com.mizan.gpa.calc.MarksCalculator;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeDef;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.notify.ReminderScheduler;
import com.mizan.gpa.util.Fmt;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Builds the list of alerts and tips shown on the dashboard. */
public final class Insights {
    private Insights() {}

    public enum Level { DANGER, WARNING, INFO, SUCCESS }

    public static final class Alert {
        public Level level;
        public int icon;
        public String title;
        public String text;
        public String courseId;
        public int courseTab = -1;
        /** Opens the forecast tab when tapped. */
        public boolean openForecast;
        public boolean openSchedule;

        Alert(Level level, int icon, String title, String text) {
            this.level = level;
            this.icon = icon;
            this.title = title;
            this.text = text;
        }
    }

    public static List<Alert> build(Context c, AppData d) {
        List<Alert> out = new ArrayList<>();
        Settings st = d.settings;
        Semester cur = d.currentSemester();
        if (cur == null) return out;

        List<Alert> attendance = new ArrayList<>();
        for (Course course : cur.courses) {
            if (course.isCompleted()) continue;
            AttendanceCalculator.Stats s = AttendanceCalculator.compute(course, cur, st);
            Alert a = null;
            switch (s.status) {
                case DEPRIVED:
                    a = new Alert(Level.DANGER, R.drawable.ic_error, c.getString(R.string.alert_deprived_title, course.displayName()),
                            c.getString(R.string.alert_deprived_text, s.counted, s.allowed));
                    break;
                case CRITICAL:
                    a = new Alert(Level.DANGER, R.drawable.ic_warning, c.getString(R.string.alert_critical_title, course.displayName()),
                            c.getString(R.string.alert_critical_text, s.counted, Fmt.pct(s.percent)));
                    break;
                case WARNING:
                    a = new Alert(Level.WARNING, R.drawable.ic_warning, c.getString(R.string.alert_warning_title, course.displayName()),
                            c.getResources().getQuantityString(R.plurals.alert_warning_text, s.remaining, s.remaining, Fmt.pct(s.percent)));
                    break;
                case UNKNOWN:
                    if (s.recorded() > 0) {
                        a = new Alert(Level.INFO, R.drawable.ic_info, c.getString(R.string.alert_unknown_total_title, course.displayName()),
                                c.getString(R.string.alert_unknown_total_text));
                    }
                    break;
                default:
                    break;
            }
            if (a != null) {
                a.courseId = course.id;
                a.courseTab = CourseActivity.TAB_ATTENDANCE;
                attendance.add(a);
            }

            MarksCalculator.Result m = MarksCalculator.compute(course);
            if (m.hasMarks() && !m.isComplete() && course.countInGpa) {
                GradeDef worstPossibleTop = st.scale.forPercent(m.maxPercent(), st.roundPercent);
                GradeDef predicted = st.scale.forPercent(m.projectedPercent(), st.roundPercent);
                if (st.scale.isFailing(worstPossibleTop)) {
                    Alert f = new Alert(Level.DANGER, R.drawable.ic_error, c.getString(R.string.alert_cannot_pass_title, course.displayName()),
                            c.getString(R.string.alert_cannot_pass_text, Fmt.pct(m.maxPercent())));
                    f.courseId = course.id;
                    f.courseTab = CourseActivity.TAB_MARKS;
                    out.add(f);
                } else if (st.scale.isFailing(predicted)) {
                    Alert f = new Alert(Level.WARNING, R.drawable.ic_trending_up, c.getString(R.string.alert_at_risk_title, course.displayName()),
                            c.getString(R.string.alert_at_risk_text, Fmt.pct(m.currentPercent())));
                    f.courseId = course.id;
                    f.courseTab = CourseActivity.TAB_MARKS;
                    out.add(f);
                }
            }
        }
        Collections.sort(attendance, (x, y) -> x.level.compareTo(y.level));
        out.addAll(0, attendance);

        // Target GPA feasibility.
        if (st.targetGpa > 0) {
            GpaCalculator.Totals base = GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL);
            double upcoming = GpaCalculator.inProgressCredits(cur);
            double req = GpaCalculator.requiredGpa(base, st.targetGpa, upcoming);
            if (!Double.isNaN(req) && req > st.scale.maxPoints() + 1e-9) {
                Alert t = new Alert(Level.WARNING, R.drawable.ic_flag, c.getString(R.string.alert_target_far_title, Fmt.gpa(st.targetGpa)),
                        c.getString(R.string.alert_target_far_text, Fmt.gpa(req)));
                t.openForecast = true;
                out.add(t);
            }
        }

        // Exams today or tomorrow.
        java.time.LocalDateTime nowTime = java.time.LocalDateTime.now();
        for (Exams.Entry e : Exams.upcoming(d, nowTime)) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(nowTime.toLocalDate(), e.start.toLocalDate());
            if (days > 1) break;
            Alert x = new Alert(days == 0 ? Level.DANGER : Level.WARNING, R.drawable.ic_exam,
                    c.getString(R.string.alert_exam_title, e.course.displayName(), Exams.countdown(c, e.exam, nowTime, true)),
                    Exams.title(c, e.exam) + " · " + Exams.when(c, e.exam)
                            + (e.exam.room == null || e.exam.room.trim().isEmpty() ? "" : " · " + e.exam.room.trim()));
            x.courseId = e.course.id;
            x.courseTab = CourseActivity.TAB_EXAMS;
            out.add(0, x);
        }

        // Next lecture today.
        ReminderScheduler.Next next = ReminderScheduler.nextLecture(d, System.currentTimeMillis());
        if (next != null) {
            LocalDate day = Instant.ofEpochMilli(next.lectureAt).atZone(ZoneId.systemDefault()).toLocalDate();
            if (day.equals(LocalDate.now())) {
                String where = next.slot.room == null || next.slot.room.trim().isEmpty() ? "" : " · " + next.slot.room.trim();
                Alert n = new Alert(Level.INFO, R.drawable.ic_clock, c.getString(R.string.alert_next_lecture_title, next.course.displayName()),
                        Fmt.timeRange(c, next.slot.start, next.slot.end) + where);
                n.openSchedule = true;
                out.add(n);
            }
        }
        return out;
    }

    public static int countAttendanceRisks(AppData d) {
        Semester cur = d.currentSemester();
        if (cur == null) return 0;
        int n = 0;
        for (Course c : cur.courses) {
            if (c.isCompleted()) continue;
            if (AttendanceCalculator.severity(AttendanceCalculator.compute(c, cur, d.settings).status) > 0) n++;
        }
        return n;
    }

    /** The current-semester course with the worst attendance status, or null when all are safe. */
    public static Course mostAtRisk(AppData d) {
        Semester cur = d.currentSemester();
        if (cur == null) return null;
        Course worst = null;
        int worstSeverity = 0;
        int worstRemaining = Integer.MAX_VALUE;
        for (Course c : cur.courses) {
            if (c.isCompleted()) continue;
            AttendanceCalculator.Stats s = AttendanceCalculator.compute(c, cur, d.settings);
            int sev = AttendanceCalculator.severity(s.status);
            if (sev > worstSeverity || (sev > 0 && sev == worstSeverity && s.remaining < worstRemaining)) {
                worst = c;
                worstSeverity = sev;
                worstRemaining = s.remaining;
            }
        }
        return worst;
    }

    public static boolean hasDeprivation(AppData d) {
        Semester cur = d.currentSemester();
        if (cur == null) return false;
        for (Course c : cur.courses) {
            if (!c.isCompleted() && AttendanceCalculator.compute(c, cur, d.settings).status == AttendanceCalculator.Status.DEPRIVED) return true;
        }
        return false;
    }
}
