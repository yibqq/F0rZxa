package com.mizan.gpa.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.mizan.gpa.R;
import com.mizan.gpa.calc.GpaCalculator;
import com.mizan.gpa.calc.GradeResolver;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.ui.views.GpaRingView;
import com.mizan.gpa.util.Fmt;

public class SemesterActivity extends BaseActivity {
    private static final String EXTRA_ID = "semester";
    private String semesterId;
    private MaterialToolbar toolbar;

    public static Intent intent(Context c, String id) {
        return new Intent(c, SemesterActivity.class).putExtra(EXTRA_ID, id);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_semester);
        semesterId = getIntent().getStringExtra(EXTRA_ID);
        toolbar = setupChrome(true);
        toolbar.inflateMenu(R.menu.semester);
        toolbar.setOnMenuItemClickListener(item -> {
            Semester s = semester();
            if (s == null) return false;
            AppData d = data();
            int id = item.getItemId();
            if (id == R.id.action_edit) {
                Dialogs.editSemester(this, s, null);
            } else if (id == R.id.action_set_current) {
                d.settings.currentSemesterId = s.id;
                repo().save();
            } else if (id == R.id.action_move_up || id == R.id.action_move_down) {
                int i = d.semesters.indexOf(s);
                int j = id == R.id.action_move_up ? i - 1 : i + 1;
                if (j >= 0 && j < d.semesters.size()) {
                    d.semesters.remove(i);
                    d.semesters.add(j, s);
                    repo().save();
                }
            } else if (id == R.id.action_delete) {
                Dialogs.confirm(this, getString(R.string.delete_semester_title, s.name),
                        getResources().getQuantityString(R.plurals.delete_semester_text, s.courses.size(), s.courses.size()),
                        R.string.delete, () -> {
                            d.semesters.remove(s);
                            if (s.id.equals(d.settings.currentSemesterId)) d.settings.currentSemesterId = null;
                            repo().save();
                            finish();
                        });
            }
            return true;
        });
        findViewById(R.id.fab).setOnClickListener(v -> {
            Semester s = semester();
            if (s != null) Dialogs.editCourse(this, s, null, null);
        });
    }

    private Semester semester() {
        return data().findSemester(semesterId);
    }

    @Override
    protected void render() {
        Semester s = semester();
        if (s == null) {
            finish();
            return;
        }
        AppData d = data();
        Settings st = d.settings;
        toolbar.setTitle(s.name);
        boolean current = d.currentSemester() == s;
        toolbar.setSubtitle(current ? getString(R.string.current_semester) : null);
        toolbar.getMenu().findItem(R.id.action_set_current).setVisible(!current);
        int idx = d.semesters.indexOf(s);
        toolbar.getMenu().findItem(R.id.action_move_up).setVisible(idx > 0);
        toolbar.getMenu().findItem(R.id.action_move_down).setVisible(idx < d.semesters.size() - 1);

        GpaCalculator.Totals off = GpaCalculator.semester(s, st, GradeResolver.Mode.OFFICIAL);
        GpaCalculator.Totals exp = GpaCalculator.semester(s, st, GradeResolver.Mode.EXPECTED);
        double max = st.scale.maxPoints();
        GpaRingView ring = findViewById(R.id.ring);
        ring.setValue(off.gpa(), max, getString(R.string.semester_gpa_short));
        Rows.kv(findViewById(R.id.row_expected), getString(R.string.label_expected), Fmt.gpa(exp.gpa()));
        Rows.kv(findViewById(R.id.row_credits), getString(R.string.credit_hours_label), Fmt.num(s.totalCredits()));
        GpaCalculator.Totals after = GpaCalculator.cumulativeUpTo(d, s, GradeResolver.Mode.OFFICIAL);
        Rows.kv(findViewById(R.id.row_cumulative), getString(R.string.cumulative_after_short), Fmt.gpa(after.gpa()));
        Rows.kv(findViewById(R.id.row_weeks), getString(R.string.teaching_weeks), String.valueOf(s.weeks));

        LinearLayout list = findViewById(R.id.list);
        list.removeAllViews();
        if (s.courses.isEmpty()) {
            Rows.empty(findViewById(android.R.id.content), R.drawable.ic_assignment, R.string.empty_courses_title,
                    R.string.empty_courses_text, 0, null);
        } else {
            Rows.hideEmpty(findViewById(android.R.id.content));
        }
        for (int i = 0; i < s.courses.size(); i++) {
            Course c = s.courses.get(i);
            View card = Rows.courseCard(list, c, i, s, st);
            card.setOnClickListener(v -> startActivity(CourseActivity.intent(this, c.id, CourseActivity.TAB_MARKS)));
            card.setOnLongClickListener(v -> {
                Dialogs.editCourse(this, s, c, null);
                return true;
            });
            list.addView(card);
        }
    }
}
