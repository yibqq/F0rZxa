package com.mizan.gpa.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.mizan.gpa.R;
import com.mizan.gpa.calc.GpaCalculator;
import com.mizan.gpa.calc.GradeResolver;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeDef;
import com.mizan.gpa.data.GradeScale;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Themes;
import com.mizan.gpa.util.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * "GPA Calculator": the cumulative GPA from saved records (semesters + the manual baseline), the
 * baseline itself with its no-double-counting policy, the saved semesters, and a temporary calculator
 * whose rows are never saved unless the user explicitly saves them as a semester. Only official
 * grades are used — there are no predictions here.
 */
public class GpaFragment extends BaseFragment {
    private static final String STATE_ROWS = "rows", STATE_COMBINE = "combine";

    /** A temporary calculator row; nothing here is part of the saved records. */
    private static final class Row {
        String name = "", credits = "", letter;
    }

    private final List<Row> rows = new ArrayList<>();
    private View root;
    private int heroText;

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        if (state != null && state.getStringArrayList(STATE_ROWS) != null) {
            ArrayList<String> flat = state.getStringArrayList(STATE_ROWS);
            for (int i = 0; i + 2 < flat.size(); i += 3) {
                Row r = new Row();
                r.name = flat.get(i);
                r.credits = flat.get(i + 1);
                r.letter = flat.get(i + 2).isEmpty() ? null : flat.get(i + 2);
                rows.add(r);
            }
        }
        if (rows.isEmpty()) {
            rows.add(new Row());
            rows.add(new Row());
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        ArrayList<String> flat = new ArrayList<>();
        for (Row r : rows) {
            flat.add(r.name);
            flat.add(r.credits);
            flat.add(r.letter == null ? "" : r.letter);
        }
        out.putStringArrayList(STATE_ROWS, flat);
        if (root != null) out.putBoolean(STATE_COMBINE, ((MaterialSwitch) root.findViewById(R.id.g_combine)).isChecked());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        root = inflater.inflate(R.layout.fragment_gpa, container, false);
        root.findViewById(R.id.g_add_semester).setOnClickListener(v -> addSemester());
        root.findViewById(R.id.g_baseline).setOnClickListener(v -> editBaseline());
        root.findViewById(R.id.g_base_edit).setOnClickListener(v -> editBaseline());
        root.findViewById(R.id.g_add_row).setOnClickListener(v -> {
            rows.add(new Row());
            buildRows();
        });
        root.findViewById(R.id.g_clear).setOnClickListener(v -> {
            rows.clear();
            rows.add(new Row());
            rows.add(new Row());
            buildRows();
        });
        root.findViewById(R.id.g_save).setOnClickListener(v -> saveAsSemester());
        MaterialSwitch combine = root.findViewById(R.id.g_combine);
        if (state != null) combine.setChecked(state.getBoolean(STATE_COMBINE, true));
        combine.setOnCheckedChangeListener((b, on) -> renderCalc());
        buildRows();
        return root;
    }

    private void addSemester() {
        Dialogs.editSemester((AppCompatActivity) requireActivity(), null,
                s -> startActivity(SemesterActivity.intent(requireContext(), s.id)));
    }

    @Override
    protected void render() {
        AppData d = data();
        Settings st = d.settings;
        double max = st.scale.maxPoints();

        // Result from saved records
        heroText = Themes.heroBackground(root.findViewById(R.id.g_result_bg), 28);
        GpaCalculator.Totals cum = GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL);
        GpaCalculator.Totals base = GpaCalculator.prior(st);
        for (int id : new int[]{R.id.g_cum_label, R.id.g_cum, R.id.g_cum_max, R.id.g_cum_detail}) {
            ((TextView) root.findViewById(id)).setTextColor(heroText);
        }
        ((TextView) root.findViewById(R.id.g_cum)).setText(Fmt.gpa(cum.gpa()));
        ((TextView) root.findViewById(R.id.g_cum_max)).setText(getString(R.string.out_of_fmt, Fmt.gpa(max)));
        String detail;
        if (Double.isNaN(cum.gpa())) detail = getString(R.string.gpa_result_empty);
        else if (base.credits > 0) detail = getString(R.string.gpa_result_with_base, Fmt.num(cum.credits - base.credits), Fmt.num(base.credits));
        else detail = getString(R.string.gpa_result_saved_only, Fmt.num(cum.credits));
        ((TextView) root.findViewById(R.id.g_cum_detail)).setText(detail);
        Semester cur = d.currentSemester();
        GpaCalculator.Totals sem = cur == null ? null : GpaCalculator.semester(cur, st, GradeResolver.Mode.OFFICIAL);
        pill(R.id.g_pill_semester, sem == null ? Fmt.DASH : Fmt.gpa(sem.gpa()), getString(R.string.semester_gpa));
        pill(R.id.g_pill_credits, Fmt.num(cum.credits), getString(R.string.pill_credits));
        pill(R.id.g_pill_semesters, String.valueOf(d.semesters.size()), getString(R.string.tab_semesters));

