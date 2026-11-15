package com.mizan.gpa.widget;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import androidx.core.content.ContextCompat;

import com.mizan.gpa.R;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Task;
import com.mizan.gpa.ui.TaskUi;
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
        private final List<Task> exams = new ArrayList<>();

        public Factory(Context ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            exams.clear();
            exams.addAll(TaskUi.upcoming(Repository.get(ctx).data(), LocalDateTime.now()));
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
            Task e = exams.get(position);
            AppData d = Repository.get(ctx).data();
            LocalDateTime now = LocalDateTime.now();
            r.setInt(R.id.e_bar, "setColorFilter", TaskUi.color(ctx, d, e));
            r.setTextViewText(R.id.e_day, String.valueOf(e.localDate().getDayOfMonth()));
            r.setTextViewText(R.id.e_month, e.localDate().getMonth().getDisplayName(TextStyle.SHORT, Fmt.locale(ctx)));
            String course = TaskUi.courseName(d, e);
            r.setTextViewText(R.id.e_course, TaskUi.title(ctx, e));
            StringBuilder detail = new StringBuilder(TaskUi.categoryLabel(ctx, e.category));
            if (course != null) detail.append(" · ").append(course);
            if (e.hasTime()) detail.append(" · ").append(Fmt.time(ctx, e.time));
            r.setTextViewText(R.id.e_detail, detail);
            r.setTextViewText(R.id.e_badge, TaskUi.countdown(ctx, e, now, true));
            TaskUi.State state = TaskUi.state(e, now);
            long days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), e.localDate());
            r.setTextColor(R.id.e_badge, state == TaskUi.State.OVERDUE || days <= 1 ? ContextCompat.getColor(ctx, R.color.status_danger)
                    : days <= 3 ? ContextCompat.getColor(ctx, R.color.status_warning) : ContextCompat.getColor(ctx, R.color.widget_text));
            Intent fill = new Intent().putExtra(WidgetActionActivity.EXTRA_ACTION,
                    e.courseId != null ? WidgetActionActivity.ACTION_OPEN_COURSE_EXAMS : WidgetActionActivity.ACTION_OPEN_EXAMS);
            if (e.courseId != null) fill.putExtra(WidgetActionActivity.EXTRA_COURSE, e.courseId);
            r.setOnClickFillInIntent(R.id.e_root, fill);
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
            return position < exams.size() ? exams.get(position).id.hashCode() : position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
