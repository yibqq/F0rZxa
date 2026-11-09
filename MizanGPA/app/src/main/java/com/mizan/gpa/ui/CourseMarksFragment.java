package com.mizan.gpa.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.mizan.gpa.R;
import com.mizan.gpa.calc.MarksCalculator;
import com.mizan.gpa.data.Assessment;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeDef;
import com.mizan.gpa.data.GradeScale;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.util.List;

public class CourseMarksFragment extends CoursePageFragment {
    private boolean showAll;

    @Override
    protected int headerLayout() {
        return R.layout.header_marks;
    }

    @Override
    protected void onCreated() {
        ((MaterialButton) header.findViewById(R.id.req_toggle)).setOnClickListener(v -> {
            showAll = !showAll;
            render();
        });
    }

    @Override
    protected void render() {
        Course c = course();
        if (c == null) return;
        Settings st = data().settings;
        GradeScale scale = st.scale;
        MarksCalculator.Result r = MarksCalculator.compute(c);

        // Summary
        TextView total = header.findViewById(R.id.m_total);
        TextView percent = header.findViewById(R.id.m_percent);
        TextView grade = header.findViewById(R.id.m_grade);
        TextView gradeLabel = header.findViewById(R.id.m_grade_label);
        LinearProgressIndicator progress = header.findViewById(R.id.m_progress);
        TextView range = header.findViewById(R.id.m_range);
        total.setText(getString(R.string.marks_of, Fmt.num(r.earned), Fmt.num(r.hasMarks() ? r.gradedWeight : r.totalWeight)));
        percent.setText(r.hasMarks()
                ? getString(R.string.marks_percent_line, Fmt.pct(r.currentPercent()), Fmt.num(r.gradedWeight), Fmt.num(r.totalWeight))
                : getString(R.string.no_marks_yet));
        Rows.GradeInfo gi = Rows.gradeInfo(c, st);
        Ui.badge(grade, gi.grade != null ? gi.grade.letter : Fmt.DASH, Ui.gradeColor(requireContext(), gi.grade, scale));
        gradeLabel.setText(gi.label);
        progress.setMax(1000);
        progress.setProgressCompat((int) Math.round(Math.min(1, r.earned / r.totalWeight) * 1000), true);
        int pc = Ui.gradeColor(requireContext(), r.hasMarks() ? scale.forPercent(r.currentPercent(), st.roundPercent) : null, scale);
        progress.setIndicatorColor(pc);
        progress.setTrackColor(Ui.withAlpha(pc, 0.18f));
        if (r.isComplete()) {
            GradeDef fin = scale.forPercent(r.securedPercent(), st.roundPercent);
            range.setText(getString(R.string.marks_complete, Fmt.pct(r.securedPercent()), Fmt.ltr(fin.letter)));
        } else if (r.hasMarks()) {
            GradeDef best = scale.forPercent(r.maxPercent(), st.roundPercent);
            GradeDef worst = scale.forPercent(r.securedPercent(), st.roundPercent);
            range.setText(getString(R.string.marks_range, Fmt.pct(r.maxPercent()), Fmt.ltr(best.letter), Fmt.pct(r.securedPercent()), Fmt.ltr(worst.letter)));
        } else {
            range.setText(getString(R.string.marks_add_hint, Fmt.num(r.totalWeight)));
        }

        // Requirements for each grade
        View reqCard = header.findViewById(R.id.req_card);
        TextView reqTitle = header.findViewById(R.id.req_title);
        boolean showReq = !c.isCompleted() && !r.isComplete() && r.remainingWeight > 0;
        reqCard.setVisibility(showReq ? View.VISIBLE : View.GONE);
        reqTitle.setVisibility(showReq ? View.VISIBLE : View.GONE);
        if (showReq) {
            if (r.lastRemaining != null) {
                reqTitle.setText(getString(R.string.req_title_single, r.lastRemaining.name, Fmt.num(r.lastRemaining.outOf)));
            } else {
                reqTitle.setText(getString(R.string.req_title_remaining, Fmt.num(r.remainingWeight)));
            }
            LinearLayout rl = header.findViewById(R.id.req_list);
            rl.removeAllViews();
            List<MarksCalculator.Requirement> reqs = MarksCalculator.requirements(r, scale, st.roundPercent);
            boolean shownSecured = false;
            int hidden = 0;
            for (MarksCalculator.Requirement q : reqs) {
                boolean visible = showAll
                        || q.state == MarksCalculator.State.POSSIBLE
                        || (q.state == MarksCalculator.State.SECURED && !shownSecured);
                if (q.state == MarksCalculator.State.SECURED) shownSecured = true;
                if (!visible) {
                    hidden++;
                    continue;
                }
                rl.addView(requirementRow(rl, q, r, scale));
            }
            MaterialButton toggle = header.findViewById(R.id.req_toggle);
            toggle.setVisibility(hidden > 0 || showAll ? View.VISIBLE : View.GONE);
            toggle.setText(showAll ? R.string.show_less : R.string.show_all_grades);
        }

        // Assessment list
        list.removeAllViews();
        showList(R.string.marks_list_title, !c.assessments.isEmpty());
        if (c.assessments.isEmpty()) {
            Rows.empty(root, R.drawable.ic_assignment, R.string.empty_marks_title, R.string.empty_marks_text,
                    R.string.add_mark, v -> Dialogs.editAssessment(activity(), c, null));
        } else {
            Rows.hideEmpty(root);
        }
        for (Assessment a : c.assessments) {
            int color = a.isGraded()
                    ? Ui.gradeColor(requireContext(), scale.forPercent(a.score / a.outOf * 100, st.roundPercent), scale)
                    : Ui.attr(requireContext(), com.google.android.material.R.attr.colorOutline);
            String sub = Labels.assessmentType(requireContext(), a.type) + " · " + getString(R.string.weight_short, Fmt.num(a.weight));
            String value = a.isGraded() ? Fmt.num(a.score) + " / " + Fmt.num(a.outOf) : getString(R.string.not_graded);
            View row = Rows.simple(list, Labels.assessmentIcon(a.type), color, a.name, sub, value);
            if (a.isGraded()) {
                LinearProgressIndicator p = row.findViewById(R.id.s_progress);
                p.setVisibility(View.VISIBLE);
                p.setMax(1000);
                p.setProgressCompat((int) Math.round(Math.min(1, a.score / a.outOf) * 1000), false);
                p.setIndicatorColor(color);
                p.setTrackColor(Ui.withAlpha(color, 0.15f));
            }
            rowMenu(row, () -> Dialogs.editAssessment(activity(), c, a), () -> {
                c.assessments.remove(a);
                repo().save();
            });
            list.addView(row);
        }
    }

