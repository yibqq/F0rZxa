package com.mizan.gpa.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.mizan.gpa.calc.ExamReminders;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Exam;
import com.mizan.gpa.data.LectureSlot;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.util.Fmt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lecture and exam reminders share a single alarm: it is always set for the earliest upcoming reminder,
 * and every time it fires (or the data changes, or the device reboots) the next one is set. Exam
 * reminders that came due while the phone was off are delivered late rather than lost.
 */
public final class ReminderScheduler {
    private static final int REQUEST_CODE = 4100;
    private static final long DAY = 24L * 60 * 60 * 1000;

    private ReminderScheduler() {}

    public static final class Next {
        public Course course;
        public LectureSlot slot;
        /** Epoch millis of the lecture start. */
        public long lectureAt;
    }

    /** A reminder that is due at {@link #triggerAt} for a lecture or an exam starting at {@link #eventAt}. */
    static final class Event {
        Course course;
        LectureSlot slot;
        Exam exam;
        long eventAt;
        long triggerAt;
    }

    /** The next lecture (start time after {@code fromMillis}) of the current semester. */
    public static Next nextLecture(AppData d, long fromMillis) {
        Semester s = d.currentSemester();
        if (s == null) return null;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Next best = null;
        for (Course c : s.courses) {
            for (LectureSlot slot : c.lectures) {
                for (int i = 0; i <= 7; i++) {
                    LocalDate day = today.plusDays(i);
                    if (Fmt.toCalendarDay(day.getDayOfWeek()) != slot.day) continue;
                    LocalDateTime at = day.atStartOfDay().plusMinutes(slot.start);
                    long ms = at.atZone(zone).toInstant().toEpochMilli();
                    if (ms <= fromMillis) continue;
                    if (best == null || ms < best.lectureAt) {
                        best = new Next();
                        best.course = c;
                        best.slot = slot;
                        best.lectureAt = ms;
                    }
                    break;
                }
            }
        }
        return best;
    }

    /** Every reminder whose trigger time lies in (from, to]. */
    static List<Event> between(AppData d, long from, long to) {
        List<Event> out = new ArrayList<>();
        Settings st = d.settings;
        ZoneId zone = ZoneId.systemDefault();
        if (st.remindersEnabled) {
            Semester s = d.currentSemester();
            long lead = st.reminderMinutes * 60_000L;
            LocalDate first = java.time.Instant.ofEpochMilli(from).atZone(zone).toLocalDate().minusDays(1);
            LocalDate last = java.time.Instant.ofEpochMilli(to).atZone(zone).toLocalDate().plusDays(1);
            if (s != null && !last.isAfter(first.plusDays(40))) {
                for (Course c : s.courses) {
                    for (LectureSlot slot : c.lectures) {
                        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
                            if (Fmt.toCalendarDay(day.getDayOfWeek()) != slot.day) continue;
                            long at = day.atStartOfDay().plusMinutes(slot.start).atZone(zone).toInstant().toEpochMilli();
                            long trigger = at - lead;
                            if (trigger <= from || trigger > to) continue;
                            Event e = new Event();
                            e.course = c;
                            e.slot = slot;
                            e.eventAt = at;
                            e.triggerAt = trigger;
                            out.add(e);
                        }
                    }
                }
            }
        }
        if (st.examReminders) {
            for (Course c : d.allCourses()) {
                for (Exam exam : c.exams) {
                    LocalDateTime start = exam.startDateTime();
                    if (start == null) continue;
                    long at = start.atZone(zone).toInstant().toEpochMilli();
                    for (ExamReminders.Trigger t : ExamReminders.triggers(exam, st)) {
                        long trigger = t.at.atZone(zone).toInstant().toEpochMilli();
                        if (trigger <= from || trigger > to) continue;
                        Event e = new Event();
                        e.course = c;
                        e.exam = exam;
                        e.eventAt = at;
                        e.triggerAt = trigger;
                        out.add(e);
                    }
                }
            }
        }
        return out;
    }

    /** Trigger time of the earliest reminder after {@code now}, or 0 if there is none. */
    static long nextTrigger(AppData d, long now) {
        long best = 0;
        // Lectures repeat weekly, so eight days ahead always contains the next one.
        for (Event e : between(d, now, now + 8 * DAY)) {
            if (best == 0 || e.triggerAt < best) best = e.triggerAt;
        }
        if (d.settings.examReminders) {
            ZoneId zone = ZoneId.systemDefault();
            for (Course c : d.allCourses()) {
                for (Exam exam : c.exams) {
                    for (ExamReminders.Trigger t : ExamReminders.triggers(exam, d.settings)) {
                        long trigger = t.at.atZone(zone).toInstant().toEpochMilli();
                        if (trigger > now && (best == 0 || trigger < best)) best = trigger;
                    }
                }
            }
        }
        return best;
    }

    public static void scheduleNext(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQUEST_CODE, new Intent(ctx, ReminderReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        long trigger = nextTrigger(Repository.get(ctx).data(), System.currentTimeMillis());
        if (trigger == 0) return;
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            else am.setWindow(AlarmManager.RTC_WAKEUP, trigger, 60_000L, pi);
        } catch (SecurityException e) {
            am.setWindow(AlarmManager.RTC_WAKEUP, trigger, 60_000L, pi);
        }
    }

    /**
     * Shows every reminder that became due since the last delivery, then remembers the time.
     * Lecture reminders are dropped once the lecture is well under way; for an exam only its most
     * recent due reminder is shown, and only while the exam has not started long ago.
     */
    public static void deliverDue(Context ctx) {
        Repository repo = Repository.get(ctx);
        AppData d = repo.data();
        long now = System.currentTimeMillis();
        long last = d.settings.lastReminderCheck;
        long from = last > 0 && last <= now ? Math.max(last, now - 3 * DAY) : now - 90_000L;
        Map<String, Event> latest = new HashMap<>();
        for (Event e : between(d, from, now + 5_000L)) {
            boolean fresh = e.exam != null ? now < e.eventAt + 15 * 60_000L : now < e.eventAt + 30 * 60_000L;
            if (!fresh) continue;
            String key = e.exam != null ? "x" + e.exam.id : "l" + e.course.id;
            Event prev = latest.get(key);
            if (prev == null || e.triggerAt > prev.triggerAt) latest.put(key, e);
        }
        for (Event e : latest.values()) {
            if (e.exam != null) Notifier.examReminder(ctx, e.course, e.exam);
            else Notifier.lectureReminder(ctx, e.course, e.slot, (int) Math.max(0, (e.eventAt - now + 30_000L) / 60_000L));
        }
        d.settings.lastReminderCheck = now;
        repo.saveQuietly();
    }

    public static boolean exactAlarmsAllowed(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        return Build.VERSION.SDK_INT < 31 || (am != null && am.canScheduleExactAlarms());
    }
}
