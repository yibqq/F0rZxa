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
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.mizan.gpa.R;
import com.mizan.gpa.calc.GpaCalculator;
import com.mizan.gpa.calc.GradeResolver;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeDef;
import com.mizan.gpa.data.GradeScale;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.ui.views.RangeBarView;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ForecastFragment extends BaseFragment {
    private View root;
    private final Map<String, String> whatIf = new HashMap<>();
    private String whatIfSemester;
    private TextInputEditText targetInput, creditsInput;
    private boolean binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        root = inflater.inflate(R.layout.fragment_forecast, container, false);
        targetInput = root.findViewById(R.id.t_target);
        creditsInput = root.findViewById(R.id.t_credits);
        Dialogs.SimpleWatcher w = new Dialogs.SimpleWatcher(s -> {
            if (!binding) renderTarget();
        });
        targetInput.addTextChangedListener(w);
        creditsInput.addTextChangedListener(w);
        targetInput.setOnFocusChangeListener((v, has) -> {
            if (!has) saveTarget();
        });
        root.findViewById(R.id.w_preset_expected).setOnClickListener(v -> preset(GradeResolver.Mode.EXPECTED));
        root.findViewById(R.id.w_preset_best).setOnClickListener(v -> preset(GradeResolver.Mode.BEST));
        root.findViewById(R.id.w_preset_worst).setOnClickListener(v -> preset(GradeResolver.Mode.WORST));
        root.findViewById(R.id.w_preset_top).setOnClickListener(v -> {
            for (Course c : inProgress()) whatIf.put(c.id, data().settings.scale.top().letter);
            buildWhatIf();
        });
        return root;
    }

    @Override
    public void onPause() {
        super.onPause();
        saveTarget();
    }

    private void saveTarget() {
        if (targetInput == null) return;
        double t = Fmt.parse(targetInput.getText());
        Settings st = data().settings;
        double value = Double.isNaN(t) || t <= 0 || t > st.scale.maxPoints() ? 0 : t;
        if (Math.abs(value - st.targetGpa) > 1e-9 && (value > 0 || isEmpty(targetInput))) {
            st.targetGpa = value;
            repo().save();
        }
    }

    private static boolean isEmpty(TextInputEditText e) {
        return e.getText() == null || e.getText().toString().trim().isEmpty();
    }

    private List<Course> inProgress() {
        List<Course> out = new ArrayList<>();
        Semester cur = data().currentSemester();
        if (cur == null) return out;
        for (Course c : cur.courses) if (!c.isCompleted() && c.countInGpa && c.credits > 0) out.add(c);
        return out;
    }

    @Override
    protected void render() {
        AppData d = data();
        Settings st = d.settings;
        Semester cur = d.currentSemester();
        View content = root.findViewById(R.id.forecast_content);
        if (cur == null || cur.courses.isEmpty()) {
            content.setVisibility(View.GONE);
            Rows.empty(root, R.drawable.ic_trending_up, R.string.empty_forecast_title, R.string.empty_forecast_text, 0, null);
            return;
        }
        Rows.hideEmpty(root);
        content.setVisibility(View.VISIBLE);
        double max = st.scale.maxPoints();

        GpaCalculator.Totals off = GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL);
        GpaCalculator.Totals exp = GpaCalculator.cumulative(d, GradeResolver.Mode.EXPECTED);
        GpaCalculator.Totals best = GpaCalculator.cumulative(d, GradeResolver.Mode.BEST);
        GpaCalculator.Totals worst = GpaCalculator.cumulative(d, GradeResolver.Mode.WORST);
        GpaCalculator.Totals semExp = GpaCalculator.semester(cur, st, GradeResolver.Mode.EXPECTED);
        GpaCalculator.Totals semBest = GpaCalculator.semester(cur, st, GradeResolver.Mode.BEST);
        GpaCalculator.Totals semWorst = GpaCalculator.semester(cur, st, GradeResolver.Mode.WORST);

        ((TextView) root.findViewById(R.id.f_semester)).setText(cur.name);
        ((TextView) root.findViewById(R.id.f_current)).setText(Fmt.gpa(off.gpa()));
        ((TextView) root.findViewById(R.id.f_expected)).setText(Fmt.gpa(exp.gpa()));
        delta(root.findViewById(R.id.f_delta), root.findViewById(R.id.f_arrow), off.gpa(), exp.gpa());
        Rows.kv(root.findViewById(R.id.f_row_sem), getString(R.string.expected_semester_gpa),
                semExp.credits > 0 ? Fmt.gpa(semExp.gpa()) : getString(R.string.not_enough_data));

        range(root.findViewById(R.id.range_semester), getString(R.string.semester_gpa), semWorst.gpa(), semBest.gpa(), semExp.gpa(), max);
        range(root.findViewById(R.id.range_cumulative), getString(R.string.cumulative_gpa), worst.gpa(), best.gpa(), exp.gpa(), max);

        if (!cur.id.equals(whatIfSemester)) {
            whatIf.clear();
            whatIfSemester = cur.id;
        }
        buildWhatIf();

        if (!targetInput.hasFocus() && !creditsInput.hasFocus()) {
            binding = true;
            targetInput.setText(st.targetGpa > 0 ? Fmt.gpa(st.targetGpa) : "");
            creditsInput.setText(Fmt.num(GpaCalculator.inProgressCredits(cur)));
            binding = false;
        }
        renderTarget();
    }

    private void delta(TextView badge, ImageView arrow, double from, double to) {
        if (Double.isNaN(from) || Double.isNaN(to)) {
            badge.setVisibility(View.GONE);
            if (arrow != null) arrow.setVisibility(View.INVISIBLE);
            return;
        }
        double diff = to - from;
        int color = ContextCompat.getColor(requireContext(),
                diff > 0.0049 ? R.color.status_excellent : diff < -0.0049 ? R.color.status_danger : R.color.status_good);
        badge.setVisibility(View.VISIBLE);
        Ui.badge(badge, Fmt.signed(diff), color);
        if (arrow != null) {
            arrow.setVisibility(View.VISIBLE);
            arrow.setImageResource(diff >= 0 ? R.drawable.ic_arrow_up : R.drawable.ic_arrow_down);
            arrow.setImageTintList(Ui.csl(color));
            arrow.setRotation(0);
        }
    }

    private void range(View v, String title, double worst, double best, double expected, double max) {
        ((TextView) v.findViewById(R.id.r_title)).setText(title);
        ((TextView) v.findViewById(R.id.r_expected)).setText(getString(R.string.expected_value, Fmt.gpa(expected)));
        ((RangeBarView) v.findViewById(R.id.r_bar)).setRange(worst, best, expected, max);
        ((TextView) v.findViewById(R.id.r_worst)).setText(getString(R.string.worst_value, Fmt.gpa(worst)));
        ((TextView) v.findViewById(R.id.r_best)).setText(getString(R.string.best_value, Fmt.gpa(best)));
    }

    private void preset(GradeResolver.Mode mode) {
        Settings st = data().settings;
        for (Course c : inProgress()) {
            GradeDef g = GradeResolver.resolve(c, st.scale, st.roundPercent, mode);
            if (g == null) g = defaultGuess(c, st);
            whatIf.put(c.id, g.letter);
        }
        buildWhatIf();
    }

    private GradeDef defaultGuess(Course c, Settings st) {
        GradeDef g = GradeResolver.resolve(c, st.scale, st.roundPercent, GradeResolver.Mode.EXPECTED);
        if (g != null) return g;
        // No information at all: start from a mid-scale grade.
        return st.scale.grades.get(Math.min(st.scale.grades.size() - 1, st.scale.grades.size() / 3));
    }

    private void buildWhatIf() {
        Settings st = data().settings;
        GradeScale scale = st.scale;
        LinearLayout list = root.findViewById(R.id.w_list);
        list.removeAllViews();
        List<Course> courses = inProgress();
        Semester cur = data().currentSemester();
        if (courses.isEmpty()) {
            TextView t = (TextView) LayoutInflater.from(requireContext()).inflate(R.layout.view_caption, list, false);
            t.setText(R.string.whatif_none);
            list.addView(t);
            root.findViewById(R.id.w_presets).setVisibility(View.GONE);
        } else {
            root.findViewById(R.id.w_presets).setVisibility(View.VISIBLE);
        }
        for (Course c : courses) {
            if (!whatIf.containsKey(c.id) || scale.find(whatIf.get(c.id)) == null) whatIf.put(c.id, defaultGuess(c, st).letter);
            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_whatif, list, false);
            Ui.dot(row.findViewById(R.id.w_dot), Ui.courseColor(c.color, cur.courses.indexOf(c)));
            ((TextView) row.findViewById(R.id.w_name)).setText(c.displayName());
            ((TextView) row.findViewById(R.id.w_info)).setText(Labels.credits(requireContext(), c.credits));
            ChipGroup chips = row.findViewById(R.id.w_chips);
            for (GradeDef g : scale.grades) {
                Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
                chip.setId(View.generateViewId());
                chip.setText(g.letter);
                chip.setCheckable(true);
                chip.setCheckedIconVisible(false);
                chip.setChecked(g.letter.equals(whatIf.get(c.id)));
                chip.setOnCheckedChangeListener((b, checked) -> {
                    if (checked) {
                        whatIf.put(c.id, g.letter);
                        renderWhatIfResult();
                    }
                });
                chips.addView(chip);
            }
            list.addView(row);
        }
        renderWhatIfResult();
    }

    private void renderWhatIfResult() {
        AppData d = data();
        Semester cur = d.currentSemester();
        if (cur == null) return;
        GpaCalculator.Totals sem = GpaCalculator.semester(cur, d.settings, GradeResolver.Mode.OFFICIAL, whatIf);
        GpaCalculator.Totals cum = GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL, whatIf);
        GpaCalculator.Totals now = GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL);
        ((TextView) root.findViewById(R.id.w_sem)).setText(Fmt.gpa(sem.gpa()));
        ((TextView) root.findViewById(R.id.w_sem)).setTextColor(Ui.gpaColor(requireContext(), sem.gpa(), d.settings.scale));
        ((TextView) root.findViewById(R.id.w_cum)).setText(Fmt.gpa(cum.gpa()));
        ((TextView) root.findViewById(R.id.w_cum)).setTextColor(Ui.gpaColor(requireContext(), cum.gpa(), d.settings.scale));
        delta(root.findViewById(R.id.w_delta), null, now.gpa(), cum.gpa());
    }

    private void renderTarget() {
        AppData d = data();
        Settings st = d.settings;
        double max = st.scale.maxPoints();
        double target = Fmt.parse(targetInput.getText());
        double credits = Fmt.parse(creditsInput.getText());
        View box = root.findViewById(R.id.t_result_box);
        ImageView icon = root.findViewById(R.id.t_icon);
        TextView result = root.findViewById(R.id.t_result);
        TextView detail = root.findViewById(R.id.t_detail);
        TextView extra = root.findViewById(R.id.t_extra);
        extra.setVisibility(View.GONE);
        GpaCalculator.Totals base = GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL);

        int color;
        if (Double.isNaN(target) || target <= 0) {
            color = Ui.attr(requireContext(), androidx.appcompat.R.attr.colorPrimary);
            icon.setImageResource(R.drawable.ic_flag);
            result.setText(R.string.target_enter);
            detail.setText(getString(R.string.target_enter_hint, Fmt.gpa(max)));
        } else if (target > max + 1e-9) {
            color = ContextCompat.getColor(requireContext(), R.color.status_danger);
            icon.setImageResource(R.drawable.ic_error);
            result.setText(R.string.target_above_max);
            detail.setText(getString(R.string.target_above_max_text, Fmt.gpa(max)));
        } else if (Double.isNaN(credits) || credits <= 0) {
            color = Ui.attr(requireContext(), androidx.appcompat.R.attr.colorPrimary);
            icon.setImageResource(R.drawable.ic_info);
            result.setText(R.string.target_need_credits);
            detail.setText(R.string.target_need_credits_text);
        } else {
            double req = GpaCalculator.requiredGpa(base, target, credits);
            double needAtMax = GpaCalculator.creditsNeededAtMax(base, target, max);
            if (req > max + 1e-9) {
                color = ContextCompat.getColor(requireContext(), R.color.status_danger);
                icon.setImageResource(R.drawable.ic_warning);
                result.setText(getString(R.string.target_unreachable, Fmt.gpa(req)));
                detail.setText(Double.isInfinite(needAtMax) ? getString(R.string.target_never)
                        : getString(R.string.target_need_hours_at_max, Fmt.num(Math.ceil(needAtMax)), Fmt.gpa(max)));
            } else if (req <= st.scale.minPoints() + 1e-9) {
                color = ContextCompat.getColor(requireContext(), R.color.status_excellent);
                icon.setImageResource(R.drawable.ic_check_circle);
                result.setText(R.string.target_secured);
                detail.setText(getString(R.string.target_secured_text, Fmt.gpa(target)));
            } else {
                GradeDef avg = lowestGradeAtLeast(st.scale, req);
                color = ContextCompat.getColor(requireContext(), R.color.status_good);
                icon.setImageResource(R.drawable.ic_flag);
                result.setText(getString(R.string.target_required, Fmt.gpa(req)));
                detail.setText(avg != null ? getString(R.string.target_required_grade, Fmt.ltr(avg.letter), Labels.credits(requireContext(), credits))
                        : Labels.credits(requireContext(), credits));
                Semester cur = d.currentSemester();
                GpaCalculator.Totals semExp = cur == null ? null : GpaCalculator.semester(cur, st, GradeResolver.Mode.EXPECTED);
                if (semExp != null && semExp.credits > 0) {
                    extra.setVisibility(View.VISIBLE);
                    extra.setText(getString(semExp.gpa() >= req - 1e-9 ? R.string.target_on_track : R.string.target_behind,
                            Fmt.gpa(semExp.gpa())));
                }
            }
        }
        icon.setImageTintList(Ui.csl(color));
        result.setTextColor(color);
        Ui.roundedFill(box, Ui.withAlpha(color, 0.10f), 16);
    }

    /** The lowest grade whose points are at least {@code points}. */
    private static GradeDef lowestGradeAtLeast(GradeScale scale, double points) {
        GradeDef best = null;
        for (GradeDef g : scale.grades) {
            if (g.points >= points - 1e-9 && (best == null || g.points < best.points)) best = g;
        }
        return best;
    }
}
