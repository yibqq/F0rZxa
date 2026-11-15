package com.mizan.gpa.ui;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Task;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Labels, states, ordering and filtering of tasks, shared by screens, widgets and notifications. */
public final class TaskUi {
    private TaskUi() {}

    public enum State {
        /** Has a date in the future (or is happening now). */
        UPCOMING,
        /** An assignment or reminder whose due time passed while still open. */
        OVERDUE,
        /** An exam or practical assessment that has taken place. */
        FINISHED,
        /** No date. */
        UNDATED,
        /** Marked as done by the user. */
        DONE
    }

    public static State state(Task t, LocalDateTime now) {
        if (t.done) return State.DONE;
        if (!t.hasDate()) return State.UNDATED;
        if (now.isAfter(t.end())) return t.isEvent() ? State.FINISHED : State.OVERDUE;
        return State.UPCOMING;
    }

    /** Open entries: upcoming, overdue or undated. */
    public static boolean isActive(Task t, LocalDateTime now) {
        State s = state(t, now);
        return s == State.UPCOMING || s == State.OVERDUE || s == State.UNDATED;
    }

    // ------------------------------------------------------------------ labels

    public static String categoryLabel(Context c, String category) {
        switch (category) {
            case Task.EXAM: return c.getString(R.string.task_cat_exam);
            case Task.ASSIGNMENT: return c.getString(R.string.task_cat_assignment);
            case Task.PRACTICAL: return c.getString(R.string.task_cat_practical);
            default: return c.getString(R.string.task_cat_general);
        }
    }

    public static int categoryIcon(String category) {
        switch (category) {
            case Task.EXAM: return R.drawable.ic_exam;
            case Task.ASSIGNMENT: return R.drawable.ic_assignment;
            case Task.PRACTICAL: return R.drawable.ic_tune;
            default: return R.drawable.ic_notifications;
        }
    }

    public static String kindLabel(Context c, String kind) {
        switch (kind) {
            case Task.KIND_MIDTERM: return c.getString(R.string.exam_type_midterm);
            case Task.KIND_FINAL: return c.getString(R.string.exam_type_final);
            case Task.KIND_QUIZ: return c.getString(R.string.exam_type_quiz);
            default: return c.getString(R.string.exam_type_other);
        }
    }

    /** The entry's title, or a sensible default from its kind/category when it has none. */
    public static String title(Context c, Task t) {
        if (t.title != null && !t.title.trim().isEmpty()) return t.title.trim();
        return Task.EXAM.equals(t.category) ? kindLabel(c, t.kind) : categoryLabel(c, t.category);
    }

    /** "Sun 10 Nov · 9:00 AM – 11:00 AM", "Sun 10 Nov", or "No date". */
    public static String when(Context c, Task t) {
        if (!t.hasDate()) return c.getString(R.string.task_no_date);
        String day = Fmt.dayName(c, Fmt.toCalendarDay(t.localDate().getDayOfWeek()), true) + " " + Fmt.dateShort(c, t.localDate());
        if (!t.hasTime()) return day;
        String time = t.hasEndTime() ? Fmt.timeRange(c, t.time, t.endTime) : Fmt.time(c, t.time);
        return day + " · " + time;
    }

    /**
     * Time left in words ("In 3 days", "Tomorrow", "In 45 minutes", "Now"), or the state
     * ("Overdue 2 days", "Finished", "Done"); empty for an undated entry.
     *
     * @param precise on the day itself, give hours/minutes instead of just "Today"
     */
    public static String countdown(Context c, Task t, LocalDateTime now, boolean precise) {
        State s = state(t, now);
        switch (s) {
            case DONE: return c.getString(R.string.task_done);
            case UNDATED: return "";
            case FINISHED: return c.getString(R.string.exam_done);
            case OVERDUE: {
                long days = ChronoUnit.DAYS.between(t.localDate(), now.toLocalDate());
                return days < 1 ? c.getString(R.string.task_overdue)
                        : c.getResources().getQuantityString(R.plurals.overdue_days, (int) days, (int) days);
            }
            default:
                break;
        }
        LocalDateTime start = t.start();
        if (t.hasTime() && !now.isBefore(start)) return c.getString(R.string.exam_now);
        long days = ChronoUnit.DAYS.between(now.toLocalDate(), start.toLocalDate());
        if (days <= 0) {
            if (!t.hasTime() || !precise) return c.getString(R.string.today);
            long minutes = ChronoUnit.MINUTES.between(now, start);
            if (minutes < 1) return c.getString(R.string.exam_now);
            if (minutes < 60) return c.getResources().getQuantityString(R.plurals.in_minutes, (int) minutes, (int) minutes);
            int hours = (int) Math.round(minutes / 60.0);
            return c.getResources().getQuantityString(R.plurals.in_hours, hours, hours);
        }
        if (days == 1) return c.getString(R.string.tomorrow);
        return c.getResources().getQuantityString(R.plurals.in_days, (int) days, (int) days);
    }