        renderBaseline(d);
        renderSemesters(d);
        renderCalc();
    }

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

    // ------------------------------------------------------------------ baseline

    private void renderBaseline(AppData d) {
        Settings st = d.settings;
        TextView value = root.findViewById(R.id.g_base_value);
        TextView sub = root.findViewById(R.id.g_base_sub);
        TextView warning = root.findViewById(R.id.g_base_warning);
        TextView policy = root.findViewById(R.id.g_base_policy);
        TextView edit = root.findViewById(R.id.g_base_edit);
        GpaCalculator.Baseline state = GpaCalculator.baseline(st);
        int marked = 0;
        for (Semester s : d.semesters) if (s.inBaseline) marked++;
        edit.setText(state == GpaCalculator.Baseline.NONE ? R.string.baseline_add : R.string.edit);
        String warn = null;
        switch (state) {
            case NONE:
                value.setText(R.string.baseline_none);
                sub.setText(R.string.baseline_none_hint);
                policy.setVisibility(View.GONE);
                if (marked > 0) warn = getString(R.string.baseline_marked_without_base);
                break;
            case NO_CREDITS:
                value.setText(Fmt.gpa(st.priorGpa));
                sub.setText(R.string.baseline_hours_unknown);
                warn = getString(R.string.baseline_warn_no_credits);
                policy.setVisibility(View.GONE);
                break;
            case OUT_OF_SCALE:
                value.setText(Fmt.gpa(st.priorGpa));
                sub.setText(getString(R.string.prior_summary, Fmt.gpa(st.priorGpa), Fmt.num(st.priorCredits)));
                warn = getString(R.string.baseline_warn_scale, Fmt.gpa(st.priorGpa), Fmt.gpa(st.scale.maxPoints()));
                policy.setVisibility(View.GONE);
                break;
            default:
                value.setText(getString(R.string.out_of_value, Fmt.gpa(st.priorGpa), Fmt.gpa(st.scale.maxPoints())));
                sub.setText(Labels.credits(requireContext(), st.priorCredits));
                policy.setVisibility(View.VISIBLE);
                policy.setText(marked > 0
                        ? getResources().getQuantityString(R.plurals.baseline_policy_marked, marked, marked)
                        : getString(R.string.baseline_policy));
                break;
        }
        if (warn != null) {
            warning.setVisibility(View.VISIBLE);
            warning.setText(warn);
            int c = ContextCompat.getColor(requireContext(), R.color.status_warning);
            Ui.roundedFill(warning, Ui.withAlpha(c, 0.14f), 12);
        } else {
            warning.setVisibility(View.GONE);
        }
    }

    /**
     * Baseline editor. Both values are required for the baseline to count; a GPA without hours is
     * kept only as a reference (never weighted), and empty fields remove the baseline.
     */
    private void editBaseline() {
        AppCompatActivity a = (AppCompatActivity) requireActivity();
        Settings st = data().settings;
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_input, null);
        TextView msg = v.findViewById(R.id.d_message);
        TextInputLayout gpaL = v.findViewById(R.id.d_value1_layout);
        TextInputEditText gpa = v.findViewById(R.id.d_value1);
        TextInputLayout hoursL = v.findViewById(R.id.d_value2_layout);
        TextInputEditText hours = v.findViewById(R.id.d_value2);
        msg.setVisibility(View.VISIBLE);
        msg.setText(R.string.baseline_dialog_message);
        gpaL.setHint(getString(R.string.baseline_gpa_hint, Fmt.gpa(st.scale.maxPoints())));
        hoursL.setHint(R.string.baseline_hours_hint);
        hoursL.setHelperText(getString(R.string.baseline_hours_helper));
        int type = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        gpa.setInputType(type);
        hours.setInputType(type);
        if (st.priorGpa > 0) gpa.setText(Fmt.gpa(st.priorGpa));
        if (st.priorCredits > 0) hours.setText(Fmt.num(st.priorCredits));
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a)
                .setTitle(R.string.baseline_title)
                .setView(v)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null);
        if (GpaCalculator.baseline(st) != GpaCalculator.Baseline.NONE) b.setNeutralButton(R.string.baseline_remove, null);
        AlertDialog dlg = Dialogs.show(b);
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            gpaL.setError(null);
            hoursL.setError(null);
            String gt = gpa.getText() == null ? "" : gpa.getText().toString().trim();
            String ht = hours.getText() == null ? "" : hours.getText().toString().trim();
            double g = gt.isEmpty() ? Double.NaN : Fmt.parse(gt);
            double h = ht.isEmpty() ? 0 : Fmt.parse(ht);
            String err = validateBaseline(gt, ht, g, h, st.scale);
            if (err != null) {
                if (err.startsWith("h:")) hoursL.setError(err.substring(2));
                else gpaL.setError(err);
                return;
            }
            st.priorGpa = gt.isEmpty() ? 0 : g;
            st.priorCredits = gt.isEmpty() ? 0 : h;
            repo().save();
            dlg.dismiss();
        });
        if (dlg.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x -> {
                st.priorGpa = 0;
                st.priorCredits = 0;
                repo().save();
                dlg.dismiss();
            });
        }
    }

    /** Null when valid; an error message otherwise ("h:" prefix = credit-hours field). */
    private String validateBaseline(String gt, String ht, double g, double h, GradeScale scale) {
        if (gt.isEmpty() && ht.isEmpty()) return null;
        if (gt.isEmpty()) return getString(R.string.baseline_err_gpa_needed);
        if (Double.isNaN(g) || g < 0 || g > scale.maxPoints() + 1e-9) {
            return getString(R.string.baseline_err_gpa_range, Fmt.gpa(scale.maxPoints()));
        }
        if (!ht.isEmpty() && (Double.isNaN(h) || h <= 0 || h > 1000)) return "h:" + getString(R.string.baseline_err_hours);
        return null;
    }

    // ------------------------------------------------------------------ saved semesters

    private void renderSemesters(AppData d) {
        LinearLayout list = root.findViewById(R.id.g_semesters);
        list.removeAllViews();
        if (d.semesters.isEmpty()) {
            View e = Rows.simple(list, R.drawable.ic_school, Ui.attr(requireContext(), androidx.appcompat.R.attr.colorPrimary),
                    getString(R.string.empty_semesters_title), getString(R.string.empty_semesters_text), null);
            e.setOnClickListener(v -> addSemester());
            list.addView(e);
            return;
        }
        Semester current = d.currentSemester();
        for (int i = d.semesters.size() - 1; i >= 0; i--) list.addView(semesterCard(list, d, d.semesters.get(i), d.semesters.get(i) == current));
    }

    private View semesterCard(ViewGroup parent, AppData d, Semester s, boolean isCurrent) {
        Settings st = d.settings;
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.item_semester, parent, false);
        ((TextView) v.findViewById(R.id.sem_name)).setText(s.name);
        TextView badge = v.findViewById(R.id.sem_badge);
        boolean inBase = s.inBaseline && GpaCalculator.baseline(st) == GpaCalculator.Baseline.VALID;
        if (inBase) Ui.badge(badge, getString(R.string.in_baseline_badge), Ui.attr(requireContext(), com.google.android.material.R.attr.colorTertiary));
        else if (isCurrent) Ui.badge(badge, getString(R.string.current), Ui.attr(requireContext(), androidx.appcompat.R.attr.colorPrimary));
        else if (s.isCompleted()) Ui.badge(badge, getString(R.string.completed), Ui.attr(requireContext(), com.google.android.material.R.attr.colorOutline));
        else badge.setVisibility(View.GONE);
        int graded = 0;
        for (Course c : s.courses) if (c.isCompleted()) graded++;
        ((TextView) v.findViewById(R.id.sem_info)).setText(getResources().getQuantityString(R.plurals.courses_count,
                s.courses.size(), s.courses.size()) + " · " + Labels.credits(requireContext(), s.totalCredits()));

        GpaCalculator.Totals off = GpaCalculator.semester(s, st, GradeResolver.Mode.OFFICIAL);
        TextView g = v.findViewById(R.id.sem_gpa);
        g.setText(Fmt.gpa(off.gpa()));
        g.setTextColor(Ui.gpaColor(requireContext(), off.gpa(), st.scale));
        ((TextView) v.findViewById(R.id.sem_gpa_label)).setText(
                graded < s.courses.size() && !s.courses.isEmpty() ? R.string.label_in_progress : R.string.semester_gpa_short);
        TextView cum = v.findViewById(R.id.sem_cum);
        if (inBase) {
            cum.setText(R.string.in_baseline_note);
        } else if (graded < s.courses.size()) {
            cum.setText(getString(R.string.graded_of, graded, s.courses.size()));
        } else {
            GpaCalculator.Totals after = GpaCalculator.cumulativeUpTo(d, s, GradeResolver.Mode.OFFICIAL);
            cum.setText(getString(R.string.cumulative_after, Fmt.gpa(after.gpa())));
            cum.setVisibility(Double.isNaN(after.gpa()) ? View.GONE : View.VISIBLE);
        }
        v.setOnClickListener(x -> startActivity(SemesterActivity.intent(requireContext(), s.id)));
        return v;
    }

    // ------------------------------------------------------------------ temporary calculator

    private void buildRows() {
        LinearLayout list = root.findViewById(R.id.g_rows);
        list.removeAllViews();
        GradeScale scale = data().settings.scale;
        String[] items = new String[scale.grades.size()];
        for (int i = 0; i < items.length; i++) items[i] = Labels.gradeOption(scale.grades.get(i));
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            View v = LayoutInflater.from(requireContext()).inflate(R.layout.item_calc_row, list, false);
            TextInputEditText name = v.findViewById(R.id.c_name);
            TextInputEditText credits = v.findViewById(R.id.c_credits);
            MaterialAutoCompleteTextView grade = v.findViewById(R.id.c_grade);
            name.setText(r.name);
            credits.setText(r.credits);
            grade.setSimpleItems(items);
            GradeDef g = scale.find(r.letter);
            if (g != null) grade.setText(Labels.gradeOption(g), false);
            name.addTextChangedListener(new Dialogs.SimpleWatcher(t -> r.name = t));
            credits.addTextChangedListener(new Dialogs.SimpleWatcher(t -> {
                r.credits = t;
                renderCalc();
            }));
            grade.setOnItemClickListener((p, view, pos, id) -> {
                r.letter = scale.grades.get(pos).letter;
                renderCalc();
            });
            v.findViewById(R.id.c_remove).setOnClickListener(x -> {
                rows.remove(r);
                if (rows.isEmpty()) rows.add(new Row());
                buildRows();
            });
            ((TextInputLayout) v.findViewById(R.id.c_name_layout)).setHint(getString(R.string.calc_course_n, i + 1));
            list.addView(v);
        }
        renderCalc();
    }

    private void renderCalc() {
        if (root == null) return;
        AppData d = data();
        Settings st = d.settings;
        List<GpaCalculator.Entry> entries = new ArrayList<>();
        int blank = 0;
        for (Row r : rows) {
            if (r.credits.trim().isEmpty() && r.letter == null) {
                blank++;
                continue;
            }
            entries.add(new GpaCalculator.Entry(Fmt.parse(r.credits), r.letter));
        }
        GpaCalculator.Calculation calc = GpaCalculator.calculate(entries, st.scale);
        boolean combine = ((MaterialSwitch) root.findViewById(R.id.g_combine)).isChecked();
        GpaCalculator.Totals saved = GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL);
        GpaCalculator.Totals combined = saved.plus(calc.totals);

        TextView gpa = root.findViewById(R.id.g_calc_gpa);
        gpa.setText(Fmt.gpa(calc.totals.gpa()));
        gpa.setTextColor(Ui.gpaColor(requireContext(), calc.totals.gpa(), st.scale));
        ((TextView) root.findViewById(R.id.g_calc_credits)).setText(Labels.credits(requireContext(), calc.totals.credits));
        root.findViewById(R.id.g_calc_cum_box).setVisibility(combine ? View.VISIBLE : View.GONE);
        TextView cum = root.findViewById(R.id.g_calc_cum);
        cum.setText(calc.totals.credits > 0 ? Fmt.gpa(combined.gpa()) : Fmt.DASH);
        cum.setTextColor(Ui.gpaColor(requireContext(), combined.gpa(), st.scale));
        ((TextView) root.findViewById(R.id.g_calc_cum_credits)).setText(calc.totals.credits > 0
                ? getString(R.string.calc_from_hours, Fmt.gpa(saved.gpa()), Fmt.num(combined.credits)) : "");
        TextView note = root.findViewById(R.id.g_calc_note);
        note.setVisibility(calc.ignored > 0 ? View.VISIBLE : View.GONE);
        note.setText(getResources().getQuantityString(R.plurals.calc_ignored, calc.ignored, calc.ignored));
        Ui.roundedFill(root.findViewById(R.id.g_calc_result),
                Ui.attr(requireContext(), com.google.android.material.R.attr.colorSurfaceContainerHigh), 16);
        root.findViewById(R.id.g_save).setEnabled(calc.totals.courses > 0);
        if (blank == rows.size()) root.findViewById(R.id.g_save).setEnabled(false);
    }

    /** Explicitly turns the calculator rows into a saved semester with official grades. */
    private void saveAsSemester() {
        AppCompatActivity a = (AppCompatActivity) requireActivity();
        AppData d = data();
        Dialogs.texts(a, getString(R.string.calc_save_semester), R.string.semester_name,
                getString(R.string.semester_default_name, d.semesters.size() + 1), 0, null, (name, ignored) -> {
                    Semester s = new Semester();
                    s.name = name.isEmpty() ? getString(R.string.semester_default_name, d.semesters.size() + 1) : name;
                    s.weeks = d.settings.defaultWeeks;
                    int n = 0;
                    for (Row r : rows) {
                        double cr = Fmt.parse(r.credits);
                        if (d.settings.scale.find(r.letter) == null || Double.isNaN(cr) || cr <= 0) continue;
                        Course c = new Course();
                        c.name = r.name.trim().isEmpty() ? getString(R.string.calc_course_n, ++n) : r.name.trim();
                        c.credits = cr;
                        c.finalGrade = d.settings.scale.find(r.letter).letter;
                        c.color = Ui.COURSE_COLORS[s.courses.size() % Ui.COURSE_COLORS.length];
                        s.courses.add(c);
                    }
                    if (s.courses.isEmpty()) return;
                    // Ask whether it is already part of the baseline, to avoid counting it twice.
                    if (GpaCalculator.baseline(d.settings) == GpaCalculator.Baseline.VALID) {
                        Dialogs.show(new MaterialAlertDialogBuilder(a)
                                .setTitle(R.string.in_baseline_question_title)
                                .setMessage(R.string.in_baseline_question)
                                .setPositiveButton(R.string.in_baseline_yes, (x, w) -> finishSave(d, s, true))
                                .setNegativeButton(R.string.in_baseline_no, (x, w) -> finishSave(d, s, false)));
                    } else {
                        finishSave(d, s, false);
                    }
                });
    }

    private void finishSave(AppData d, Semester s, boolean inBaseline) {
        s.inBaseline = inBaseline;
        d.semesters.add(0, s);
        rows.clear();
        rows.add(new Row());
        rows.add(new Row());
        repo().save();
        buildRows();
        Ui.snack(requireActivity(), getString(R.string.calc_saved, s.name));
    }
}
