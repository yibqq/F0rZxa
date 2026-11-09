package com.mizan.gpa.notify;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.AttendanceRecord;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.LectureSlot;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.ui.CourseActivity;
import com.mizan.gpa.util.Fmt;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        AppData d = Repository.get(ctx).data();
        String courseId = intent.getStringExtra(ReminderScheduler.EXTRA_COURSE);
        String slotId = intent.getStringExtra(ReminderScheduler.EXTRA_SLOT);
        long lectureAt = intent.getLongExtra(ReminderScheduler.EXTRA_LECTURE_AT, 0);
        Course c = courseId == null ? null : d.findCourse(courseId);
        LectureSlot slot = c == null ? null : c.findSlot(slotId);
        // Skip stale alarms (deleted lecture, or delivered long after the lecture started).
        boolean fresh = System.currentTimeMillis() < lectureAt + 30 * 60_000L;
        if (c != null && slot != null && fresh && d.settings.remindersEnabled) {
            show(ctx, c, slot, d.settings.reminderMinutes);
        }
        ReminderScheduler.scheduleNext(ctx);
    }

    static int notificationId(String courseId) {
        return ("lec" + courseId).hashCode();
    }

    private static void show(Context ctx, Course c, LectureSlot slot, int minutes) {
        int id = notificationId(c.id);
        String title = minutes > 0
                ? ctx.getResources().getQuantityString(R.plurals.reminder_title, minutes, c.displayName(), minutes)
                : ctx.getString(R.string.reminder_title_now, c.displayName());
        StringBuilder text = new StringBuilder(Fmt.timeRange(ctx, slot.start, slot.end));
        if (slot.room != null && !slot.room.trim().isEmpty()) text.append(" · ").append(slot.room.trim());
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, Notifier.CHANNEL_LECTURES)
                .setSmallIcon(R.drawable.ic_stat_mizan)
                .setColor(ContextCompat.getColor(ctx, R.color.brand_primary))
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(Notifier.openCourse(ctx, c.id, CourseActivity.TAB_ATTENDANCE))
                .addAction(R.drawable.ic_check, ctx.getString(R.string.action_mark_present),
                        action(ctx, c.id, AttendanceRecord.PRESENT, id))
                .addAction(R.drawable.ic_event_busy, ctx.getString(R.string.action_mark_absent),
                        action(ctx, c.id, AttendanceRecord.ABSENT, id));
        Notifier.post(ctx, id, b);
    }

    private static PendingIntent action(Context ctx, String courseId, String status, int notifId) {
        Intent i = new Intent(ctx, AttendanceActionReceiver.class)
                .putExtra(AttendanceActionReceiver.EXTRA_COURSE, courseId)
                .putExtra(AttendanceActionReceiver.EXTRA_STATUS, status)
                .putExtra(AttendanceActionReceiver.EXTRA_NOTIFICATION, notifId);
        return PendingIntent.getBroadcast(ctx, (courseId + status).hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
