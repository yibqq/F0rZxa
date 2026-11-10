package com.mizan.gpa.widget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.mizan.gpa.R;
import com.mizan.gpa.data.AttendanceRecord;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.ui.CourseActivity;
import com.mizan.gpa.ui.Dialogs;
import com.mizan.gpa.ui.MainActivity;

/**
 * Invisible activity behind widget taps: records attendance or opens the right screen, then finishes.
 * Widget list items can only share one click template, so every action goes through here.
 */
public class WidgetActionActivity extends Activity {
    static final String EXTRA_ACTION = "widgetAction";
    static final String EXTRA_COURSE = "course";
    static final String ACTION_OPEN_COURSE = "openCourse";
    static final String ACTION_PRESENT = "present";
    static final String ACTION_ABSENT = "absent";
    static final String ACTION_OPEN_SCHEDULE = "openSchedule";
    static final String ACTION_OPEN_ATTENDANCE = "openAttendance";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        String action = in.getStringExtra(EXTRA_ACTION);
        String courseId = in.getStringExtra(EXTRA_COURSE);
        Course c = courseId == null ? null : Repository.get(this).data().findCourse(courseId);
        if (ACTION_PRESENT.equals(action) || ACTION_ABSENT.equals(action)) {
            if (c != null) {
                boolean present = ACTION_PRESENT.equals(action);
                Dialogs.quickRecord(this, c, present ? AttendanceRecord.PRESENT : AttendanceRecord.ABSENT);
                Toast.makeText(this, getString(present ? R.string.recorded_present : R.string.recorded_absent, c.displayName()),
                        Toast.LENGTH_SHORT).show();
            }
        } else if (ACTION_OPEN_COURSE.equals(action) && c != null) {
            startActivity(CourseActivity.intent(this, c.id, CourseActivity.TAB_ATTENDANCE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        } else {
            int tab = ACTION_OPEN_SCHEDULE.equals(action) ? R.id.tab_schedule : R.id.tab_attendance;
            startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_TAB, tab)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
