package com.mizan.gpa.ui;

import android.view.View;
import android.widget.TextView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.mizan.gpa.R;
import com.mizan.gpa.calc.AttendanceCalculator;
import com.mizan.gpa.data.AttendanceRecord;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.util.ArrayList;
import java.util.List;

public class CourseAttendanceFragment extends CoursePageFragment {
    @Override
    protected int headerLayout() {
        return R.layout.header_attendance;
    }

    @Override
    protected void onCreated() {
        header.findViewById(R.id.a_present).setOnClickListener(v -> quick(AttendanceRecord.PRESENT));
        header.findViewById(R.id.a_absent).setOnClickListener(v -> quick(AttendanceRecord.ABSENT));
        header.findViewById(R.id.a_late).setOnClickListener(v -> quick(AttendanceRecord.LATE));
    }

    private void quick(String status) {
        Course c = course();
        if (c == null) return;
        AttendanceRecord r = Dialogs.quickRecord(requireContext(), c, status);
        Snackbar.make(root, getString(R.string.recorded_status, Labels.status(requireContext(), status)), Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> {
                    c.attendance.remove(r);
                    repo().save();
                }).show();
    }

    @Override
    protected void render() {
        Course c = course();
        Semester s = semester();
        if (c == null) return;
        Settings st = data().settings;
        AttendanceCalculator.Stats a = AttendanceCalculator.compute(c, s, st);
        int color = Ui.statusColor(requireContext(), a.status);

        TextView pct = header.findViewById(R.id.a_percent);
        LinearProgressIndicator bar = header.findViewById(R.id.a_progress);
        pct.setText(a.hasTotal() ? Fmt.pct(a.percent) : Fmt.DASH);
        Ui.badge(header.findViewById(R.id.a_status), Ui.statusLabel(requireContext(), a.status), color);
        bar.setMax(1000);
        bar.setProgressCompat(a.hasTotal() ? (int) Math.min(1000, a.percent / a.limitPercent * 1000) : 0, true);
        bar.setIndicatorColor(color);
        bar.setTrackColor(Ui.withAlpha(color, 0.18f));
        ((TextView) header.findViewById(R.id.a_remaining)).setText(AttendanceFragment.remaining(this, a));

        String totalText;
        if (!a.hasTotal()) totalText = getString(R.string.not_set);
        else if (a.totalFromSchedule) totalText = getString(R.string.total_from_schedule, a.total, c.lectures.size(), s == null ? 0 : s.weeks);
        else totalText = String.valueOf(a.total);
        Rows.kv(header.findViewById(R.id.a_row_total), getString(R.string.planned_lectures), totalText);
        Rows.kv(header.findViewById(R.id.a_row_counts), getString(R.string.absences_label),
                getString(R.string.counts_line, a.absent, a.late, a.excused, a.present));
        Rows.kv(header.findViewById(R.id.a_row_limit), getString(R.string.limit_label),
                a.hasTotal() ? getString(R.string.limit_line, Fmt.pct(a.limitPercent), a.allowed) : Fmt.pct(a.limitPercent));

        list.removeAllViews();
        List<AttendanceRecord> records = new ArrayList<>(c.attendance);
        records.sort((x, y) -> y.date.compareTo(x.date));
        showList(R.string.attendance_log, !records.isEmpty());
        if (records.isEmpty()) {
            Rows.empty(root, R.drawable.ic_event_available, R.string.empty_records_title, R.string.empty_records_text, 0, null);
        } else {
            Rows.hideEmpty(root);
        }
        for (AttendanceRecord r : records) {
            int rc = statusColor(r.status);
            StringBuilder sub = new StringBuilder(Fmt.date(requireContext(), r.date));
            if (r.sessions > 1) sub.append(" · ").append(getResources().getQuantityString(R.plurals.sessions, r.sessions, r.sessions));
            if (r.note != null && !r.note.trim().isEmpty()) sub.append(" · ").append(r.note.trim());
            View row = Rows.simple(list, Labels.statusIcon(r.status), rc, Labels.status(requireContext(), r.status), sub, null);
            rowMenu(row, () -> Dialogs.editRecord(activity(), c, r, r.status), () -> {
                c.attendance.remove(r);
                repo().save();
            });
            list.addView(row);
        }
    }

    private int statusColor(String status) {
        switch (status) {
            case AttendanceRecord.PRESENT: return androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_excellent);
            case AttendanceRecord.LATE: return androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_warning);
            case AttendanceRecord.EXCUSED: return androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_good);
            default: return androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_danger);
        }
    }
}
