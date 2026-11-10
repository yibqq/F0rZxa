package com.mizan.gpa.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;

/** Home-screen widget: the whole week's lectures grouped by day, starting with today. */
public class ScheduleWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx, id));
        // The "today" grouping depends on the date, so refresh the list as well.
        m.notifyAppWidgetViewDataChanged(ids, R.id.w_list);
    }

    public static RemoteViews build(Context ctx, int widgetId) {
        AppData d = Repository.get(ctx).data();
        Semester cur = d.currentSemester();
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_schedule);
        v.setInt(R.id.w_icon_bg, "setColorFilter", Widgets.accent(ctx));
        v.setTextViewText(R.id.w_subtitle, cur == null ? "" : cur.name);
        int total = 0;
        if (cur != null) for (Course c : cur.courses) total += c.lectures.size();
        v.setViewVisibility(R.id.w_badge, total > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
        v.setTextViewText(R.id.w_badge, ctx.getResources().getQuantityString(R.plurals.widget_lectures_week, total, total));

        v.setRemoteAdapter(R.id.w_list, Widgets.serviceIntent(ctx, ScheduleWidgetService.class, widgetId));
        v.setEmptyView(R.id.w_list, R.id.w_empty);
        Intent template = new Intent(ctx, WidgetActionActivity.class);
        v.setPendingIntentTemplate(R.id.w_list, PendingIntent.getActivity(ctx, 7300 + widgetId, template,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
        Intent open = new Intent(ctx, WidgetActionActivity.class)
                .putExtra(WidgetActionActivity.EXTRA_ACTION, WidgetActionActivity.ACTION_OPEN_SCHEDULE);
        PendingIntent openApp = PendingIntent.getActivity(ctx, 7200 + widgetId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_header, openApp);
        v.setOnClickPendingIntent(R.id.w_empty, openApp);
        return v;
    }
}
