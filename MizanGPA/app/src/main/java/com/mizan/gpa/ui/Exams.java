package com.mizan.gpa.ui;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Exam;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Labels, countdowns and ordering for exams, shared by screens, widgets and notifications. */
public final class Exams {
    private Exams() {}

    /** An exam together with the course it belongs to. */
    public static final class Entry {
        public final Course course;
        public final int courseIndex;
        public final Exam exam;
        public final LocalDateTime start;

        Entry(Course course, int courseIndex, Exam exam, LocalDateTime start) {
            this.course = course;
            this.courseIndex = courseIndex;
            this.exam = exam;
            this.start = start;
        }

        public int color() {
            return Ui.courseColor(course.color, courseIndex);
        }
    }

    public static String typeLabel(Context c, String type) {
        switch (type) {
            case Exam.MIDTERM: return c.getString(R.string.exam_type_midterm);
            case Exam.FINAL: return c.getString(R.string.exam_type_final);
            case Exam.QUIZ: return c.getString(R.string.exam_type_quiz);
            case Exam.PRACTICAL: return c.getString(R.string.exam_type_practical);
            default: return c.getString(R.string.exam_type_other);
        }
    }

    /** The exam's own title, or its type when it has none. */
    public static String title(Context c, Exam e) {
        return e.title != null && !e.title.trim().isEmpty() ? e.title.trim() : typeLabel(c, e.type);
    }

    /** "Sun 10 Nov · 9:00 AM – 11:00 AM". */
    public static String when(Context c, Exam e) {
        String time = e.hasEnd() ? Fmt.timeRange(c, e.start, e.end) : Fmt.time(c, e.start);
        LocalDateTime start = e.startDateTime();
        if (start == null) return time;
        return Fmt.dayName(c, Fmt.toCalendarDay(start.getDayOfWeek()), true) + " " + Fmt.dateShort(c, start.toLocalDate()) + " · " + time;
    }

    public static boolean isPast(Exam e, LocalDateTime now) {
        LocalDateTime end = e.endDateTime();
        return end == null || (e.hasEnd() ? now.isAfter(end) : now.isAfter(end.plusHours(2)));
    }

    public static boolean isRunning(Exam e, LocalDateTime now) {
        LocalDateTime start = e.startDateTime();
        return start != null && !now.isBefore(start) && !isPast(e, now);
    }

    /**
     * Time left in words: "in 3 days", "tomorrow", "today", "in 45 minutes", "now", "done".
     *
     * @param precise on the exam day, give hours/minutes instead of just "today"
     */
    public static String countdown(Context c, Exam e, LocalDateTime now, boolean precise) {
        LocalDateTime start = e.startDateTime();
        if (start == null) return "";
        if (isRunning(e, now)) return c.getString(R.string.exam_now);
        if (now.isAfter(start)) return c.getString(R.string.exam_done);
        long days = ChronoUnit.DAYS.between(now.toLocalDate(), start.toLocalDate());
        if (days == 0) {
            long minutes = ChronoUnit.MINUTES.between(now, start);
            if (minutes < 1) return c.getString(R.string.exam_now);
            if (!precise) return c.getString(R.string.today);
            if (minutes < 60) return c.getResources().getQuantityString(R.plurals.in_minutes, (int) minutes, (int) minutes);
            int hours = (int) Math.round(minutes / 60.0);
            return c.getResources().getQuantityString(R.plurals.in_hours, hours, hours);
        }
        if (days == 1) return c.getString(R.string.tomorrow);
        return c.getResources().getQuantityString(R.plurals.in_days, (int) days, (int) days);
    }

    /** Red when the exam is within a day, amber within three days, otherwise the given default. */
    public static int urgencyColor(Context c, Exam e, LocalDateTime now, int normal) {
        LocalDateTime start = e.startDateTime();
        if (start == null || isPast(e, now)) return Ui.attr(c, com.google.android.material.R.attr.colorOutline);
        long days = ChronoUnit.DAYS.between(now.toLocalDate(), start.toLocalDate());
        if (days <= 1) return ContextCompat.getColor(c, R.color.status_danger);
        if (days <= 3) return ContextCompat.getColor(c, R.color.status_warning);
        return normal;
    }

    /** Exams of a semester: upcoming ones first (soonest first), then past ones (latest first). */
    public static List<Entry> of(Semester s, LocalDateTime now) {
        List<Entry> upcoming = new ArrayList<>(), past = new ArrayList<>();
        if (s == null) return upcoming;
        for (int i = 0; i < s.courses.size(); i++) {
            Course course = s.courses.get(i);
            for (Exam e : course.exams) {
                LocalDateTime start = e.startDateTime();
                if (start == null) continue;
                (isPast(e, now) ? past : upcoming).add(new Entry(course, i, e, start));
            }
        }
        upcoming.sort((a, b) -> a.start.compareTo(b.start));
        past.sort((a, b) -> b.start.compareTo(a.start));
        upcoming.addAll(past);
        return upcoming;
    }

    public static List<Entry> upcoming(AppData d, LocalDateTime now) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : of(d.currentSemester(), now)) if (!isPast(e.exam, now)) out.add(e);
        return out;
    }

    // ------------------------------------------------------------------ reminder option labels

    public static final int[] DAY_OPTIONS = {1, 2, 3, 7};
    public static final int[] MINUTE_OPTIONS = {0, 15, 30, 60, 120, 180};

    public static String dayLabel(Context c, int days) {
        return c.getResources().getQuantityString(R.plurals.days_before, days, days);
    }

    public static String minuteLabel(Context c, int minutes) {
        if (minutes == 0) return c.getString(R.string.at_exam_time);
        if (minutes == 30) return c.getString(R.string.half_hour_before);
        if (minutes % 60 == 0) return c.getResources().getQuantityString(R.plurals.hours_before, minutes / 60, minutes / 60);
        return c.getResources().getQuantityString(R.plurals.minutes_before, minutes, minutes);
    }

    /** "1 day before, 1 hour before" style summary of a set of reminders. */
    public static String summary(Context c, List<Integer> days, List<Integer> minutes) {
        List<Integer> d = new ArrayList<>(days), m = new ArrayList<>(minutes);
        d.sort((a, b) -> b - a);
        m.sort((a, b) -> b - a);
        StringBuilder b = new StringBuilder();
        for (int x : d) b.append(b.length() > 0 ? c.getString(R.string.list_separator) + " " : "").append(dayLabel(c, x));
        for (int x : m) b.append(b.length() > 0 ? c.getString(R.string.list_separator) + " " : "").append(minuteLabel(c, x));
        return b.length() == 0 ? c.getString(R.string.no_reminders) : b.toString();
    }

    public static LocalDate today() {
        return LocalDate.now();
    }
}
