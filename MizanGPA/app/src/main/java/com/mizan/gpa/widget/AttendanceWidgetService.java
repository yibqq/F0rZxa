package com.mizan.gpa.widget;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import androidx.core.content.ContextCompat;

import com.mizan.gpa.R;
import com.mizan.gpa.calc.AttendanceCalculator;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.util.ArrayList;
import java.util.List;

public class AttendanceWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    /** Builds one row per in-progress course of the current semester, most at-risk first. */
    public static final class Factory implements RemoteViewsFactory {
        private final Context ctx;
        private final List<Course> courses = new ArrayList<>();
        private final List<AttendanceCalculator.Stats> stats = new ArrayList<>();
        private final List<Integer> colors = new ArrayList<>();

        public Factory(Context ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            courses.clear();
            stats.clear();
            colors.clear();
            AppData d = Repository.get(ctx).data();
            Semester cur = d.currentSemester();
            if (cur == null) return;
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < cur.courses.size(); i++) if (!cur.courses.get(i).isCompleted()) order.add(i);
            order.sort((a, b) -> Integer.compare(
                    AttendanceCalculator.severity(AttendanceCalculator.compute(cur.courses.get(b), cur, d.settings).status),
                    AttendanceCalculator.severity(AttendanceCalculator.compute(cur.courses.get(a), cur, d.settings).status)));
            for (int i : order) {
                Course c = cur.courses.get(i);
                courses.add(c);
                stats.add(AttendanceCalculator.compute(c, cur, d.settings));
                colors.add(Ui.courseColor(c.color, i));
            }
        }

        @Override
        public void onDestroy() {
            courses.clear();
        }

        @Override
        public int getCount() {
            return courses.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            RemoteViews r = new RemoteViews(ctx.getPackageName(), R.layout.widget_attendance_row);
            if (position >= courses.size()) return r;
            Course c = courses.get(position);
            AttendanceCalculator.Stats s = stats.get(position);
            r.setInt(R.id.r_dot, "setColorFilter", colors.get(position));
            r.setTextViewText(R.id.r_name, c.displayName());
            int statusColor = Ui.statusColor(ctx, s.status);
            if (s.status == AttendanceCalculator.Status.UNKNOWN) statusColor = ContextCompat.getColor(ctx, R.color.widget_muted);
            r.setTextColor(R.id.r_percent, statusColor);
            r.setTextViewText(R.id.r_percent, s.hasTotal() ? Fmt.pct(s.percent) : String.valueOf(s.counted));
            String detail;
            if (!s.hasTotal()) detail = ctx.getString(R.string.course_absences_count, s.counted);
            else if (s.status == AttendanceCalculator.Status.DEPRIVED) detail = ctx.getString(R.string.status_deprived);
            else detail = ctx.getString(R.string.widget_att_detail, s.counted, s.allowed, Math.max(0, s.remaining));
            r.setTextViewText(R.id.r_detail, detail);
            int bar = s.status == AttendanceCalculator.Status.DEPRIVED || s.status == AttendanceCalculator.Status.CRITICAL ? R.id.r_bar_danger
                    : s.status == AttendanceCalculator.Status.WARNING ? R.id.r_bar_warn : R.id.r_bar_safe;
            for (int id : new int[]{R.id.r_bar_safe, R.id.r_bar_warn, R.id.r_bar_danger}) {
                r.setViewVisibility(id, id == bar && s.hasTotal() ? View.VISIBLE : View.GONE);
            }
            if (s.hasTotal()) r.setProgressBar(bar, 1000, (int) Math.min(1000, s.percent / s.limitPercent * 1000), false);
            r.setOnClickFillInIntent(R.id.r_root, fill(c, WidgetActionActivity.ACTION_OPEN_COURSE));
            r.setOnClickFillInIntent(R.id.r_present, fill(c, WidgetActionActivity.ACTION_PRESENT));
            r.setOnClickFillInIntent(R.id.r_absent, fill(c, WidgetActionActivity.ACTION_ABSENT));
            r.setContentDescription(R.id.r_present, ctx.getString(R.string.recorded_present, c.displayName()));
            r.setContentDescription(R.id.r_absent, ctx.getString(R.string.recorded_absent, c.displayName()));
            return r;
        }

        private static Intent fill(Course c, String action) {
            return new Intent().putExtra(WidgetActionActivity.EXTRA_ACTION, action)
                    .putExtra(WidgetActionActivity.EXTRA_COURSE, c.id);
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position < courses.size() ? courses.get(position).id.hashCode() : position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
