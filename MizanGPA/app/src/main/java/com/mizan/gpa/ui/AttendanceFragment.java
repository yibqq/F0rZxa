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

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.mizan.gpa.R;
import com.mizan.gpa.calc.AttendanceCalculator;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.AttendanceRecord;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.util.ArrayList;
import java.util.List;

public class AttendanceFragment extends BaseFragment {
    private View root;
    private TextView headerText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        root = inflater.inflate(R.layout.fragment_list, container, false);
        root.findViewById(R.id.fab).setVisibility(View.GONE);
        FrameLayout h = root.findViewById(R.id.header);
        headerText = (TextView) inflater.inflate(R.layout.view_caption, h, false);
        h.addView(headerText);
        return root;
    }

    @Override
    protected void render() {
        AppData d = data();
        Settings st = d.settings;
        Semester cur = d.currentSemester();
        LinearLayout list = root.findViewById(R.id.list);
        list.removeAllViews();
        List<Course> active = new ArrayList<>();
        if (cur != null) for (Course c : cur.courses) if (!c.isCompleted()) active.add(c);
        if (cur == null || active.isEmpty()) {
            headerText.setVisibility(View.GONE);
            Rows.empty(root, R.drawable.ic_event_available, R.string.empty_attendance_title, R.string.empty_attendance_text,
                    cur == null ? 0 : R.string.open_semester,
                    cur == null ? null : v -> startActivity(SemesterActivity.intent(requireContext(), cur.id)));
            return;
        }
        Rows.hideEmpty(root);
        headerText.setVisibility(View.VISIBLE);
        headerText.setText(getString(st.deprivedOnReach ? R.string.attendance_rule_reach : R.string.attendance_rule_exceed,
                Fmt.pct(st.absenceLimit)));
        // Most at-risk courses first.
        active.sort((a, b) -> Integer.compare(
                AttendanceCalculator.severity(AttendanceCalculator.compute(b, cur, st).status),
                AttendanceCalculator.severity(AttendanceCalculator.compute(a, cur, st).status)));
        for (Course c : active) list.addView(card(list, c, cur, st));
    }

    private View card(ViewGroup parent, Course c, Semester cur, Settings st) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.item_attendance_course, parent, false);
        AttendanceCalculator.Stats s = AttendanceCalculator.compute(c, cur, st);
        int color = Ui.statusColor(requireContext(), s.status);
        Ui.dot(v.findViewById(R.id.att_dot), Ui.courseColor(c.color, cur.courses.indexOf(c)));
        ((TextView) v.findViewById(R.id.att_name)).setText(c.displayName());
        Ui.badge(v.findViewById(R.id.att_status), Ui.statusLabel(requireContext(), s.status), color);
        TextView pct = v.findViewById(R.id.att_percent);
        LinearProgressIndicator bar = v.findViewById(R.id.att_progress);
        TextView limit = v.findViewById(R.id.att_limit);
        TextView detail = v.findViewById(R.id.att_detail);
        if (s.hasTotal()) {
            pct.setText(Fmt.pct(s.percent));
            pct.setTextColor(color);
            limit.setText(getString(R.string.of_limit, Fmt.pct(s.limitPercent)));
            bar.setMax(1000);
            bar.setProgressCompat((int) Math.min(1000, s.percent / s.limitPercent * 1000), true);
            detail.setText(remainingText(s) + "  ·  " + getString(R.string.absences_of_allowed, s.counted, s.allowed));
        } else {
            pct.setText(String.valueOf(s.counted));
            pct.setTextColor(color);
            limit.setText(R.string.absences_label);
            bar.setVisibility(View.GONE);
            detail.setText(R.string.set_total_lectures_hint);
        }
        bar.setIndicatorColor(color);
        bar.setTrackColor(Ui.withAlpha(color, 0.15f));
        v.setOnClickListener(x -> startActivity(CourseActivity.intent(requireContext(), c.id, CourseActivity.TAB_ATTENDANCE)));
        v.findViewById(R.id.att_present).setOnClickListener(x -> quick(c, AttendanceRecord.PRESENT));
        v.findViewById(R.id.att_absent).setOnClickListener(x -> quick(c, AttendanceRecord.ABSENT));
        v.findViewById(R.id.att_more).setOnClickListener(x ->
                Dialogs.editRecord((AppCompatActivity) requireActivity(), c, null, AttendanceRecord.LATE));
        return v;
    }

    String remainingText(AttendanceCalculator.Stats s) {
        return remaining(this, s);
    }

    static String remaining(androidx.fragment.app.Fragment f, AttendanceCalculator.Stats s) {
        switch (s.status) {
            case DEPRIVED: return f.getString(R.string.remaining_deprived);
            case CRITICAL: return f.getString(R.string.remaining_none);
            case UNKNOWN: return f.getString(R.string.set_total_lectures_hint);
            default: return f.getResources().getQuantityString(R.plurals.remaining_absences, s.remaining, s.remaining);
        }
    }

    private void quick(Course c, String status) {
        AttendanceRecord r = Dialogs.quickRecord(requireContext(), c, status);
        root.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                ? android.view.HapticFeedbackConstants.CONFIRM : android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        String msg = getString(AttendanceRecord.PRESENT.equals(status) ? R.string.recorded_present : R.string.recorded_absent, c.displayName());
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG)
                .setAnchorView(requireActivity().findViewById(R.id.nav) instanceof com.google.android.material.bottomnavigation.BottomNavigationView
                        ? requireActivity().findViewById(R.id.nav) : null)
                .setAction(R.string.undo, x -> {
                    c.attendance.remove(r);
                    repo().save();
                })
                .show();
    }
}
