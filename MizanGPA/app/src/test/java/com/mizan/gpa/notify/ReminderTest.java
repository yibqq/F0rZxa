package com.mizan.gpa.notify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Exam;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Exam reminders end to end: which alarm is set next, and which notifications appear when it fires. */
@RunWith(RobolectricTestRunner.class)
public class ReminderTest {
    private Context ctx;
    private Course course;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        shadowOf((Application) ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        AppData d = new AppData();
        Semester s = new Semester();
        s.name = "Fall";
        course = new Course();
        course.name = "Networks";
        s.courses.add(course);
        d.semesters.add(s);
        Repository.get(ctx).replace(d);
    }

    private Exam exam(LocalDateTime start) {
        Exam e = new Exam();
        e.date = start.toLocalDate().toString();
        e.start = start.getHour() * 60 + start.getMinute();
        course.exams.add(e);
        return e;
    }

    private static long millis(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private List<Notification> shown() {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        return shadowOf(nm).getAllNotifications();
    }

    @Test
    public void nextAlarmIsTheEarliestExamReminder() {
        AppData d = Repository.get(ctx).data();
        LocalDateTime start = LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
        exam(start);
        // Defaults: one day before at 20:00, and one hour before.
        long expected = millis(start.toLocalDate().minusDays(1).atTime(20, 0));
        assertEquals(expected, ReminderScheduler.nextTrigger(d, System.currentTimeMillis()));
        // Once that one has passed, the next is one hour before the exam.
        assertEquals(millis(start.minusHours(1)), ReminderScheduler.nextTrigger(d, expected));
        // And nothing after the last reminder.
        assertEquals(0, ReminderScheduler.nextTrigger(d, millis(start)));
    }

    @Test
    public void dueReminderIsShownOnce() {
        AppData d = Repository.get(ctx).data();
        // The "one hour before" reminder came due a minute ago.
        exam(LocalDateTime.now().plusMinutes(59));
        d.settings.lastReminderCheck = System.currentTimeMillis() - 5 * 60_000L;
        ReminderScheduler.deliverDue(ctx);
        assertEquals(1, shown().size());
        assertEquals(Notifier.CHANNEL_EXAMS, shown().get(0).getChannelId());
        // Delivering again does not repeat it.
        ReminderScheduler.deliverDue(ctx);
        assertEquals(1, shown().size());
        assertTrue(d.settings.lastReminderCheck > 0);
    }

    @Test
    public void reminderMissedWhilePhoneWasOffIsDeliveredLate() {
        AppData d = Repository.get(ctx).data();
        // Exam tomorrow; the "day before" reminder was due while the phone was off.
        LocalDateTime start = LocalDateTime.now().plusHours(10);
        Exam e = exam(start);
        e.customReminders = true;
        e.minutesBefore.add(14 * 60);   // due four hours ago
        d.settings.lastReminderCheck = System.currentTimeMillis() - 8 * 60 * 60_000L;
        ReminderScheduler.deliverDue(ctx);
        assertEquals(1, shown().size());
    }

    @Test
    public void nothingIsShownWhenExamRemindersAreOff() {
        AppData d = Repository.get(ctx).data();
        exam(LocalDateTime.now().plusMinutes(59));
        d.settings.examReminders = false;
        d.settings.lastReminderCheck = System.currentTimeMillis() - 5 * 60_000L;
        ReminderScheduler.deliverDue(ctx);
        assertEquals(0, shown().size());
        assertEquals(0, ReminderScheduler.nextTrigger(d, System.currentTimeMillis()));
    }

    @Test
    public void finishedExamIsNotAnnounced() {
        AppData d = Repository.get(ctx).data();
        Exam e = exam(LocalDateTime.now().minusHours(2));
        e.customReminders = true;
        e.minutesBefore.add(0);
        d.settings.lastReminderCheck = System.currentTimeMillis() - 6 * 60 * 60_000L;
        ReminderScheduler.deliverDue(ctx);
        assertEquals(0, shown().size());
    }
}
