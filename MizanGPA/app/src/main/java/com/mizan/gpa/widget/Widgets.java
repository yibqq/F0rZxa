package com.mizan.gpa.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.ContextThemeWrapper;

import com.google.android.material.color.MaterialColors;
import com.mizan.gpa.R;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.util.Themes;

/** Refreshes every home-screen widget after the data changes. */
public final class Widgets {
    private Widgets() {}

    public static void updateAll(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        if (m == null) return;
        try {
            int[] att = m.getAppWidgetIds(new ComponentName(ctx, AttendanceWidget.class));
            for (int id : att) m.updateAppWidget(id, AttendanceWidget.build(ctx, id));
            if (att.length > 0) m.notifyAppWidgetViewDataChanged(att, R.id.w_list);
            int[] sch = m.getAppWidgetIds(new ComponentName(ctx, ScheduleWidget.class));
            for (int id : sch) m.updateAppWidget(id, ScheduleWidget.build(ctx, id));
            if (sch.length > 0) m.notifyAppWidgetViewDataChanged(sch, R.id.w_list);
            int[] ex = m.getAppWidgetIds(new ComponentName(ctx, ExamsWidget.class));
            for (int id : ex) m.updateAppWidget(id, ExamsWidget.build(ctx, id));
            if (ex.length > 0) m.notifyAppWidgetViewDataChanged(ex, R.id.w_list);
            for (int id : m.getAppWidgetIds(new ComponentName(ctx, GpaWidget.class))) m.updateAppWidget(id, GpaWidget.build(ctx, id));
            for (int id : m.getAppWidgetIds(new ComponentName(ctx, NextLectureWidget.class))) m.updateAppWidget(id, NextLectureWidget.build(ctx, id));
        } catch (RuntimeException ignored) {
            // No widget host (e.g. during tests).
        }
    }

    /** The palette's primary color, so widgets match the app's chosen theme. */
    public static int accent(Context ctx) {
        Settings st = Repository.get(ctx).settings();
        ContextThemeWrapper themed = new ContextThemeWrapper(ctx, R.style.Theme_Mizan);
        Themes.Palette p = Themes.find(st.colorTheme);
        if (p.overlay != 0) themed.getTheme().applyStyle(p.overlay, true);
        return MaterialColors.getColor(themed, androidx.appcompat.R.attr.colorPrimary, 0xFF0B7A74);
    }

    /** A distinct data URI per widget so each one gets its own list adapter. */
    static Intent serviceIntent(Context ctx, Class<?> service, int widgetId) {
        Intent i = new Intent(ctx, service).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        i.setData(Uri.parse("mizan://widget/" + service.getSimpleName() + "/" + widgetId));
        return i;
    }
}
