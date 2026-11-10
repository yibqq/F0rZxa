package com.mizan.gpa.widget;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.LectureSlot;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.ui.Labels;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class ScheduleWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    /** Rows are either a day header or a lecture; days start from today and wrap around the week. */
    public static final class Factory implements RemoteViewsFactory {
        private static final int TYPE_DAY = 0, TYPE_LECTURE = 1;

        private static final class Row {
            int type;
            int day;
            int count;
            Course course;
            int color;
            LectureSlot slot;
        }

        private final Context ctx;
        private final List<Row> rows = new ArrayList<>();
        private int today;

        public Factory(Context ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            rows.clear();
            AppData d = Repository.get(ctx).data();
            Semester cur = d.currentSemester();
            if (cur == null) return;
            today = Fmt.toCalendarDay(LocalDate.now().getDayOfWeek());
            for (int offset = 0; offset < 7; offset++) {
                int day = ((today - 1 + offset) % 7) + 1;
                List<Row> lectures = new ArrayList<>();
                for (int i = 0; i < cur.courses.size(); i++) {
                    Course c = cur.courses.get(i);
                    for (LectureSlot s : c.lectures) {
                        if (s.day != day) continue;
                        Row r = new Row();
                        r.type = TYPE_LECTURE;
                        r.day = day;
                        r.course = c;
                        r.slot = s;
                        r.color = Ui.courseColor(c.color, i);
                        lectures.add(r);
                    }
                }
                if (lectures.isEmpty()) continue;
                lectures.sort((a, b) -> Integer.compare(a.slot.start, b.slot.start));
                Row header = new Row();
                header.type = TYPE_DAY;
                header.day = day;
                header.count = lectures.size();
                rows.add(header);
                rows.addAll(lectures);
            }
        }

        @Override
        public void onDestroy() {
            rows.clear();
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position >= rows.size()) return new RemoteViews(ctx.getPackageName(), R.layout.widget_schedule_row);
            Row row = rows.get(position);
            if (row.type == TYPE_DAY) {
                RemoteViews r = new RemoteViews(ctx.getPackageName(), R.layout.widget_schedule_day);
                r.setTextViewText(R.id.d_name, Fmt.dayName(ctx, row.day, false));
                r.setViewVisibility(R.id.d_today, row.day == today ? View.VISIBLE : View.GONE);
                r.setTextViewText(R.id.d_count, ctx.getResources().getQuantityString(R.plurals.sessions, row.count, row.count));
                return r;
            }
            RemoteViews r = new RemoteViews(ctx.getPackageName(), R.layout.widget_schedule_row);
            r.setInt(R.id.s_bar, "setColorFilter", row.color);
            r.setTextViewText(R.id.s_start, Fmt.time(ctx, row.slot.start));
            r.setTextViewText(R.id.s_end, Fmt.time(ctx, row.slot.end));
            r.setTextViewText(R.id.s_course, row.course.displayName());
            StringBuilder sub = new StringBuilder(Labels.kind(ctx, row.slot.kind));
            if (row.slot.room != null && !row.slot.room.trim().isEmpty()) sub.append(" · ").append(row.slot.room.trim());
            r.setTextViewText(R.id.s_room, sub);
            int nowMin = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
            boolean isNow = row.day == today && nowMin >= row.slot.start && nowMin < row.slot.end;
            r.setViewVisibility(R.id.s_now, isNow ? View.VISIBLE : View.GONE);
            r.setTextViewText(R.id.s_now, ctx.getString(R.string.now));
            r.setOnClickFillInIntent(R.id.s_root, new Intent()
                    .putExtra(WidgetActionActivity.EXTRA_ACTION, WidgetActionActivity.ACTION_OPEN_COURSE)
                    .putExtra(WidgetActionActivity.EXTRA_COURSE, row.course.id));
            return r;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public long getItemId(int position) {
            if (position >= rows.size()) return position;
            Row r = rows.get(position);
            return r.type == TYPE_DAY ? -r.day : (r.slot.id.hashCode() & 0xFFFFFFFFL);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
