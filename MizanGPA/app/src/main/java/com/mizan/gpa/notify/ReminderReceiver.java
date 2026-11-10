package com.mizan.gpa.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.mizan.gpa.widget.Widgets;

/** Fired by the reminder alarm: shows what is due, refreshes the widgets and arms the next alarm. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        ReminderScheduler.deliverDue(ctx);
        ReminderScheduler.scheduleNext(ctx);
        Widgets.updateAll(ctx);
    }
}
