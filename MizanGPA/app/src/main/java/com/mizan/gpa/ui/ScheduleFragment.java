package com.mizan.gpa.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.AttendanceRecord;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.LectureSlot;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.notify.ReminderScheduler;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class ScheduleFragment extends BaseFragment {
    private static final String STATE_DAY = "day";
    /** 0 = whole week. */
    private int selectedDay;
    private View root;
    private View header;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        root = inflater.inflate(R.layout.fragment_list, container, false);
        selectedDay = state != null ? state.getInt(STATE_DAY) : Fmt.toCalendarDay(LocalDate.now().getDayOfWeek());
        ExtendedFloatingActionButton fab = root.findViewById(R.id.fab);
        fab.setText(R.string.add_lecture);
        fab.setOnClickListener(v -> addLecture());
        FrameLayout h = root.findViewById(R.id.header);
        header = inflater.inflate(R.layout.header_schedule, h, false);
        h.addView(header);
        buildDayChips();
        return root;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_DAY, selectedDay);
    }

    private void buildDayChips() {
        ChipGroup group = header.findViewById(R.id.days);
        group.removeAllViews();
        int today = Fmt.toCalendarDay(LocalDate.now().getDayOfWeek());
        addChip(group, 0, getString(R.string.whole_week));
        for (int day : Dialogs.orderedDays()) {
            String label = Fmt.dayName(requireContext(), day, true);
            addChip(group, day, day == today ? label + " •" : label);
        }
    }

    private void addChip(ChipGroup group, int day, String text) {
        Chip c = new Chip(requireContext());
        c.setId(View.generateViewId());
        c.setText(text);
        c.setCheckable(true);
        c.setCheckedIconVisible(false);
        c.setChecked(day == selectedDay);
        c.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                selectedDay = day;
                render();
            }
        });
        group.addView(c);
    }

    private void addLecture() {
        Semester cur = data().currentSemester();
        if (cur == null || cur.courses.isEmpty()) {
            Ui.snack(requireActivity(), getString(R.string.add_courses_first));
            return;
        }
        int day = selectedDay == 0 ? Fmt.toCalendarDay(LocalDate.now().getDayOfWeek()) : selectedDay;
        Dialogs.editLecture((AppCompatActivity) requireActivity(), cur, null, null, day);
    }

    private static final class Item {
        Course course;
        int index;
        LectureSlot slot;
    }

    @Override
    protected void render() {
        AppData d = data();
        Semester cur = d.currentSemester();
        LinearLayout list = root.findViewById(R.id.list);
        list.removeAllViews();
        boolean noCourses = cur == null || cur.courses.isEmpty();
        header.setVisibility(noCourses ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.fab).setVisibility(noCourses ? View.GONE : View.VISIBLE);
        if (noCourses) {
            Rows.empty(root, R.drawable.ic_calendar, R.string.empty_schedule_title, R.string.empty_schedule_no_courses,
                    cur == null ? 0 : R.string.open_semester,
                    cur == null ? null : v -> startActivity(SemesterActivity.intent(requireContext(), cur.id)));
            return;
        }

        // Next lecture banner
        ReminderScheduler.Next next = ReminderScheduler.nextLecture(d, System.currentTimeMillis());
        View banner = header.findViewById(R.id.next_card);
        if (next != null) {
            banner.setVisibility(View.VISIBLE);
            LocalDate day = Instant.ofEpochMilli(next.lectureAt).atZone(ZoneId.systemDefault()).toLocalDate();
            String when = day.equals(LocalDate.now()) ? getString(R.string.today)
                    : day.equals(LocalDate.now().plusDays(1)) ? getString(R.string.tomorrow)
                    : Fmt.dayName(requireContext(), next.slot.day, false);
            ((TextView) header.findViewById(R.id.next_title)).setText(next.course.displayName());
            String room = next.slot.room == null || next.slot.room.trim().isEmpty() ? "" : " · " + next.slot.room.trim();
            ((TextView) header.findViewById(R.id.next_time)).setText(when + " · " + Fmt.timeRange(requireContext(), next.slot.start, next.slot.end) + room);
            banner.setOnClickListener(v -> startActivity(CourseActivity.intent(requireContext(), next.course.id, CourseActivity.TAB_ATTENDANCE)));
        } else {
            banner.setVisibility(View.GONE);
        }

        List<Item> items = new ArrayList<>();
        for (int i = 0; i < cur.courses.size(); i++) {
            Course c = cur.courses.get(i);
            for (LectureSlot s : c.lectures) {
                if (selectedDay != 0 && s.day != selectedDay) continue;
                Item it = new Item();
                it.course = c;
                it.index = i;
                it.slot = s;
                items.add(it);
            }
        }
        int[] order = Dialogs.orderedDays();
        items.sort((a, b) -> {
            int da = indexOf(order, a.slot.day), db = indexOf(order, b.slot.day);
            return da != db ? Integer.compare(da, db) : Integer.compare(a.slot.start, b.slot.start);
        });
        if (items.isEmpty()) {
            Rows.empty(root, R.drawable.ic_calendar,
                    selectedDay == 0 ? R.string.empty_schedule_title : R.string.empty_day_title,
                    R.string.empty_schedule_text, R.string.add_lecture, v -> addLecture());
            return;
        }
        Rows.hideEmpty(root);
        int lastDay = -1;
        int today = Fmt.toCalendarDay(LocalDate.now().getDayOfWeek());
        int nowMin = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
        for (Item it : items) {
            if (selectedDay == 0 && it.slot.day != lastDay) {
                TextView t = (TextView) LayoutInflater.from(requireContext()).inflate(R.layout.view_day_header, list, false);
                t.setText(Fmt.dayName(requireContext(), it.slot.day, false) + (it.slot.day == today ? " · " + getString(R.string.today) : ""));
                list.addView(t);
                lastDay = it.slot.day;
            }
            list.addView(slotCard(list, it, it.slot.day == today && nowMin >= it.slot.start && nowMin < it.slot.end));
        }
    }

    private static int indexOf(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return 0;
    }

    private View slotCard(ViewGroup parent, Item it, boolean now) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.item_lecture, parent, false);
        int color = Ui.courseColor(it.course.color, it.index);
        Ui.roundedFill(v.findViewById(R.id.l_color), color, 3);
        ((TextView) v.findViewById(R.id.l_start)).setText(Fmt.time(requireContext(), it.slot.start));
        ((TextView) v.findViewById(R.id.l_end)).setText(Fmt.time(requireContext(), it.slot.end));
        ((TextView) v.findViewById(R.id.l_course)).setText(it.course.displayName());
        StringBuilder sub = new StringBuilder(Labels.kind(requireContext(), it.slot.kind));
        if (it.slot.room != null && !it.slot.room.trim().isEmpty()) sub.append(" · ").append(it.slot.room.trim());
        if (it.course.instructor != null && !it.course.instructor.trim().isEmpty()) sub.append(" · ").append(it.course.instructor.trim());
        ((TextView) v.findViewById(R.id.l_sub)).setText(sub);
        TextView badge = v.findViewById(R.id.l_badge);
        if (now) Ui.badge(badge, getString(R.string.now), Ui.attr(requireContext(), androidx.appcompat.R.attr.colorPrimary));
        else badge.setVisibility(View.GONE);
        Semester cur = data().currentSemester();
        v.setOnClickListener(x -> Dialogs.editLecture((AppCompatActivity) requireActivity(), cur, null, it.slot, it.slot.day));
        View menu = v.findViewById(R.id.l_menu);
        menu.setOnClickListener(x -> {
            PopupMenu p = new PopupMenu(requireContext(), menu);
            p.getMenu().add(0, 1, 0, R.string.mark_present_today);
            p.getMenu().add(0, 2, 0, R.string.mark_absent_today);
            p.getMenu().add(0, 3, 0, R.string.edit);
            p.getMenu().add(0, 4, 0, R.string.delete);
            p.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1:
                        Dialogs.quickRecord(requireContext(), it.course, AttendanceRecord.PRESENT);
                        Ui.snack(requireActivity(), getString(R.string.recorded_present, it.course.displayName()));
                        return true;
                    case 2:
                        Dialogs.quickRecord(requireContext(), it.course, AttendanceRecord.ABSENT);
                        Ui.snack(requireActivity(), getString(R.string.recorded_absent, it.course.displayName()));
                        return true;
                    case 3:
                        Dialogs.editLecture((AppCompatActivity) requireActivity(), cur, null, it.slot, it.slot.day);
                        return true;
                    case 4:
                        it.course.lectures.remove(it.slot);
                        repo().save();
                        return true;
                    default:
                        return false;
                }
            });
            p.show();
        });
        return v;
    }
}
