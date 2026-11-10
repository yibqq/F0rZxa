package com.mizan.gpa.notify;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms the lecture reminder after a reboot, an app update, or a clock/time-zone change. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED:
            case AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED:
                // Exam reminders that came due while the phone was off are shown now.
                if (Intent.ACTION_BOOT_COMPLETED.equals(action)) ReminderScheduler.deliverDue(ctx);
                ReminderScheduler.scheduleNext(ctx);
                com.mizan.gpa.widget.Widgets.updateAll(ctx);
                break;
            default:
                break;
        }
    }
}
