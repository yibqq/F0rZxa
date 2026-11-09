package com.mizan.gpa.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.LectureSlot;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.util.Fmt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Lecture reminders use a single alarm: it is always set for the next upcoming reminder of the current
 * semester, and every time it fires (or the data changes, or the device reboots) the next one is set.
 */
public final class ReminderScheduler {
    static final String EXTRA_COURSE = "course";
    static final String EXTRA_SLOT = "slot";
    static final String EXTRA_LECTURE_AT = "lectureAt";
    private static final int REQUEST_CODE = 4100;

    private ReminderScheduler() {}

    public static final class Next {
        public Course course;
        public LectureSlot slot;
        /** Epoch millis of the lecture start. */
        public long lectureAt;
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

    public static void scheduleNext(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent existing = pending(ctx, null, PendingIntent.FLAG_NO_CREATE);
        if (existing != null) am.cancel(existing);

        AppData d = Repository.get(ctx).data();
        if (!d.settings.remindersEnabled) return;
        long lead = d.settings.reminderMinutes * 60_000L;
        long now = System.currentTimeMillis();
        // Find the first lecture whose reminder time is still in the future.
        Next next = nextLecture(d, now + lead);
        if (next == null) return;
        long trigger = next.lectureAt - lead;

        Intent i = new Intent(ctx, ReminderReceiver.class)
                .putExtra(EXTRA_COURSE, next.course.id)
                .putExtra(EXTRA_SLOT, next.slot.id)
                .putExtra(EXTRA_LECTURE_AT, next.lectureAt);
        PendingIntent pi = pending(ctx, i, PendingIntent.FLAG_UPDATE_CURRENT);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            } else {
                am.setWindow(AlarmManager.RTC_WAKEUP, trigger, 60_000L, pi);
            }
        } catch (SecurityException e) {
            am.setWindow(AlarmManager.RTC_WAKEUP, trigger, 60_000L, pi);
        }
    }

    public static boolean exactAlarmsAllowed(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        return Build.VERSION.SDK_INT < 31 || (am != null && am.canScheduleExactAlarms());
    }

    private static PendingIntent pending(Context ctx, Intent intent, int flags) {
        Intent i = intent != null ? intent : new Intent(ctx, ReminderReceiver.class);
        return PendingIntent.getBroadcast(ctx, REQUEST_CODE, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }
}
