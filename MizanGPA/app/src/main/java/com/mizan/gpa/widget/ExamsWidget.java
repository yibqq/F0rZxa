package com.mizan.gpa.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.ui.Exams;

import java.time.LocalDateTime;

/** Home-screen widget: upcoming exams with a countdown for each. */
public class ExamsWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx, id));
        m.notifyAppWidgetViewDataChanged(ids, R.id.w_list);
    }

    public static RemoteViews build(Context ctx, int widgetId) {
        AppData d = Repository.get(ctx).data();
        Semester cur = d.currentSemester();
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_exams);
        v.setInt(R.id.w_icon_bg, "setColorFilter", Widgets.accent(ctx));
        v.setTextViewText(R.id.w_subtitle, cur == null ? "" : cur.name);
        int count = Exams.upcoming(d, LocalDateTime.now()).size();
        v.setViewVisibility(R.id.w_badge, count > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
        v.setTextViewText(R.id.w_badge, ctx.getResources().getQuantityString(R.plurals.exams_count, count, count));

        v.setRemoteAdapter(R.id.w_list, Widgets.serviceIntent(ctx, ExamsWidgetService.class, widgetId));
        v.setEmptyView(R.id.w_list, R.id.w_empty);
        Intent template = new Intent(ctx, WidgetActionActivity.class);
        v.setPendingIntentTemplate(R.id.w_list, PendingIntent.getActivity(ctx, 7500 + widgetId, template,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
        Intent open = new Intent(ctx, WidgetActionActivity.class)
                .putExtra(WidgetActionActivity.EXTRA_ACTION, WidgetActionActivity.ACTION_OPEN_EXAMS);
        PendingIntent openApp = PendingIntent.getActivity(ctx, 7400 + widgetId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_header, openApp);
        v.setOnClickPendingIntent(R.id.w_empty, openApp);
        return v;
    }
}