    private View requirementRow(LinearLayout parent, MarksCalculator.Requirement q, MarksCalculator.Result r, GradeScale scale) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.item_requirement, parent, false);
        TextView badge = v.findViewById(R.id.req_grade);
        TextView text = v.findViewById(R.id.req_text);
        TextView value = v.findViewById(R.id.req_value);
        Ui.badge(badge, q.grade.letter, Ui.gradeColor(requireContext(), q.grade, scale));
        switch (q.state) {
            case SECURED:
                text.setText(R.string.req_secured);
                value.setText("✓");
                value.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_excellent));
                break;
            case IMPOSSIBLE:
                text.setText(R.string.req_impossible);
                value.setText("✕");
                value.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_danger));
                v.setAlpha(0.6f);
                break;
            default:
                if (!Double.isNaN(q.neededScore)) {
                    text.setText(getString(R.string.req_need_score, Fmt.num(roundUp(q.neededScore)), Fmt.num(q.neededOutOf)));
                } else {
                    text.setText(getString(R.string.req_need_marks, Fmt.num(roundUp(q.neededMarks)), Fmt.num(r.remainingWeight)));
                }
                value.setText(Fmt.pct(q.neededPercent));
                value.setTextColor(Ui.attr(requireContext(), com.google.android.material.R.attr.colorOnSurface));
                break;
        }
        return v;
    }

    /** Rounds up to two decimals so the shown requirement is never slightly too low. */
    private static double roundUp(double v) {
        return Math.ceil(v * 100 - 1e-6) / 100.0;
    }
}
