package com.mizan.gpa.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

import com.mizan.gpa.calc.AttendanceCalculator;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.AttendanceRecord;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.util.Fmt;

/** Records attendance from the buttons on a lecture reminder. */
public class AttendanceActionReceiver extends BroadcastReceiver {
    static final String EXTRA_COURSE = "course";
    static final String EXTRA_STATUS = "status";
    static final String EXTRA_NOTIFICATION = "notification";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Repository repo = Repository.get(ctx);
        AppData d = repo.data();
        String courseId = intent.getStringExtra(EXTRA_COURSE);
        String status = intent.getStringExtra(EXTRA_STATUS);
        NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0));
        Course c = courseId == null ? null : d.findCourse(courseId);
        if (c == null || status == null) return;
        Semester s = d.semesterOf(c.id);
        AttendanceCalculator.Status before = AttendanceCalculator.compute(c, s, d.settings).status;
        AttendanceRecord r = new AttendanceRecord();
        r.date = Fmt.today();
        r.status = status;
        c.attendance.add(r);
        repo.save();
        Notifier.attendanceChanged(ctx, c, s, d.settings, before);
    }
}
