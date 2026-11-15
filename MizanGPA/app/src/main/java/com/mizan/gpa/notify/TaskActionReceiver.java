package com.mizan.gpa.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Task;

/** Marks a task as done from the button on its reminder. */
public class TaskActionReceiver extends BroadcastReceiver {
    static final String EXTRA_TASK = "task";
    static final String EXTRA_NOTIFICATION = "notification";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0));
        Repository repo = Repository.get(ctx);
        String id = intent.getStringExtra(EXTRA_TASK);
        Task t = id == null ? null : repo.data().findTask(id);
        if (t == null || t.done) return;
        t.done = true;
        repo.save();
    }
}
