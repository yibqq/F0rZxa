package com.mizan.gpa.calc;

import com.mizan.gpa.data.Task;
import com.mizan.gpa.data.Settings;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * When to remind about a task.
 *
 * <p>"Days before" reminders are sent N days before the date at a fixed time of day (for example 8 PM
 * the evening before). "Minutes before" reminders are relative to the task's time (0 = at the time)
 * and need a time. A task without a date, or one that is done, has no reminders at all. A task uses
 * its own lists when it has custom reminders, otherwise the defaults.
 */
public final class TaskReminders {
    private TaskReminders() {}

    public static final class Trigger implements Comparable<Trigger> {
        public final LocalDateTime at;
        /** True for a "days before" reminder, false for a "minutes before" one. */
        public final boolean dayKind;
        public final int amount;

        Trigger(LocalDateTime at, boolean dayKind, int amount) {
            this.at = at;
            this.dayKind = dayKind;
            this.amount = amount;
        }

        @Override
        public int compareTo(Trigger o) {
            return at.compareTo(o.at);
        }
    }

    public static List<Integer> days(Task e, Settings st) {
        return e.customReminders ? e.daysBefore : st.examDaysBefore;
    }

    public static List<Integer> minutes(Task e, Settings st) {
        return e.customReminders ? e.minutesBefore : st.examMinutesBefore;
    }

    /** All reminder times of the task in chronological order, without duplicates. */
    public static List<Trigger> triggers(Task e, Settings st) {
        List<Trigger> out = new ArrayList<>();
        if (e.done || !e.hasDate()) return out;
        LocalDateTime start = e.start();
        LocalDate date = start.toLocalDate();
        for (int d : days(e, st)) {
            if (d < 1) continue;
            LocalDateTime at = date.minusDays(d).atStartOfDay().plusMinutes(st.examDayTime);
            add(out, new Trigger(at, true, d));
        }
        for (int m : minutes(e, st)) {
            if (m < 0 || !e.hasTime()) continue;
            add(out, new Trigger(start.minusMinutes(m), false, m));
        }
        Collections.sort(out);
        return out;
    }

    private static void add(List<Trigger> list, Trigger t) {
        for (Trigger x : list) if (x.at.equals(t.at)) return;
        list.add(t);
    }

    /** True when the task will produce at least one reminder after {@code now}. */
    public static boolean hasUpcoming(Task e, Settings st, LocalDateTime now) {
        if (!st.examReminders) return false;
        for (Trigger t : triggers(e, st)) if (t.at.isAfter(now)) return true;
        return false;
    }
}
