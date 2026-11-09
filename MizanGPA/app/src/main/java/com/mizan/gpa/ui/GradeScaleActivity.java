package com.mizan.gpa.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeDef;
import com.mizan.gpa.data.GradeScale;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

/**
 * Edits the grading scale. Whenever a letter disappears from the scale, courses that used it are
 * moved to the grade the new scale gives for the same percentage, so no grade is silently lost.
 */
public class GradeScaleActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grade_scale);
        setupChrome(true);
        presetChip(R.id.preset_sa5, GradeScale.PRESET_SA5);
        presetChip(R.id.preset_sa4, GradeScale.PRESET_SA4);
        presetChip(R.id.preset_us4, GradeScale.PRESET_US4);
        findViewById(R.id.fab).setOnClickListener(v -> {
            GradeScale scale = data().settings.scale;
            Dialogs.editGradeDef(this, scale, null, g -> {
                GradeScale next = scale.copy();
                next.grades.add(g);
                next.preset = GradeScale.PRESET_CUSTOM;
                apply(next);
            }, null);
        });
    }

    private void presetChip(int id, String preset) {
        Chip chip = findViewById(id);
        chip.setOnClickListener(v -> {
            GradeScale current = data().settings.scale;
            if (preset.equals(current.preset)) {
                chip.setChecked(true);
                return;
            }
            Dialogs.confirm(this, getString(R.string.switch_scale_title, chip.getText()), getString(R.string.switch_scale_text),
                    R.string.switch_scale, () -> apply(GradeScale.preset(preset)));
            render();
        });
    }

    private void apply(GradeScale next) {
        AppData d = data();
        GradeScale old = d.settings.scale;
        next.sort();
        for (Course c : d.allCourses()) {
            c.finalGrade = remap(c.finalGrade, old, next);
            c.expectedGrade = remap(c.expectedGrade, old, next);
        }
        d.settings.scale = next;
        repo().save();
    }

    private static String remap(String letter, GradeScale old, GradeScale next) {
        if (letter == null) return null;
        if (next.find(letter) != null) return next.find(letter).letter;
        GradeDef was = old.find(letter);
        if (was == null) return null;
        GradeDef now = next.forPercent(was.minPercent, false);
        return now == null ? null : now.letter;
    }

    @Override
    protected void render() {
        GradeScale scale = data().settings.scale;
        ((Chip) findViewById(R.id.preset_sa5)).setChecked(GradeScale.PRESET_SA5.equals(scale.preset));
        ((Chip) findViewById(R.id.preset_sa4)).setChecked(GradeScale.PRESET_SA4.equals(scale.preset));
        ((Chip) findViewById(R.id.preset_us4)).setChecked(GradeScale.PRESET_US4.equals(scale.preset));
        findViewById(R.id.preset_custom).setVisibility(GradeScale.PRESET_CUSTOM.equals(scale.preset) ? View.VISIBLE : View.GONE);
        ((Chip) findViewById(R.id.preset_custom)).setChecked(true);
        ((TextView) findViewById(R.id.scale_summary)).setText(getString(R.string.scale_summary_long,
                Labels.presetName(this, scale.preset), Fmt.gpa(scale.maxPoints()), scale.grades.size()));

        LinearLayout list = findViewById(R.id.list);
        list.removeAllViews();
        for (int i = 0; i < scale.grades.size(); i++) {
            GradeDef g = scale.grades.get(i);
            String range = i == 0 ? getString(R.string.range_top, Fmt.num(g.minPercent))
                    : getString(R.string.range_between, Fmt.num(g.minPercent), Fmt.num(scale.grades.get(i - 1).minPercent));
            View row = Rows.simple(list, 0, 0, getString(R.string.points_fmt, Fmt.gpa(g.points)), range, null);
            TextView badge = new TextView(this, null, 0, R.style.Mizan_Badge);
            Ui.badge(badge, g.letter, Ui.gradeColor(this, g, scale));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ((LinearLayout) row).addView(badge, 0, lp);
            int index = i;
            Runnable edit = () -> Dialogs.editGradeDef(this, scale, g, edited -> {
                GradeScale next = scale.copy();
                GradeDef target = next.grades.get(index);
                String oldLetter = g.letter;
                target.letter = edited.letter;
                target.points = edited.points;
                target.minPercent = edited.minPercent;
                next.preset = GradeScale.PRESET_CUSTOM;
                // A renamed letter keeps its courses.
                for (Course c : data().allCourses()) {
                    if (oldLetter.equals(c.finalGrade)) c.finalGrade = edited.letter;
                    if (oldLetter.equals(c.expectedGrade)) c.expectedGrade = edited.letter;
                }
                apply(next);
            }, scale.grades.size() > 2 ? () -> delete(index) : null);
            row.setOnClickListener(v -> edit.run());
            View menu = row.findViewById(R.id.s_menu);
            menu.setVisibility(View.VISIBLE);
            menu.setOnClickListener(v -> {
                androidx.appcompat.widget.PopupMenu p = new androidx.appcompat.widget.PopupMenu(this, menu);
                p.getMenu().add(0, 1, 0, R.string.edit);
                if (scale.grades.size() > 2) p.getMenu().add(0, 2, 0, R.string.delete);
                p.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) edit.run();
                    else delete(index);
                    return true;
                });
                p.show();
            });
            list.addView(row);
        }
    }

    private void delete(int index) {
        GradeScale scale = data().settings.scale;
        GradeScale next = scale.copy();
        next.grades.remove(index);
        next.preset = GradeScale.PRESET_CUSTOM;
        apply(next);
    }
}
