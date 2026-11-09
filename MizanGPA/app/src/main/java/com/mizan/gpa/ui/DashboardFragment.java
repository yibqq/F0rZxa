package com.mizan.gpa.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mizan.gpa.R;
import com.mizan.gpa.calc.AttendanceCalculator;
import com.mizan.gpa.calc.GpaCalculator;
import com.mizan.gpa.calc.GradeResolver;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.export.SampleData;
import com.mizan.gpa.ui.views.GpaRingView;
import com.mizan.gpa.ui.views.TrendChartView;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.util.ArrayList;
import java.util.List;

public class DashboardFragment extends BaseFragment {
    private View root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        root = inflater.inflate(R.layout.fragment_dashboard, container, false);
        root.findViewById(R.id.welcome_add).setOnClickListener(v ->
                Dialogs.editSemester((AppCompatActivity) requireActivity(), null,
                        s -> startActivity(SemesterActivity.intent(requireContext(), s.id))));
        root.findViewById(R.id.welcome_sample).setOnClickListener(v -> {
            Repository.get(requireContext()).replace(SampleData.create(requireContext(), data().settings));
            Ui.snack(requireActivity(), getString(R.string.sample_loaded));
        });
        Ui.dot(root.findViewById(R.id.legend_bar), ContextCompat.getColor(requireContext(), R.color.chart_bar));
        Ui.dot(root.findViewById(R.id.legend_line), ContextCompat.getColor(requireContext(), R.color.chart_line));
        root.findViewById(R.id.hero).setOnClickListener(v -> ((MainActivity) requireActivity()).selectTab(R.id.tab_semesters));
        return root;
    }

    @Override
    protected void render() {
        AppData d = data();
        Settings st = d.settings;
        boolean empty = d.semesters.isEmpty();
        root.findViewById(R.id.welcome).setVisibility(empty ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.dash_content).setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;

        Semester cur = d.currentSemester();
        double max = st.scale.maxPoints();
        GpaCalculator.Totals cum = GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL);
        GpaCalculator.Totals cumExp = GpaCalculator.cumulative(d, GradeResolver.Mode.EXPECTED);
        GpaRingView ring = root.findViewById(R.id.ring);
        ring.setValue(cum.gpa(), max, getString(R.string.out_of_fmt, Fmt.gpa(max)));
        ((TextView) root.findViewById(R.id.hero_semester_name)).setText(cur != null
                ? getString(R.string.current_semester_fmt, cur.name) : "");

        String semText = Fmt.DASH;
        if (cur != null) {
            GpaCalculator.Totals off = GpaCalculator.semester(cur, st, GradeResolver.Mode.OFFICIAL);
            GpaCalculator.Totals exp = GpaCalculator.semester(cur, st, GradeResolver.Mode.EXPECTED);
            if (exp.credits > off.credits + 1e-9 && !Double.isNaN(exp.gpa())) semText = "≈ " + Fmt.gpa(exp.gpa());
            else semText = Fmt.gpa(off.gpa());
        }
        Rows.kv(root.findViewById(R.id.hero_row_semester), getString(R.string.semester_gpa), semText);
        Rows.kv(root.findViewById(R.id.hero_row_expected), getString(R.string.expected_cumulative), Fmt.gpa(cumExp.gpa()));
        Rows.kv(root.findViewById(R.id.hero_row_credits), getString(R.string.completed_credits), Fmt.num(cum.credits));

        // Tiles
        int courses = cur == null ? 0 : cur.courses.size();
        tile(R.id.tile_courses, R.drawable.ic_school, String.valueOf(courses),
                getString(R.string.tile_courses, cur == null ? 0 : (int) Math.round(cur.totalCredits())), null);
        int risks = Insights.countAttendanceRisks(d);
        tile(R.id.tile_attendance, risks > 0 ? R.drawable.ic_warning : R.drawable.ic_event_available, String.valueOf(risks),
                getString(R.string.tile_attendance), v -> ((MainActivity) requireActivity()).selectTab(R.id.tab_attendance));
        tile(R.id.tile_target, R.drawable.ic_flag, st.targetGpa > 0 ? Fmt.gpa(st.targetGpa) : Fmt.DASH,
                getString(st.targetGpa > 0 ? R.string.tile_target : R.string.tile_set_target),
                v -> ((MainActivity) requireActivity()).selectTab(R.id.tab_forecast));
        ImageView attIcon = root.findViewById(R.id.tile_attendance).findViewById(R.id.tile_icon);
        attIcon.setImageTintList(Ui.csl(risks > 0 ? ContextCompat.getColor(requireContext(), R.color.status_warning)
                : Ui.attr(requireContext(), androidx.appcompat.R.attr.colorPrimary)));

        // Alerts
        LinearLayout alerts = root.findViewById(R.id.alerts);
        alerts.removeAllViews();
        List<Insights.Alert> list = Insights.build(requireContext(), d);
        for (Insights.Alert a : list) alerts.addView(Rows.alert(alerts, a, v -> openAlert(a)));
        if (list.isEmpty()) {
            Insights.Alert ok = new Insights.Alert(Insights.Level.SUCCESS, R.drawable.ic_check_circle,
                    getString(R.string.alert_all_good_title), getString(R.string.alert_all_good_text));
            alerts.addView(Rows.alert(alerts, ok, null));
        }

        // Trend
        List<GpaCalculator.TrendPoint> trend = GpaCalculator.trend(d);
        TrendChartView chart = root.findViewById(R.id.trend);
        List<TrendChartView.Point> pts = new ArrayList<>();
        boolean anyProjected = false;
        for (GpaCalculator.TrendPoint p : trend) {
            pts.add(new TrendChartView.Point(p.semester.name, p.semesterGpa, p.cumulativeGpa, p.projected));
            anyProjected |= p.projected;
        }
        chart.setVisibility(pts.isEmpty() ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.trend_empty).setVisibility(pts.isEmpty() ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.trend_note).setVisibility(anyProjected ? View.VISIBLE : View.GONE);
        if (!pts.isEmpty()) chart.setData(pts, max);

        // Current semester courses
        LinearLayout cl = root.findViewById(R.id.current_list);
        cl.removeAllViews();
        boolean hasCourses = cur != null && !cur.courses.isEmpty();
        root.findViewById(R.id.current_title).setVisibility(cur != null ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.current_card).setVisibility(cur != null ? View.VISIBLE : View.GONE);
        if (cur != null) {
            ((TextView) root.findViewById(R.id.current_title)).setText(cur.name);
            if (!hasCourses) {
                View add = Rows.simple(cl, R.drawable.ic_add, Ui.attr(requireContext(), androidx.appcompat.R.attr.colorPrimary),
                        getString(R.string.add_course), getString(R.string.no_courses_hint), null);
                add.setOnClickListener(v -> startActivity(SemesterActivity.intent(requireContext(), cur.id)));
                cl.addView(add);
            }
            for (int i = 0; hasCourses && i < cur.courses.size(); i++) {
                Course c = cur.courses.get(i);
                cl.addView(courseRow(cl, c, i, cur, st));
            }
        }
    }

    private View courseRow(ViewGroup parent, Course c, int index, Semester cur, Settings st) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.item_course_row, parent, false);
        Ui.dot(v.findViewById(R.id.row_dot), Ui.courseColor(c.color, index));
        ((TextView) v.findViewById(R.id.row_title)).setText(c.displayName());
        AttendanceCalculator.Stats a = AttendanceCalculator.compute(c, cur, st);
        String sub = Labels.credits(requireContext(), c.credits);
        if (!c.isCompleted() && a.hasTotal()) {
            sub += "  ·  " + getString(R.string.course_absences_short, a.counted, a.allowed);
            com.google.android.material.progressindicator.LinearProgressIndicator p = v.findViewById(R.id.row_progress);
            p.setVisibility(View.VISIBLE);
            p.setMax(Math.max(1, a.allowed + 1));
            p.setProgressCompat(Math.min(a.counted, a.allowed + 1), true);
            p.setIndicatorColor(Ui.statusColor(requireContext(), a.status));
            p.setTrackColor(Ui.withAlpha(Ui.statusColor(requireContext(), a.status), 0.15f));
        }
        ((TextView) v.findViewById(R.id.row_sub)).setText(sub);
        Rows.GradeInfo gi = Rows.gradeInfo(c, st);
        String text = gi.grade == null ? Fmt.DASH : (gi.label == R.string.label_final ? "" : "≈ ") + gi.grade.letter;
        Ui.badge(v.findViewById(R.id.row_badge), text, Ui.gradeColor(requireContext(), gi.grade, st.scale));
        v.setOnClickListener(x -> startActivity(CourseActivity.intent(requireContext(), c.id, CourseActivity.TAB_MARKS)));
        return v;
    }

    private void tile(int id, int icon, String value, String label, View.OnClickListener click) {
        View t = root.findViewById(id);
        ((ImageView) t.findViewById(R.id.tile_icon)).setImageResource(icon);
        ((TextView) t.findViewById(R.id.tile_value)).setText(value);
        ((TextView) t.findViewById(R.id.tile_label)).setText(label);
        t.setOnClickListener(click);
        t.setClickable(click != null);
    }

    private void openAlert(Insights.Alert a) {
        if (a.courseId != null) {
            startActivity(CourseActivity.intent(requireContext(), a.courseId, a.courseTab));
        } else if (a.openForecast) {
            ((MainActivity) requireActivity()).selectTab(R.id.tab_forecast);
        } else if (a.openSchedule) {
            ((MainActivity) requireActivity()).selectTab(R.id.tab_schedule);
        }
    }
}
