package com.mizan.gpa.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.RemoteViews;

import com.mizan.gpa.R;
import com.mizan.gpa.calc.GpaCalculator;
import com.mizan.gpa.calc.GradeResolver;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.ui.MainActivity;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

/** Small home-screen widget: the cumulative GPA on a gauge, with the expected semester GPA below. */
public class GpaWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx, id));
    }

    public static RemoteViews build(Context ctx, int widgetId) {
        AppData d = Repository.get(ctx).data();
        double max = d.settings.scale.maxPoints();
        GpaCalculator.Totals cum = GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL);
        GpaCalculator.Totals shown = cum;
        boolean expectedOnly = false;
        if (Double.isNaN(cum.gpa())) {
            // No official grades yet: show the expected GPA instead.
            shown = GpaCalculator.cumulative(d, GradeResolver.Mode.EXPECTED);
            expectedOnly = !Double.isNaN(shown.gpa());
        }
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_gpa);
        v.setImageViewBitmap(R.id.g_ring, ring(ctx, shown.gpa(), max, Widgets.accent(ctx)));
        v.setTextViewText(R.id.g_value, (expectedOnly ? "≈" : "") + Fmt.gpa(shown.gpa()));
        v.setTextViewText(R.id.g_max, ctx.getString(R.string.out_of_fmt, Fmt.gpa(max)));
        Semester cur = d.currentSemester();
        String sem = Fmt.DASH;
        if (cur != null) {
            GpaCalculator.Totals off = GpaCalculator.semester(cur, d.settings, GradeResolver.Mode.OFFICIAL);
            GpaCalculator.Totals exp = GpaCalculator.semester(cur, d.settings, GradeResolver.Mode.EXPECTED);
            boolean projected = exp.credits > off.credits + 1e-9 && !Double.isNaN(exp.gpa());
            sem = projected ? "≈" + Fmt.gpa(exp.gpa()) : Fmt.gpa(off.gpa());
        }
        v.setTextViewText(R.id.g_semester, ctx.getString(R.string.widget_gpa_semester, Fmt.ltr(sem)));
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(android.R.id.background, PendingIntent.getActivity(ctx, 7600 + widgetId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    /** Draws the 270° gauge used throughout the app. */
    static Bitmap ring(Context ctx, double gpa, double max, int color) {
        int size = Ui.dp(ctx, 120);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float stroke = size * 0.09f;
        RectF oval = new RectF(stroke, stroke, size - stroke, size - stroke);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(stroke);
        p.setColor(Ui.withAlpha(color, 0.18f));
        c.drawArc(oval, 135, 270, false, p);
        if (!Double.isNaN(gpa) && max > 0) {
            p.setColor(color);
            c.drawArc(oval, 135, (float) (270 * Math.max(0.01, Math.min(1, gpa / max))), false, p);
        }
        return b;
    }
}
