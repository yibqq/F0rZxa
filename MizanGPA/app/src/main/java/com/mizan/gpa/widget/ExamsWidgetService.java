package com.mizan.gpa.widget;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import androidx.core.content.ContextCompat;

import com.mizan.gpa.R;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.ui.Exams;
import com.mizan.gpa.util.Fmt;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;

public class ExamsWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    public static final class Factory implements RemoteViewsFactory {
        private final Context ctx;
        private final List<Exams.Entry> exams = new ArrayList<>();

        public Factory(Context ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            exams.clear();
            exams.addAll(Exams.upcoming(Repository.get(ctx).data(), LocalDateTime.now()));
        }

        @Override
        public void onDestroy() {
            exams.clear();
        }

        @Override
        public int getCount() {
            return exams.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            RemoteViews r = new RemoteViews(ctx.getPackageName(), R.layout.widget_exam_row);
            if (position >= exams.size()) return r;
            Exams.Entry e = exams.get(position);
            LocalDateTime now = LocalDateTime.now();
            r.setInt(R.id.e_bar, "setColorFilter", e.color());
            r.setTextViewText(R.id.e_day, String.valueOf(e.start.getDayOfMonth()));
            r.setTextViewText(R.id.e_month, e.start.getMonth().getDisplayName(TextStyle.SHORT, Fmt.locale(ctx)));
            r.setTextViewText(R.id.e_course, e.course.displayName());
            String detail = Exams.title(ctx, e.exam) + " · " + Fmt.time(ctx, e.exam.start);
            if (e.exam.room != null && !e.exam.room.trim().isEmpty()) detail += " · " + e.exam.room.trim();
            r.setTextViewText(R.id.e_detail, detail);
            r.setTextViewText(R.id.e_badge, Exams.countdown(ctx, e.exam, now, true));
            long days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), e.start.toLocalDate());
            r.setTextColor(R.id.e_badge, days <= 1 ? ContextCompat.getColor(ctx, R.color.status_danger)
                    : days <= 3 ? ContextCompat.getColor(ctx, R.color.status_warning) : ContextCompat.getColor(ctx, R.color.widget_text));
            r.setOnClickFillInIntent(R.id.e_root, new Intent()
                    .putExtra(WidgetActionActivity.EXTRA_ACTION, WidgetActionActivity.ACTION_OPEN_COURSE_EXAMS)
                    .putExtra(WidgetActionActivity.EXTRA_COURSE, e.course.id));
            return r;
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
            return position < exams.size() ? exams.get(position).exam.id.hashCode() : position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
