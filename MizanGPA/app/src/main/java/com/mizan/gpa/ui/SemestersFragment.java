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

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.mizan.gpa.R;
import com.mizan.gpa.calc.GpaCalculator;
import com.mizan.gpa.calc.GradeResolver;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

public class SemestersFragment extends BaseFragment {
    private View root;
    private View header;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        root = inflater.inflate(R.layout.fragment_list, container, false);
        ExtendedFloatingActionButton fab = root.findViewById(R.id.fab);
        fab.setText(R.string.add_semester);
        fab.setOnClickListener(v -> addSemester());
        FrameLayout h = root.findViewById(R.id.header);
        header = inflater.inflate(R.layout.header_semesters, h, false);
        h.addView(header);
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
        LinearLayout list = root.findViewById(R.id.list);
        list.removeAllViews();
        if (d.semesters.isEmpty()) {
            header.setVisibility(View.GONE);
            Rows.empty(root, R.drawable.ic_school, R.string.empty_semesters_title, R.string.empty_semesters_text,
                    R.string.add_semester, v -> addSemester());
            return;
        }
        Rows.hideEmpty(root);
        header.setVisibility(View.VISIBLE);
        GpaCalculator.Totals cum = GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL);
        ((TextView) header.findViewById(R.id.h_gpa)).setText(Fmt.gpa(cum.gpa()));
        ((TextView) header.findViewById(R.id.h_gpa)).setTextColor(Ui.gpaColor(requireContext(), cum.gpa(), st.scale));
        ((TextView) header.findViewById(R.id.h_credits)).setText(Fmt.num(cum.credits));
        int courses = 0;
        for (Semester s : d.semesters) courses += s.courses.size();
        ((TextView) header.findViewById(R.id.h_courses)).setText(String.valueOf(courses));
        TextView prior = header.findViewById(R.id.h_prior);
        if (st.priorCredits > 0) {
            prior.setVisibility(View.VISIBLE);
            prior.setText(getString(R.string.prior_included, Fmt.gpa(st.priorGpa), Fmt.num(st.priorCredits)));
        } else {
            prior.setVisibility(View.GONE);
        }

        Semester current = d.currentSemester();
        for (int i = d.semesters.size() - 1; i >= 0; i--) {
            // Newest first.
            Semester s = d.semesters.get(i);
            list.addView(semesterCard(list, d, s, s == current));
        }
    }

    private View semesterCard(ViewGroup parent, AppData d, Semester s, boolean isCurrent) {
        Settings st = d.settings;
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.item_semester, parent, false);
        ((TextView) v.findViewById(R.id.sem_name)).setText(s.name);
        TextView badge = v.findViewById(R.id.sem_badge);
        if (isCurrent) Ui.badge(badge, getString(R.string.current), Ui.attr(requireContext(), androidx.appcompat.R.attr.colorPrimary));
        else if (s.isCompleted()) Ui.badge(badge, getString(R.string.completed), Ui.attr(requireContext(), com.google.android.material.R.attr.colorOutline));
        else badge.setVisibility(View.GONE);
        ((TextView) v.findViewById(R.id.sem_info)).setText(getResources().getQuantityString(R.plurals.courses_count,
                s.courses.size(), s.courses.size()) + " · " + Labels.credits(requireContext(), s.totalCredits()));

        GpaCalculator.Totals off = GpaCalculator.semester(s, st, GradeResolver.Mode.OFFICIAL);
        GpaCalculator.Totals exp = GpaCalculator.semester(s, st, GradeResolver.Mode.EXPECTED);
        boolean projected = exp.credits > off.credits + 1e-9;
        double gpa = projected ? exp.gpa() : off.gpa();
        TextView g = v.findViewById(R.id.sem_gpa);
        g.setText(Fmt.gpa(gpa));
        g.setTextColor(Ui.gpaColor(requireContext(), gpa, st.scale));
        ((TextView) v.findViewById(R.id.sem_gpa_label)).setText(projected ? R.string.label_expected : R.string.semester_gpa_short);
        GpaCalculator.Totals after = GpaCalculator.cumulativeUpTo(d, s, projected ? GradeResolver.Mode.EXPECTED : GradeResolver.Mode.OFFICIAL);
        TextView cum = v.findViewById(R.id.sem_cum);
        cum.setText(getString(R.string.cumulative_after, Fmt.gpa(after.gpa())));
        cum.setVisibility(Double.isNaN(after.gpa()) ? View.GONE : View.VISIBLE);
        v.setOnClickListener(x -> startActivity(SemesterActivity.intent(requireContext(), s.id)));
        return v;
    }
}
