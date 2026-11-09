package com.mizan.gpa.ui;

import android.view.View;

import com.mizan.gpa.R;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.LectureSlot;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CourseLecturesFragment extends CoursePageFragment {
    @Override
    protected int headerLayout() {
        return 0;
    }

    @Override
    protected void render() {
        Course c = course();
        Semester s = semester();
        if (c == null || s == null) return;
        list.removeAllViews();
        showList(R.string.weekly_lectures, !c.lectures.isEmpty());
        if (c.lectures.isEmpty()) {
            Rows.empty(root, R.drawable.ic_calendar, R.string.empty_lectures_title, R.string.empty_lectures_text,
                    R.string.add_lecture, v -> Dialogs.editLecture(activity(), s, c, null, Fmt.toCalendarDay(LocalDate.now().getDayOfWeek())));
            return;
        }
        Rows.hideEmpty(root);
        int[] order = Dialogs.orderedDays();
        List<LectureSlot> slots = new ArrayList<>(c.lectures);
        slots.sort((a, b) -> {
            int da = pos(order, a.day), db = pos(order, b.day);
            return da != db ? Integer.compare(da, db) : Integer.compare(a.start, b.start);
        });
        int color = Ui.courseColor(c.color, s.courses.indexOf(c));
        for (LectureSlot slot : slots) {
            String sub = Fmt.timeRange(requireContext(), slot.start, slot.end) + " · " + Labels.kind(requireContext(), slot.kind)
                    + (slot.room == null || slot.room.trim().isEmpty() ? "" : " · " + slot.room.trim());
            View row = Rows.simple(list, R.drawable.ic_clock, color, Fmt.dayName(requireContext(), slot.day, false), sub, null);
            rowMenu(row, () -> Dialogs.editLecture(activity(), s, c, slot, slot.day), () -> {
                c.lectures.remove(slot);
                repo().save();
            });
            list.addView(row);
        }
        View note = Rows.simple(list, R.drawable.ic_info, Ui.attr(requireContext(), com.google.android.material.R.attr.colorOutline),
                getString(R.string.lectures_per_semester, c.lectures.size() * s.weeks), getString(R.string.lectures_per_semester_hint), null);
        note.setClickable(false);
        list.addView(note);
    }

    private static int pos(int[] order, int day) {
        for (int i = 0; i < order.length; i++) if (order[i] == day) return i;
        return 0;
    }
}
