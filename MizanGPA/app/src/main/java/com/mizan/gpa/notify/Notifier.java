package com.mizan.gpa.notify;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.mizan.gpa.R;
import com.mizan.gpa.calc.AttendanceCalculator;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.ui.CourseActivity;
import com.mizan.gpa.ui.MainActivity;
import com.mizan.gpa.util.Fmt;

public final class Notifier {
    public static final String CHANNEL_LECTURES = "lectures";
    public static final String CHANNEL_ATTENDANCE = "attendance";

    private Notifier() {}

    public static void createChannels(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel lectures = new NotificationChannel(CHANNEL_LECTURES,
                ctx.getString(R.string.channel_lectures), NotificationManager.IMPORTANCE_HIGH);
        lectures.setDescription(ctx.getString(R.string.channel_lectures_desc));
        NotificationChannel attendance = new NotificationChannel(CHANNEL_ATTENDANCE,
                ctx.getString(R.string.channel_attendance), NotificationManager.IMPORTANCE_HIGH);
        attendance.setDescription(ctx.getString(R.string.channel_attendance_desc));
        nm.createNotificationChannel(lectures);
        nm.createNotificationChannel(attendance);
    }

    public static boolean canPost(Context ctx) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled();
    }

    @SuppressWarnings("MissingPermission")
    public static void post(Context ctx, int id, NotificationCompat.Builder b) {
        if (!canPost(ctx)) return;
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build());
        } catch (SecurityException ignored) {
            // Permission revoked between the check and the call.
        }
    }

    public static PendingIntent openApp(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static PendingIntent openCourse(Context ctx, String courseId, int tab) {
        Intent i = CourseActivity.intent(ctx, courseId, tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, courseId.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Posts a deprivation warning if the course's attendance status got worse than {@code before}.
     */
    public static void attendanceChanged(Context ctx, Course c, Semester s, Settings st, AttendanceCalculator.Status before) {
        AttendanceCalculator.Stats after = AttendanceCalculator.compute(c, s, st);
        if (!st.attendanceAlerts) return;
        if (AttendanceCalculator.severity(after.status) <= AttendanceCalculator.severity(before)) return;
        if (after.status == AttendanceCalculator.Status.SAFE || after.status == AttendanceCalculator.Status.UNKNOWN) return;
        String title;
        String text;
        switch (after.status) {
            case DEPRIVED:
                title = ctx.getString(R.string.notif_deprived_title, c.displayName());
                text = ctx.getString(R.string.notif_deprived_text, after.counted, after.allowed, Fmt.pct(after.percent));
                break;
            case CRITICAL:
                title = ctx.getString(R.string.notif_critical_title, c.displayName());
                text = ctx.getString(R.string.notif_critical_text, Fmt.pct(after.percent));
                break;
            default:
                title = ctx.getString(R.string.notif_warning_title, c.displayName());
                text = ctx.getResources().getQuantityString(R.plurals.notif_warning_text, after.remaining, after.remaining, Fmt.pct(after.percent));
                break;
        }
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ATTENDANCE)
                .setSmallIcon(R.drawable.ic_stat_mizan)
                .setColor(ContextCompat.getColor(ctx, R.color.status_danger))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openCourse(ctx, c.id, CourseActivity.TAB_ATTENDANCE));
        post(ctx, ("att" + c.id).hashCode(), b);
    }
}
