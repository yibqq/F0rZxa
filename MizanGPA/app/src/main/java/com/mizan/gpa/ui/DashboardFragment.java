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
        Ui.dot(root.findViewById(R.id.legend_bar), TrendChartView.barColor(root));
        Ui.dot(root.findViewById(R.id.legend_line), TrendChartView.lineColor(root));
        com.mizan.gpa.util.Themes.brandTile(root.findViewById(R.id.welcome_logo), root.findViewById(R.id.welcome_logo_icon), 16);
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
        styleHero();
        GpaRingView ring = root.findViewById(R.id.ring);
        ring.setValue(cum.gpa(), max, getString(R.string.out_of_fmt, Fmt.gpa(max)));
        TextView semName = root.findViewById(R.id.hero_semester_name);
        semName.setText(cur != null ? cur.name : "");
        semName.setVisibility(cur != null ? View.VISIBLE : View.GONE);

        // Change of the cumulative GPA since the previous completed semester.
        TextView trendText = root.findViewById(R.id.hero_trend);
        List<GpaCalculator.TrendPoint> official = new ArrayList<>();
        for (GpaCalculator.TrendPoint p : GpaCalculator.trend(d)) if (!p.projected) official.add(p);
        if (official.size() >= 2) {
            double delta = official.get(official.size() - 1).cumulativeGpa - official.get(official.size() - 2).cumulativeGpa;
            String arrow = delta > 0.0049 ? "▲" : delta < -0.0049 ? "▼" : "●";
            trendText.setText(getString(R.string.hero_trend, arrow, Fmt.ltr(Fmt.gpa(Math.abs(delta)))));
            trendText.setVisibility(View.VISIBLE);
        } else {
            trendText.setVisibility(View.GONE);
        }

        String semText = Fmt.DASH;
        if (cur != null) {
            GpaCalculator.Totals off = GpaCalculator.semester(cur, st, GradeResolver.Mode.OFFICIAL);
            GpaCalculator.Totals exp = GpaCalculator.semester(cur, st, GradeResolver.Mode.EXPECTED);
            if (exp.credits > off.credits + 1e-9 && !Double.isNaN(exp.gpa())) semText = "≈" + Fmt.gpa(exp.gpa());
            else semText = Fmt.gpa(off.gpa());
        }
        pill(R.id.pill_semester, semText, getString(R.string.semester_gpa));
        pill(R.id.pill_expected, Fmt.gpa(cumExp.gpa()), getString(R.string.pill_expected));
        pill(R.id.pill_credits, Fmt.num(cum.credits), getString(R.string.pill_credits));

        // Tiles
        int primary = Ui.attr(requireContext(), androidx.appcompat.R.attr.colorPrimary);
        int tertiary = Ui.attr(requireContext(), com.google.android.material.R.attr.colorTertiary);
        int courses = cur == null ? 0 : cur.courses.size();
        tile(R.id.tile_courses, R.drawable.ic_school, primary, String.valueOf(courses), getString(R.string.tile_courses_label),
                Labels.credits(requireContext(), cur == null ? 0 : cur.totalCredits()), null);

        Course risky = Insights.mostAtRisk(d);
        int risks = Insights.countAttendanceRisks(d);
        int riskColor = ContextCompat.getColor(requireContext(), risks == 0 ? R.color.status_excellent
                : Insights.hasDeprivation(d) ? R.color.status_danger : R.color.status_warning);
        tile(R.id.tile_attendance, risks > 0 ? R.drawable.ic_warning : R.drawable.ic_check_circle, riskColor, String.valueOf(risks),
                getString(R.string.tile_attendance), risky != null ? risky.displayName() : getString(R.string.tile_attendance_ok),
                v -> ((MainActivity) requireActivity()).selectTab(R.id.tab_attendance));

        String targetCaption;
        if (st.targetGpa <= 0) {
            targetCaption = getString(R.string.tile_target_tap);
        } else {
            double req = GpaCalculator.requiredGpa(cum, st.targetGpa, GpaCalculator.inProgressCredits(cur));
            if (Double.isNaN(req)) targetCaption = cum.gpa() >= st.targetGpa ? getString(R.string.tile_target_reached) : Fmt.DASH;
            else if (req > max + 1e-9) targetCaption = getString(R.string.tile_target_far);
            else if (req <= st.scale.minPoints() + 1e-9) targetCaption = getString(R.string.tile_target_reached);
            else targetCaption = getString(R.string.tile_target_needs, Fmt.gpa(req));
        }
        tile(R.id.tile_target, R.drawable.ic_flag, tertiary, st.targetGpa > 0 ? Fmt.gpa(st.targetGpa) : Fmt.DASH,
                getString(R.string.tile_target), targetCaption,
                v -> ((MainActivity) requireActivity()).selectTab(R.id.tab_forecast));

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

        // Upcoming exams (next three)
        LinearLayout el = root.findViewById(R.id.exams_list);
        el.removeAllViews();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<Exams.Entry> exams = Exams.upcoming(d, now);
        root.findViewById(R.id.exams_title).setVisibility(exams.isEmpty() ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.exams_card).setVisibility(exams.isEmpty() ? View.GONE : View.VISIBLE);
        for (int i = 0; i < Math.min(3, exams.size()); i++) {
            Exams.Entry e = exams.get(i);
            View row = Rows.simple(el, R.drawable.ic_exam, e.color(), e.course.displayName(),
                    Exams.title(requireContext(), e.exam) + " · " + Exams.when(requireContext(), e.exam), null);
            TextView badge = row.findViewById(R.id.s_value);
            badge.setVisibility(View.VISIBLE);
            int pad = Ui.dp(requireContext(), 10);
            badge.setPadding(pad, pad / 3, pad, pad / 3);
            Ui.badge(badge, Exams.countdown(requireContext(), e.exam, now, false),
                    Exams.urgencyColor(requireContext(), e.exam, now, Ui.attr(requireContext(), androidx.appcompat.R.attr.colorPrimary)));
            ((android.view.ViewGroup.MarginLayoutParams) badge.getLayoutParams()).setMarginEnd(Ui.dp(requireContext(), 12));
            row.setOnClickListener(v -> ((MainActivity) requireActivity()).openExams());
            el.addView(row);
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

    private void tile(int id, int icon, int color, String value, String label, String caption, View.OnClickListener click) {
        View t = root.findViewById(id);
        ((com.google.android.material.card.MaterialCardView) t).setCardBackgroundColor(
                Ui.blend(Ui.attr(requireContext(), com.google.android.material.R.attr.colorSurfaceContainerLow), color, 0.09f));
        ImageView iv = t.findViewById(R.id.tile_icon);
        iv.setImageResource(icon);
        iv.setImageTintList(Ui.csl(color));
        Ui.dot(t.findViewById(R.id.tile_icon_bg), Ui.withAlpha(color, 0.16f));
        TextView v = t.findViewById(R.id.tile_value);
        v.setText(value);
        v.setTextColor(color);
        ((TextView) t.findViewById(R.id.tile_label)).setText(label);
        ((TextView) t.findViewById(R.id.tile_caption)).setText(caption);
        t.setOnClickListener(click);
        t.setClickable(click != null);
    }

    /** Gradient background and readable text colors for the hero card in the current palette. */
    private void styleHero() {
        android.content.Context c = requireContext();
        boolean night = com.mizan.gpa.util.Themes.isNight(c);
        int from = Ui.attr(c, night ? com.google.android.material.R.attr.colorPrimaryContainer : androidx.appcompat.R.attr.colorPrimary);
        int to = Ui.blend(from, Ui.attr(c, night ? com.google.android.material.R.attr.colorTertiaryContainer
                : com.google.android.material.R.attr.colorTertiary), 0.55f);
        int text = Ui.attr(c, night ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnPrimary);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                Ui.isRtl(c) ? android.graphics.drawable.GradientDrawable.Orientation.TR_BL
                        : android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        g.setCornerRadius(Ui.dp(c, 28));
        root.findViewById(R.id.hero_bg).setBackground(g);
        ((TextView) root.findViewById(R.id.hero_label)).setTextColor(text);
        TextView chip = root.findViewById(R.id.hero_semester_name);
        chip.setTextColor(text);
        Ui.roundedFill(chip, Ui.withAlpha(text, 0.16f), 100);
        ((TextView) root.findViewById(R.id.hero_trend)).setTextColor(Ui.withAlpha(text, 0.85f));
        ((GpaRingView) root.findViewById(R.id.ring)).setColors(text, text);
        heroText = text;
    }

    private int heroText;

    private void pill(int id, String value, String label) {
        View p = root.findViewById(id);
        Ui.roundedFill(p, Ui.withAlpha(heroText, 0.14f), 18);
        TextView v = p.findViewById(R.id.pill_value);
        v.setText(value);
        v.setTextColor(heroText);
        TextView l = p.findViewById(R.id.pill_label);
        l.setText(label);
        l.setTextColor(Ui.withAlpha(heroText, 0.8f));
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