    /** Red when overdue or within a day, amber within three days, muted when closed, else {@code normal}. */
    public static int urgencyColor(Context c, Task t, LocalDateTime now, int normal) {
        State s = state(t, now);
        if (s == State.OVERDUE) return ContextCompat.getColor(c, R.color.status_danger);
        if (s == State.DONE || s == State.FINISHED || s == State.UNDATED) {
            return Ui.attr(c, com.google.android.material.R.attr.colorOutline);
        }
        long days = ChronoUnit.DAYS.between(now.toLocalDate(), t.localDate());
        if (days <= 1) return ContextCompat.getColor(c, R.color.status_danger);
        if (days <= 3) return ContextCompat.getColor(c, R.color.status_warning);
        return normal;
    }

    // ------------------------------------------------------------------ ordering and filtering

    public static final String SORT_DATE = "date";
    public static final String SORT_COURSE = "course";
    public static final String SORT_CATEGORY = "category";

    /**
     * Chronological order for open entries (overdue first, then soonest), undated ones after, and closed
     * ones (done/finished) newest first. Within equal keys, the title decides.
     */
    public static Comparator<Task> byDate(LocalDateTime now) {
        return (a, b) -> {
            int ga = group(a, now), gb = group(b, now);
            if (ga != gb) return Integer.compare(ga, gb);
            if (a.hasDate() && b.hasDate()) {
                int c = a.start().compareTo(b.start());
                if (c != 0) return ga == 3 ? -c : c;
            }
            return a.title.compareToIgnoreCase(b.title);
        };
    }

    private static int group(Task t, LocalDateTime now) {
        switch (state(t, now)) {
            case OVERDUE: return 0;
            case UPCOMING: return 1;
            case UNDATED: return 2;
            default: return 3;
        }
    }

    public static Comparator<Task> comparator(AppData d, String sort, LocalDateTime now) {
        Comparator<Task> date = byDate(now);
        if (SORT_CATEGORY.equals(sort)) {
            return Comparator.<Task>comparingInt(t -> categoryIndex(t.category)).thenComparing(date);
        }
        if (SORT_COURSE.equals(sort)) {
            return Comparator.<Task, String>comparing(t -> {
                Course c = t.courseId == null ? null : d.findCourse(t.courseId);
                return c == null ? "￿" : c.displayName();
            }).thenComparing(date);
        }
        return date;
    }

    private static int categoryIndex(String category) {
        for (int i = 0; i < Task.CATEGORIES.length; i++) if (Task.CATEGORIES[i].equals(category)) return i;
        return Task.CATEGORIES.length;
    }

    /**
     * Tasks matching the filters, sorted.
     *
     * @param category a category, or null for all
     * @param courseId a course, or null for all
     * @param closed   true for done/finished entries, false for open ones
     */
    public static List<Task> filter(AppData d, String category, String courseId, boolean closed, String sort, LocalDateTime now) {
        List<Task> out = new ArrayList<>();
        for (Task t : d.tasks) {
            if (category != null && !category.equals(t.category)) continue;
            if (courseId != null && !courseId.equals(t.courseId)) continue;
            if (isActive(t, now) == closed) continue;
            out.add(t);
        }
        out.sort(comparator(d, sort, now));
        return out;
    }

    /** Open tasks with a date, soonest first (overdue ones first). */
    public static List<Task> upcoming(AppData d, LocalDateTime now) {
        List<Task> out = new ArrayList<>();
        for (Task t : d.tasks) {
            State s = state(t, now);
            if (s == State.UPCOMING || s == State.OVERDUE) out.add(t);
        }
        out.sort(byDate(now));
        return out;
    }

    public static int countOverdue(AppData d, LocalDateTime now) {
        int n = 0;
        for (Task t : d.tasks) if (state(t, now) == State.OVERDUE) n++;
        return n;
    }

    /** Color of the course a task belongs to, or the theme's primary color. */
    public static int color(Context c, AppData d, Task t) {
        if (t.courseId != null) {
            for (Semester s : d.semesters) {
                int i = 0;
                for (Course course : s.courses) {
                    if (course.id.equals(t.courseId)) return Ui.courseColor(course.color, i);
                    i++;
                }
            }
        }
        return Ui.attr(c, androidx.appcompat.R.attr.colorPrimary);
    }

    public static String courseName(AppData d, Task t) {
        Course c = t.courseId == null ? null : d.findCourse(t.courseId);
        return c == null ? null : c.displayName();
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

    /** "1 day before, 1 hour before" style summary. */
    public static String summary(Context c, List<Integer> days, List<Integer> minutes) {
        List<Integer> d = new ArrayList<>(days), m = new ArrayList<>(minutes);
        d.sort((a, b) -> b - a);
        m.sort((a, b) -> b - a);
        StringBuilder b = new StringBuilder();
        String sep = c.getString(R.string.list_separator) + " ";
        for (int x : d) b.append(b.length() > 0 ? sep : "").append(dayLabel(c, x));
        for (int x : m) b.append(b.length() > 0 ? sep : "").append(minuteLabel(c, x));
        return b.length() == 0 ? c.getString(R.string.no_reminders) : b.toString();
    }
}
