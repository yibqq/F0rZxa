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
import com.mizan.gpa.ui.views.TimetableView;
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
        fab.setText("exams".equals(data().settings.scheduleView) ? R.string.add_exam : R.string.add_lecture);
        fab.setOnClickListener(v -> {
            if ("exams".equals(data().settings.scheduleView)) addExam();
            else addLecture();
        });
        FrameLayout h = root.findViewById(R.id.header);
        header = inflater.inflate(R.layout.header_schedule, h, false);
        h.addView(header);
        buildDayChips();
        com.google.android.material.button.MaterialButtonToggleGroup mode = header.findViewById(R.id.view_mode);
        String current = data().settings.scheduleView;
        mode.check("list".equals(current) ? R.id.mode_list : "exams".equals(current) ? R.id.mode_exams : R.id.mode_grid);
        mode.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            String value = id == R.id.mode_list ? "list" : id == R.id.mode_exams ? "exams" : "grid";
            if (!value.equals(data().settings.scheduleView)) {
                data().settings.scheduleView = value;
                repo().save();
            }
        });
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

    /** Shows the exams list; used when the tab is opened from a shortcut, widget or the dashboard. */
    public void showExams() {
        if (header == null) return;
        ((com.google.android.material.button.MaterialButtonToggleGroup) header.findViewById(R.id.view_mode)).check(R.id.mode_exams);
    }

    private void addExam() {
        Semester cur = data().currentSemester();
        if (cur == null || cur.courses.isEmpty()) {
            Ui.snack(requireActivity(), getString(R.string.add_courses_first));
            return;
        }
        Dialogs.editExam((AppCompatActivity) requireActivity(), cur, null, null);
    }

    private void renderExams(LinearLayout list, Semester cur) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<Exams.Entry> all = Exams.of(cur, now);
        if (all.isEmpty()) {
            Rows.empty(root, R.drawable.ic_exam, R.string.empty_exams_title, R.string.empty_exams_text,
                    R.string.add_exam, v -> addExam());
            return;
        }
        Rows.hideEmpty(root);
        boolean pastHeader = false, upcomingHeader = false;
        for (Exams.Entry e : all) {
            boolean past = Exams.isPast(e.exam, now);
            if (!past && !upcomingHeader) {
                list.addView(sectionHeader(list, getString(R.string.exams_upcoming)));
                upcomingHeader = true;
            }
            if (past && !pastHeader) {
                list.addView(sectionHeader(list, getString(R.string.exams_past)));
                pastHeader = true;
            }
            list.addView(Rows.examCard(list, (AppCompatActivity) requireActivity(), cur, e, true));
        }
    }

    private View sectionHeader(ViewGroup parent, String text) {
        TextView t = (TextView) LayoutInflater.from(requireContext()).inflate(R.layout.view_day_header, parent, false);
        t.setText(text);
        return t;
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

        boolean exams = "exams".equals(d.settings.scheduleView);
        boolean grid = !exams && !"list".equals(d.settings.scheduleView);
        ExtendedFloatingActionButton fab = root.findViewById(R.id.fab);
        fab.setText(exams ? R.string.add_exam : R.string.add_lecture);
        fab.extend();
        header.findViewById(R.id.days_scroll).setVisibility(grid || exams ? View.GONE : View.VISIBLE);
        if (exams) {
            Rows.hideEmpty(root);
            renderExams(list, cur);
            return;
        }
        if (grid) {
            renderGrid(list, cur);
            return;
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

    private void renderGrid(LinearLayout list, Semester cur) {
        List<TimetableView.Block> blocks = new ArrayList<>();
        for (int i = 0; i < cur.courses.size(); i++) {
            Course c = cur.courses.get(i);
            for (LectureSlot s : c.lectures) {
                // The card height already shows the duration, so narrow columns only need the start time.
                String sub = Fmt.ltr(Fmt.clock(s.start))
                        + (s.room == null || s.room.trim().isEmpty() ? "" : "\n" + s.room.trim());
                TimetableView.Block b = new TimetableView.Block(s.day, s.start, s.end, Ui.courseColor(c.color, i),
                        c.displayName(), sub, new Object[]{c, s});
                b.shortTitle = c.code == null ? null : c.code.trim();
                blocks.add(b);
            }
        }
        if (blocks.isEmpty()) {
            Rows.empty(root, R.drawable.ic_calendar, R.string.empty_schedule_title, R.string.empty_schedule_text,
                    R.string.add_lecture, v -> addLecture());
            return;
        }
        Rows.hideEmpty(root);
        com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(requireContext());
        card.setCardBackgroundColor(Ui.attr(requireContext(), com.google.android.material.R.attr.colorSurface));
        card.setStrokeWidth(0);
        card.setCardElevation(0);
        TimetableView tv = new TimetableView(requireContext());
        tv.setData(blocks, Dialogs.orderedDays(), Fmt.toCalendarDay(LocalDate.now().getDayOfWeek()));
        tv.setOnBlockClickListener(b -> {
            Object[] pair = (Object[]) b.tag;
            Course c = (Course) pair[0];
            LectureSlot s = (LectureSlot) pair[1];
            CharSequence[] actions = {getString(R.string.open_course), getString(R.string.mark_present_today),
                    getString(R.string.mark_absent_today), getString(R.string.edit_lecture)};
            Dialogs.show(new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(c.displayName())
                    .setItems(actions, (d, which) -> {
                        if (which == 0) {
                            startActivity(CourseActivity.intent(requireContext(), c.id, CourseActivity.TAB_ATTENDANCE));
                        } else if (which == 1 || which == 2) {
                            String st = which == 1 ? AttendanceRecord.PRESENT : AttendanceRecord.ABSENT;
                            Dialogs.quickRecord(requireContext(), c, st);
                            Ui.snack(requireActivity(), getString(which == 1 ? R.string.recorded_present : R.string.recorded_absent, c.displayName()));
                        } else {
                            Dialogs.editLecture((AppCompatActivity) requireActivity(), cur, null, s, s.day);
                        }
                    }));
        });
        tv.setContentDescription(getString(R.string.schedule_mode_grid));
        card.addView(tv);
        list.addView(card);
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
