package com.mizan.gpa.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.notify.ReminderScheduler;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** One-row widget: the lecture in progress or the next one, with present/absent buttons. */
public class NextLectureWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx, id));
    }

    public static RemoteViews build(Context ctx, int widgetId) {
        AppData d = Repository.get(ctx).data();
        Semester cur = d.currentSemester();
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_next);
        long now = System.currentTimeMillis();
        // A lecture that started less than its length ago is still "now".
        ReminderScheduler.Next next = null;
        boolean running = false;
        if (cur != null) {
            int today = Fmt.toCalendarDay(LocalDate.now().getDayOfWeek());
            int minute = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
            for (com.mizan.gpa.data.Course c : cur.courses) {
                for (com.mizan.gpa.data.LectureSlot s : c.lectures) {
                    if (s.day == today && minute >= s.start && minute < s.end) {
                        next = new ReminderScheduler.Next();
                        next.course = c;
                        next.slot = s;
                        running = true;
                    }
                }
            }
        }
        if (next == null) next = ReminderScheduler.nextLecture(d, now);

        Intent open = new Intent(ctx, WidgetActionActivity.class)
                .putExtra(WidgetActionActivity.EXTRA_ACTION, WidgetActionActivity.ACTION_OPEN_SCHEDULE);
        if (next == null) {
            v.setTextViewText(R.id.n_label, ctx.getString(R.string.next_lecture));
            v.setTextViewText(R.id.n_course, ctx.getString(R.string.widget_next_none));
            v.setTextViewText(R.id.n_time, cur == null ? "" : cur.name);
            v.setInt(R.id.n_bar, "setColorFilter", Widgets.accent(ctx));
            v.setViewVisibility(R.id.n_present, View.GONE);
            v.setViewVisibility(R.id.n_absent, View.GONE);
        } else {
            int index = cur == null ? 0 : cur.courses.indexOf(next.course);
            String when;
            if (running) {
                when = ctx.getString(R.string.now);
            } else {
                LocalDate day = Instant.ofEpochMilli(next.lectureAt).atZone(ZoneId.systemDefault()).toLocalDate();
                when = day.equals(LocalDate.now()) ? ctx.getString(R.string.today)
                        : day.equals(LocalDate.now().plusDays(1)) ? ctx.getString(R.string.tomorrow)
                        : Fmt.dayName(ctx, next.slot.day, false);
            }
            String time = when + " · " + Fmt.timeRange(ctx, next.slot.start, next.slot.end);
            if (next.slot.room != null && !next.slot.room.trim().isEmpty()) time += " · " + next.slot.room.trim();
            v.setTextViewText(R.id.n_label, ctx.getString(running ? R.string.widget_next_running : R.string.next_lecture));
            v.setTextViewText(R.id.n_course, next.course.displayName());
            v.setTextViewText(R.id.n_time, time);
            v.setInt(R.id.n_bar, "setColorFilter", Ui.courseColor(next.course.color, index));
            v.setViewVisibility(R.id.n_present, View.VISIBLE);
            v.setViewVisibility(R.id.n_absent, View.VISIBLE);
            v.setOnClickPendingIntent(R.id.n_present, action(ctx, widgetId, 1, next.course.id, WidgetActionActivity.ACTION_PRESENT));
            v.setOnClickPendingIntent(R.id.n_absent, action(ctx, widgetId, 2, next.course.id, WidgetActionActivity.ACTION_ABSENT));
        }
        v.setOnClickPendingIntent(R.id.n_texts, PendingIntent.getActivity(ctx, 7700 + widgetId * 4, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    private static PendingIntent action(Context ctx, int widgetId, int slot, String courseId, String action) {
        Intent i = new Intent(ctx, WidgetActionActivity.class)
                .putExtra(WidgetActionActivity.EXTRA_ACTION, action)
                .putExtra(WidgetActionActivity.EXTRA_COURSE, courseId);
        return PendingIntent.getActivity(ctx, 7700 + widgetId * 4 + slot, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
