package com.mizan.gpa.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.mizan.gpa.R;
import com.mizan.gpa.calc.AttendanceCalculator;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Assessment;
import com.mizan.gpa.data.AttendanceRecord;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Task;
import com.mizan.gpa.data.GradeDef;
import com.mizan.gpa.data.GradeScale;
import com.mizan.gpa.data.LectureSlot;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.notify.Notifier;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Fonts;
import com.mizan.gpa.util.Ui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;

/** All add/edit forms. Every form validates in place and keeps the dialog open on errors. */
public final class Dialogs {
    private Dialogs() {}

    public interface Validator {
        /** Returns true when the input is valid and was applied. */
        boolean apply();
    }

    // ------------------------------------------------------------------ helpers

    static AlertDialog form(Context ctx, CharSequence title, View view, @StringRes int positive, Validator ok,
                            Runnable onDelete) {
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(positive, null)
                .setNegativeButton(R.string.cancel, null);
        if (onDelete != null) b.setNeutralButton(R.string.delete, null);
        AlertDialog d = show(b);
        d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (ok.apply()) d.dismiss();
        });
        if (onDelete != null) {
            TextView del = d.getButton(DialogInterface.BUTTON_NEUTRAL);
            del.setTextColor(Ui.attr(ctx, androidx.appcompat.R.attr.colorError));
            del.setOnClickListener(v -> {
                d.dismiss();
                onDelete.run();
            });
        }
        return d;
    }

    /** Shows a dialog and keeps its text in the app font. */
    public static AlertDialog show(MaterialAlertDialogBuilder b) {
        AlertDialog d = b.show();
        if (d.getWindow() != null) {
            View decor = d.getWindow().getDecorView();
            Fonts.applyTree(decor);
            decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> Fonts.applyTree(decor));
        }
        return d;
    }

    public static void confirm(Context ctx, CharSequence title, CharSequence message, @StringRes int positive, Runnable onYes) {
        show(new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positive, (d, w) -> onYes.run())
                .setNegativeButton(R.string.cancel, null));
    }

    public static void choose(Context ctx, CharSequence title, CharSequence[] items, int checked, Consumer<Integer> onPick) {
        show(new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    d.dismiss();
                    onPick.accept(which);
                })
                .setNegativeButton(R.string.cancel, null));
    }

    static String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    static void error(TextInputLayout l, @StringRes int msg) {
        l.setError(l.getContext().getString(msg));
    }

    static void clearErrorOnType(TextInputLayout l) {
        if (l.getEditText() == null) return;
        l.getEditText().addTextChangedListener(new SimpleWatcher(s -> l.setError(null)));
    }

    static final class SimpleWatcher implements TextWatcher {
        private final Consumer<String> after;

        SimpleWatcher(Consumer<String> after) {
            this.after = after;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

        @Override
        public void onTextChanged(CharSequence s, int a, int b, int c) {}

        @Override
        public void afterTextChanged(Editable s) {
            after.accept(s.toString());
        }
    }

    static void setNum(TextInputEditText e, double v) {
        e.setText(Double.isNaN(v) ? "" : Fmt.num(v));
    }

    /** Ordered days of the week starting from the locale's first day. */
    static int[] orderedDays() {
        int first = Calendar.getInstance().getFirstDayOfWeek();
        int[] out = new int[7];
        for (int i = 0; i < 7; i++) out[i] = ((first - 1 + i) % 7) + 1;
        return out;
    }

    // ------------------------------------------------------------------ semester

    public static void editSemester(AppCompatActivity a, Semester existing, Consumer<Semester> onSaved) {
        Repository repo = Repository.get(a);
        AppData d = repo.data();
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_semester, null);
        TextInputLayout nameL = v.findViewById(R.id.d_name_layout);
        TextInputEditText name = v.findViewById(R.id.d_name);
        TextInputLayout weeksL = v.findViewById(R.id.d_weeks_layout);
        TextInputEditText weeks = v.findViewById(R.id.d_weeks);
        MaterialCheckBox current = v.findViewById(R.id.d_current);
        MaterialCheckBox inBase = v.findViewById(R.id.d_in_baseline);
        // Offer "already in my previous cumulative GPA" only when a usable baseline exists.
        boolean baseValid = com.mizan.gpa.calc.GpaCalculator.baseline(d.settings) == com.mizan.gpa.calc.GpaCalculator.Baseline.VALID;
        int baseVis = baseValid || (existing != null && existing.inBaseline) ? View.VISIBLE : View.GONE;
        inBase.setVisibility(baseVis);
        v.findViewById(R.id.d_in_baseline_help).setVisibility(baseVis);
        inBase.setChecked(existing != null && existing.inBaseline);
        clearErrorOnType(nameL);
        clearErrorOnType(weeksL);
        if (existing != null) {
            name.setText(existing.name);
            weeks.setText(String.valueOf(existing.weeks));
            current.setChecked(d.currentSemester() == existing);
        } else {
            name.setText(a.getString(R.string.semester_default_name, d.semesters.size() + 1));
            weeks.setText(String.valueOf(d.settings.defaultWeeks));
            current.setChecked(true);
        }
        name.selectAll();
        form(a, a.getString(existing == null ? R.string.add_semester : R.string.edit_semester), v,
                R.string.save, () -> {
                    String n = text(name);
                    double w = Fmt.parse(text(weeks));
                    boolean ok = true;
                    if (n.isEmpty()) { error(nameL, R.string.err_required); ok = false; }
                    if (Double.isNaN(w) || w < 1 || w > 52) { error(weeksL, R.string.err_weeks); ok = false; }
                    if (!ok) return false;
                    Semester s = existing != null ? existing : new Semester();
                    s.name = n;
                    s.weeks = (int) Math.round(w);
                    s.inBaseline = inBase.getVisibility() == View.VISIBLE && inBase.isChecked();
                    if (existing == null) d.semesters.add(s);
                    if (current.isChecked() || d.settings.currentSemesterId == null) d.settings.currentSemesterId = s.id;
                    else if (s.id.equals(d.settings.currentSemesterId)) d.settings.currentSemesterId = null;
                    repo.save();
                    if (onSaved != null) onSaved.accept(s);
                    return true;
                }, null);
    }

    // ------------------------------------------------------------------ course

    public static void editCourse(AppCompatActivity a, Semester sem, Course existing, Consumer<Course> onSaved) {
        Repository repo = Repository.get(a);
        Settings st = repo.settings();
        GradeScale scale = st.scale;
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_course, null);
        TextInputLayout nameL = v.findViewById(R.id.d_name_layout);
        TextInputEditText name = v.findViewById(R.id.d_name);
        TextInputEditText code = v.findViewById(R.id.d_code);
        TextInputLayout crnL = v.findViewById(R.id.d_crn_layout);
        TextInputEditText crn = v.findViewById(R.id.d_crn);
        View crnMatch = v.findViewById(R.id.d_crn_match);
        TextView crnMatchText = v.findViewById(R.id.d_crn_match_text);
        TextInputLayout creditsL = v.findViewById(R.id.d_credits_layout);
        TextInputEditText credits = v.findViewById(R.id.d_credits);
        MaterialAutoCompleteTextView fin = v.findViewById(R.id.d_final);
        TextInputLayout expL = v.findViewById(R.id.d_expected_layout);
        MaterialAutoCompleteTextView exp = v.findViewById(R.id.d_expected);
        MaterialSwitch count = v.findViewById(R.id.d_count);
        LinearLayout colors = v.findViewById(R.id.d_colors);
        TextInputEditText instructor = v.findViewById(R.id.d_instructor);
        TextInputLayout sessionsL = v.findViewById(R.id.d_sessions_layout);
        TextInputEditText sessions = v.findViewById(R.id.d_sessions);
        TextInputLayout limitL = v.findViewById(R.id.d_limit_layout);
        TextInputEditText limit = v.findViewById(R.id.d_limit);
        TextInputLayout marksL = v.findViewById(R.id.d_total_marks_layout);
        TextInputEditText marks = v.findViewById(R.id.d_total_marks);
        for (TextInputLayout l : new TextInputLayout[]{nameL, creditsL, sessionsL, limitL, marksL}) clearErrorOnType(l);

        String[] finalItems = new String[scale.grades.size() + 1];
        String[] expItems = new String[scale.grades.size() + 1];
        finalItems[0] = a.getString(R.string.grade_in_progress);
        expItems[0] = a.getString(R.string.grade_auto);
        for (int i = 0; i < scale.grades.size(); i++) {
            finalItems[i + 1] = Labels.gradeOption(scale.grades.get(i));
            expItems[i + 1] = finalItems[i + 1];
        }
        fin.setSimpleItems(finalItems);
        exp.setSimpleItems(expItems);
        final int[] finalIdx = {0};
        final int[] expIdx = {0};
        fin.setOnItemClickListener((p, view, pos, id) -> {
            finalIdx[0] = pos;
            expL.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
        });
        exp.setOnItemClickListener((p, view, pos, id) -> expIdx[0] = pos);

        int index = sem.courses.size();
        final int[] color = {Ui.courseColor(0, index)};
        if (existing != null) {
            name.setText(existing.name);
            code.setText(existing.code);
            setNum(credits, existing.credits);
            GradeDef f = scale.find(existing.finalGrade);
            finalIdx[0] = f == null ? 0 : scale.grades.indexOf(f) + 1;
            GradeDef e = scale.find(existing.expectedGrade);
            expIdx[0] = e == null ? 0 : scale.grades.indexOf(e) + 1;
            count.setChecked(existing.countInGpa);
            instructor.setText(existing.instructor);
            if (existing.totalSessions > 0) sessions.setText(String.valueOf(existing.totalSessions));
            if (existing.absenceLimit > 0) setNum(limit, existing.absenceLimit);
            setNum(marks, existing.totalMarks);
            color[0] = Ui.courseColor(existing.color, sem.courses.indexOf(existing));
        } else {
            credits.setText("3");
            count.setChecked(true);
            marks.setText("100");
        }
        fin.setText(finalItems[finalIdx[0]], false);
        exp.setText(expItems[expIdx[0]], false);
        expL.setVisibility(finalIdx[0] == 0 ? View.VISIBLE : View.GONE);

        int planned = existing == null ? 0 : existing.lectures.size() * sem.weeks;
        sessionsL.setHelperText(planned > 0
                ? a.getString(R.string.total_lectures_helper_auto, planned)
                : a.getString(R.string.total_lectures_helper));
        limitL.setHelperText(a.getString(R.string.absence_limit_course_helper, Fmt.pct(st.absenceLimit)));

        buildColorPicker(colors, color);

        // CRN: optional. A CRN already in the student's own records offers to reuse that course's
        // details (name, code, hours, instructor); otherwise everything is entered by hand.
        if (st.crnDigits > 0) crnL.setHelperText(a.getResources().getQuantityString(R.plurals.crn_helper_digits, st.crnDigits, st.crnDigits));
        if (existing != null) crn.setText(existing.crn);
        clearErrorOnType(crnL);
        AppData data = repo.data();
        Runnable lookup = () -> {
            Course match = data.findCourseByCrn(text(crn), existing);
            boolean same = match != null && match.displayName().equals(text(name)) && Math.abs(match.credits - Fmt.parse(text(credits))) < 1e-9;
            if (match == null || same) {
                crnMatch.setVisibility(View.GONE);
                return;
            }
            Semester from = data.semesterOf(match.id);
            crnMatchText.setText(a.getString(R.string.crn_match, match.displayName(), Labels.credits(a, match.credits),
                    from == null ? "" : from.name));
            crnMatch.setVisibility(View.VISIBLE);
            v.findViewById(R.id.d_crn_use).setOnClickListener(x -> {
                name.setText(match.name);
                code.setText(match.code);
                setNum(credits, match.credits);
                if (match.instructor != null && !match.instructor.isEmpty()) instructor.setText(match.instructor);
                crnMatch.setVisibility(View.GONE);
            });
        };
        crn.addTextChangedListener(new SimpleWatcher(t -> lookup.run()));
        Ui.roundedFill(crnMatch, Ui.attr(a, com.google.android.material.R.attr.colorSecondaryContainer), 14);

        form(a, a.getString(existing == null ? R.string.add_course : R.string.edit_course), v, R.string.save, () -> {
            String n = text(name), c = text(code);
            double cr = Fmt.parse(text(credits));
            String sTxt = text(sessions), lTxt = text(limit), mTxt = text(marks);
            double ses = sTxt.isEmpty() ? 0 : Fmt.parse(sTxt);
            double lim = lTxt.isEmpty() ? -1 : Fmt.parse(lTxt);
            double mk = mTxt.isEmpty() ? 100 : Fmt.parse(mTxt);
            boolean ok = true;
            if (n.isEmpty() && c.isEmpty()) { error(nameL, R.string.err_name_or_code); ok = false; }
            if (Double.isNaN(cr) || cr < 0 || cr > 40) { error(creditsL, R.string.err_credits); ok = false; }
            if (Double.isNaN(ses) || ses < 0 || ses > 1000) { error(sessionsL, R.string.err_number); ok = false; }
            if (Double.isNaN(lim) || (!lTxt.isEmpty() && (lim <= 0 || lim > 100))) { error(limitL, R.string.err_percent); ok = false; }
            if (Double.isNaN(mk) || mk <= 0 || mk > 10000) { error(marksL, R.string.err_number); ok = false; }
            AppData.CrnProblem crnProblem = AppData.checkCrn(text(crn), st.crnDigits);
            if (crnProblem == AppData.CrnProblem.WRONG_LENGTH || crnProblem == AppData.CrnProblem.NOT_DIGITS) {
                crnL.setError(a.getResources().getQuantityString(R.plurals.err_crn_digits, st.crnDigits, st.crnDigits));
                ok = false;
            } else if (crnProblem != AppData.CrnProblem.NONE) {
                crnL.setError(a.getString(R.string.err_crn_format));
                ok = false;
            } else {
                // The same CRN twice in one semester would be a duplicate enrollment; across semesters
                // (a repeated course) it is fine.
                Course dup = AppData.crnConflict(sem, text(crn), existing);
                if (dup != null) {
                    crnL.setError(a.getString(R.string.err_crn_duplicate, dup.displayName()));
                    ok = false;
                }
            }
            if (!ok) return false;
            Course course = existing != null ? existing : new Course();
            course.name = n;
            course.code = c;
            course.crn = AppData.normalizeCrn(text(crn));
            course.credits = cr;
            course.finalGrade = finalIdx[0] == 0 ? null : scale.grades.get(finalIdx[0] - 1).letter;
            course.expectedGrade = expIdx[0] == 0 || course.finalGrade != null ? null : scale.grades.get(expIdx[0] - 1).letter;
            course.countInGpa = count.isChecked();
            course.instructor = text(instructor);
            course.totalSessions = (int) Math.round(ses);
            course.absenceLimit = lim;
            course.totalMarks = mk;
            course.color = color[0];
            if (existing == null) sem.courses.add(course);
            repo.save();
            if (onSaved != null) onSaved.accept(course);
            return true;
        }, null);
    }

    private static void buildColorPicker(LinearLayout row, int[] selected) {
        Context c = row.getContext();
        row.removeAllViews();
        int size = Ui.dp(c, 34);
        int gap = Ui.dp(c, 8);
        List<View> dots = new ArrayList<>();
        for (int col : Ui.COURSE_COLORS) {
            View dot = new View(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(gap);
            dot.setLayoutParams(lp);
            dot.setContentDescription(c.getString(R.string.color));
            dot.setTag(col);
            dot.setOnClickListener(v -> {
                selected[0] = col;
                for (View o : dots) paintDot(o, (int) o.getTag(), (int) o.getTag() == selected[0]);
            });
            dots.add(dot);
            row.addView(dot);
        }
        for (View o : dots) paintDot(o, (int) o.getTag(), (int) o.getTag() == selected[0]);
    }

    private static void paintDot(View v, int color, boolean selected) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(color);
        if (selected) g.setStroke(Ui.dp(v.getContext(), 3), Ui.attr(v.getContext(), com.google.android.material.R.attr.colorOnSurface));
        v.setBackground(g);
        v.setScaleX(selected ? 1.1f : 1f);
        v.setScaleY(selected ? 1.1f : 1f);
    }

    // ------------------------------------------------------------------ assessment

    public static void editAssessment(AppCompatActivity a, Course course, Assessment existing) {
        editAssessment(a, course, existing, null, null);
    }

    /** @param presetName name and type to start a new assessment with (e.g. from a finished exam) */
    public static void editAssessment(AppCompatActivity a, Course course, Assessment existing, String presetName, String presetType) {
        Repository repo = Repository.get(a);
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_assessment, null);
        TextInputLayout nameL = v.findViewById(R.id.d_name_layout);
        TextInputEditText name = v.findViewById(R.id.d_name);
        MaterialAutoCompleteTextView type = v.findViewById(R.id.d_type);
        TextInputLayout scoreL = v.findViewById(R.id.d_score_layout);
        TextInputEditText score = v.findViewById(R.id.d_score);
        TextInputLayout outL = v.findViewById(R.id.d_outof_layout);
        TextInputEditText outOf = v.findViewById(R.id.d_outof);
        TextInputLayout weightL = v.findViewById(R.id.d_weight_layout);
        TextInputEditText weight = v.findViewById(R.id.d_weight);
        for (TextInputLayout l : new TextInputLayout[]{nameL, scoreL, outL, weightL}) clearErrorOnType(l);

        String[] types = new String[Assessment.TYPES.length];
        for (int i = 0; i < types.length; i++) types[i] = Labels.assessmentType(a, Assessment.TYPES[i]);
        type.setSimpleItems(types);
        final int[] typeIdx = {0};
        final String[] autoName = {null};

        double otherWeights = 0;
        for (Assessment x : course.assessments) if (x != existing) otherWeights += Math.max(0, x.weight);
        final double others = otherWeights;
        Runnable updateWeightHelper = () -> {
            double w = Fmt.parse(text(weight));
            if (Double.isNaN(w)) w = Fmt.parse(text(outOf));
            double total = others + (Double.isNaN(w) ? 0 : w);
            weightL.setHelperText(a.getString(R.string.weight_helper_total, Fmt.num(total), Fmt.num(course.totalMarks)));
        };

        if (existing != null) {
            name.setText(existing.name);
            for (int i = 0; i < Assessment.TYPES.length; i++) if (Assessment.TYPES[i].equals(existing.type)) typeIdx[0] = i;
            if (existing.isGraded()) setNum(score, existing.score);
            setNum(outOf, existing.outOf);
            if (Math.abs(existing.weight - existing.outOf) > 1e-9) setNum(weight, existing.weight);
        } else {
            double remaining = Math.max(0, course.totalMarks - others);
            boolean hasFinal = false;
            for (Assessment x : course.assessments) if (Assessment.FINAL.equals(x.type)) hasFinal = true;
            typeIdx[0] = course.assessments.isEmpty() ? 1 : 0; // midterm first, then quizzes
            if (!hasFinal && remaining > 0 && remaining <= 60 && !course.assessments.isEmpty() && others >= 40) {
                typeIdx[0] = 6; // the final is probably what is left
                setNum(outOf, remaining);
            } else {
                outOf.setText(typeIdx[0] == 1 ? "30" : "10");
            }
            if (presetType != null) {
                for (int i = 0; i < Assessment.TYPES.length; i++) if (Assessment.TYPES[i].equals(presetType)) typeIdx[0] = i;
            }
            autoName[0] = presetName != null ? presetName : autoName(a, course, Assessment.TYPES[typeIdx[0]]);
            name.setText(autoName[0]);
        }
        type.setText(types[typeIdx[0]], false);
        type.setOnItemClickListener((p, view, pos, id) -> {
            typeIdx[0] = pos;
            if (existing == null && (text(name).isEmpty() || text(name).equals(autoName[0]))) {
                autoName[0] = autoName(a, course, Assessment.TYPES[pos]);
                name.setText(autoName[0]);
            }
        });
        outOf.addTextChangedListener(new SimpleWatcher(s -> updateWeightHelper.run()));
        weight.addTextChangedListener(new SimpleWatcher(s -> updateWeightHelper.run()));
        updateWeightHelper.run();

        Runnable delete = existing == null ? null : () -> {
            course.assessments.remove(existing);
            repo.save();
        };
        form(a, a.getString(existing == null ? R.string.add_mark : R.string.edit_mark), v, R.string.save, () -> {
            String n = text(name);
            String sTxt = text(score);
            double sc = sTxt.isEmpty() ? Double.NaN : Fmt.parse(sTxt);
            double out = Fmt.parse(text(outOf));
            String wTxt = text(weight);
            double w = wTxt.isEmpty() ? out : Fmt.parse(wTxt);
            boolean ok = true;
            if (n.isEmpty()) { error(nameL, R.string.err_required); ok = false; }
            if (Double.isNaN(out) || out <= 0) { error(outL, R.string.err_positive); ok = false; }
            if (!sTxt.isEmpty() && (Double.isNaN(sc) || sc < 0)) { error(scoreL, R.string.err_number); ok = false; }
            else if (!Double.isNaN(sc) && !Double.isNaN(out) && sc > out + 1e-9) { error(scoreL, R.string.err_score_above_max); ok = false; }
            if (Double.isNaN(w) || w < 0) { error(weightL, R.string.err_number); ok = false; }
            if (!ok) return false;
            Assessment x = existing != null ? existing : new Assessment();
            x.name = n;
            x.type = Assessment.TYPES[typeIdx[0]];
            x.score = sc;
            x.outOf = out;
            x.weight = w;
            if (existing == null) course.assessments.add(x);
            repo.save();
            return true;
        }, delete);
    }

    private static String autoName(Context c, Course course, String type) {
        int n = 1;
        for (Assessment x : course.assessments) if (type.equals(x.type)) n++;
        String label = Labels.assessmentType(c, type);
        boolean single = Assessment.FINAL.equals(type) || Assessment.PROJECT.equals(type) || Assessment.PARTICIPATION.equals(type);
        return single && n == 1 ? label : label + " " + n;
    }

    // ------------------------------------------------------------------ attendance

    /**
     * Records today's attendance. Repeated taps never add a second absence for the same day: the
     * result says whether the record was added, updated (another status replaced) or already there.
     */
    public static Course.Recorded quickRecord(Context ctx, Course course, String status) {
        Repository repo = Repository.get(ctx);
        AppData d = repo.data();
        Semester s = d.semesterOf(course.id);
        AttendanceCalculator.Status before = AttendanceCalculator.compute(course, s, d.settings).status;
        Course.Recorded r = course.recordAttendance(Fmt.today(), status);
        if (r.result != Course.RecordResult.DUPLICATE) {
            repo.save();
            Notifier.attendanceChanged(ctx, course, s, d.settings, before);
        }
        return r;
    }

    /** Message for a quick record: added, changed, or "already recorded today". */
    public static String recordedMessage(Context ctx, Course c, Course.Recorded r) {
        if (r.result == Course.RecordResult.DUPLICATE) {
            if (AttendanceRecord.ABSENT.equals(r.record.status)) {
                return ctx.getString(R.string.absence_already_today, c.displayName());
            }
            return ctx.getString(R.string.status_already_today, Labels.status(ctx, r.record.status), c.displayName());
        }
        if (r.result == Course.RecordResult.UPDATED) {
            return ctx.getString(R.string.record_changed_today, c.displayName(), Labels.status(ctx, r.record.status));
        }
        return ctx.getString(AttendanceRecord.PRESENT.equals(r.record.status) ? R.string.recorded_present
                : AttendanceRecord.ABSENT.equals(r.record.status) ? R.string.recorded_absent : R.string.recorded_status_course,
                c.displayName(), Labels.status(ctx, r.record.status));
    }

    /** Sets the number of absences recorded without a date. */
    public static void editUndatedAbsences(AppCompatActivity a, Course course) {
        numbers(a, a.getString(R.string.undated_absences), a.getString(R.string.undated_absences_message),
                R.string.undated_absences_count, course.undatedAbsences, 0, Double.NaN, false, (n, x) -> {
                    if (Double.isNaN(n)) n = 0;
                    if (n < 0 || n > 200 || n != Math.floor(n)) return false;
                    Repository repo = Repository.get(a);
                    AppData d = repo.data();
                    Semester s = d.semesterOf(course.id);
                    AttendanceCalculator.Status before = AttendanceCalculator.compute(course, s, d.settings).status;
                    course.undatedAbsences = (int) n;
                    repo.save();
                    Notifier.attendanceChanged(a, course, s, d.settings, before);
                    return true;
                });
    }

    public static void editRecord(AppCompatActivity a, Course course, AttendanceRecord existing, String defaultStatus) {
        Repository repo = Repository.get(a);
        AppData d = repo.data();
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_attendance, null);
        MaterialButtonToggleGroup group = v.findViewById(R.id.d_status);
        TextInputEditText date = v.findViewById(R.id.d_date);
        TextInputLayout sessionsL = v.findViewById(R.id.d_sessions_layout);
        TextInputEditText sessions = v.findViewById(R.id.d_sessions);
        TextInputEditText note = v.findViewById(R.id.d_note);
        clearErrorOnType(sessionsL);
        int[] ids = {R.id.d_st_present, R.id.d_st_absent, R.id.d_st_late, R.id.d_st_excused};
        final String[] iso = {existing != null ? existing.date : Fmt.today()};
        String status = existing != null ? existing.status : defaultStatus;
        for (int i = 0; i < 4; i++) if (AttendanceRecord.STATUSES[i].equals(status)) group.check(ids[i]);
        if (group.getCheckedButtonId() == View.NO_ID) group.check(R.id.d_st_absent);
        sessions.setText(String.valueOf(existing != null ? existing.sessions : 1));
        if (existing != null) note.setText(existing.note);
        date.setText(Fmt.date(a, iso[0]));
        date.setOnClickListener(x -> {
            long sel;
            try {
                sel = LocalDate.parse(iso[0]).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            } catch (Exception e) {
                sel = MaterialDatePicker.todayInUtcMilliseconds();
            }
            MaterialDatePicker<Long> p = MaterialDatePicker.Builder.datePicker()
                    .setTitleText(R.string.date).setSelection(sel).build();
            p.addOnPositiveButtonClickListener(ms -> {
                iso[0] = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString();
                date.setText(Fmt.date(a, iso[0]));
            });
            p.show(a.getSupportFragmentManager(), "date");
        });
        Runnable delete = existing == null ? null : () -> {
            course.attendance.remove(existing);
            repo.save();
        };
        TextInputLayout dateL = v.findViewById(R.id.d_date_layout);
        form(a, a.getString(existing == null ? R.string.record_attendance : R.string.edit_record), v, R.string.save, () -> {
            double n = Fmt.parse(text(sessions));
            if (Double.isNaN(n) || n < 1 || n > 20) {
                error(sessionsL, R.string.err_sessions);
                return false;
            }
            // One record per course per date: a second one for the same day is refused, so an absence
            // can never be counted twice. (A double lecture is one record with 2 sessions.)
            AttendanceRecord clash = course.recordOn(iso[0], existing);
            if (clash != null && (existing == null || !iso[0].equals(existing.date))) {
                dateL.setError(a.getString(R.string.err_record_same_day, Labels.status(a, clash.status)));
                return false;
            }
            Semester s = d.semesterOf(course.id);
            AttendanceCalculator.Status before = AttendanceCalculator.compute(course, s, d.settings).status;
            AttendanceRecord r = existing != null ? existing : new AttendanceRecord();
            int checked = group.getCheckedButtonId();
            for (int i = 0; i < 4; i++) if (ids[i] == checked) r.status = AttendanceRecord.STATUSES[i];
            r.date = iso[0];
            r.sessions = (int) Math.round(n);
            r.note = text(note);
            if (existing == null) course.attendance.add(r);
            repo.save();
            Notifier.attendanceChanged(a, course, s, d.settings, before);
            return true;
        }, delete);
    }

    // ------------------------------------------------------------------ lecture

    public static void editLecture(AppCompatActivity a, Semester sem, Course fixedCourse, LectureSlot existing, int defaultDay) {
        Repository repo = Repository.get(a);
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_lecture, null);
        TextInputLayout courseL = v.findViewById(R.id.d_course_layout);
        MaterialAutoCompleteTextView courseDd = v.findViewById(R.id.d_course);
        MaterialAutoCompleteTextView day = v.findViewById(R.id.d_day);
        TextInputEditText start = v.findViewById(R.id.d_start);
        TextInputLayout endL = v.findViewById(R.id.d_end_layout);
        TextInputEditText end = v.findViewById(R.id.d_end);
        TextInputEditText room = v.findViewById(R.id.d_room);
        MaterialAutoCompleteTextView kind = v.findViewById(R.id.d_kind);

        List<Course> courses = sem.courses;
        final int[] courseIdx = {fixedCourse != null ? courses.indexOf(fixedCourse) : (courses.isEmpty() ? -1 : 0)};
        if (fixedCourse != null) {
            courseL.setVisibility(View.GONE);
        } else {
            String[] names = new String[courses.size()];
            for (int i = 0; i < names.length; i++) names[i] = courses.get(i).displayName();
            courseDd.setSimpleItems(names);
            if (courseIdx[0] >= 0) courseDd.setText(names[courseIdx[0]], false);
            courseDd.setOnItemClickListener((p, view, pos, id) -> {
                courseIdx[0] = pos;
                courseL.setError(null);
            });
        }

        int[] days = orderedDays();
        String[] dayNames = new String[7];
        for (int i = 0; i < 7; i++) dayNames[i] = Fmt.dayName(a, days[i], false);
        day.setSimpleItems(dayNames);
        final int[] dayVal = {existing != null ? existing.day : defaultDay};
        for (int i = 0; i < 7; i++) if (days[i] == dayVal[0]) day.setText(dayNames[i], false);
        day.setOnItemClickListener((p, view, pos, id) -> dayVal[0] = days[pos]);

        String[] kinds = new String[LectureSlot.KINDS.length];
        for (int i = 0; i < kinds.length; i++) kinds[i] = Labels.kind(a, LectureSlot.KINDS[i]);
        kind.setSimpleItems(kinds);
        final int[] kindIdx = {0};
        if (existing != null) for (int i = 0; i < kinds.length; i++) if (LectureSlot.KINDS[i].equals(existing.kind)) kindIdx[0] = i;
        kind.setText(kinds[kindIdx[0]], false);
        kind.setOnItemClickListener((p, view, pos, id) -> kindIdx[0] = pos);

        final int[] times = {existing != null ? existing.start : 8 * 60, existing != null ? existing.end : 9 * 60 + 30};
        start.setText(Fmt.time(a, times[0]));
        end.setText(Fmt.time(a, times[1]));
        if (existing != null) room.setText(existing.room);
        View.OnClickListener pick = x -> {
            boolean isStart = x == start;
            int cur = isStart ? times[0] : times[1];
            MaterialTimePicker p = new MaterialTimePicker.Builder()
                    .setTimeFormat(android.text.format.DateFormat.is24HourFormat(a) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                    .setHour(cur / 60).setMinute(cur % 60)
                    .setTitleText(isStart ? R.string.start_time : R.string.end_time)
                    .build();
            p.addOnPositiveButtonClickListener(b -> {
                int m = p.getHour() * 60 + p.getMinute();
                if (isStart) {
                    int dur = times[1] - times[0];
                    times[0] = m;
                    times[1] = Math.min(24 * 60 - 1, m + Math.max(30, dur));
                } else {
                    times[1] = m;
                }
                start.setText(Fmt.time(a, times[0]));
                end.setText(Fmt.time(a, times[1]));
                endL.setError(null);
            });
            p.show(a.getSupportFragmentManager(), "time");
        };
        start.setOnClickListener(pick);
        end.setOnClickListener(pick);

        Runnable delete = existing == null ? null : () -> {
            for (Course c : sem.courses) c.lectures.remove(existing);
            repo.save();
        };
        form(a, a.getString(existing == null ? R.string.add_lecture : R.string.edit_lecture), v, R.string.save, () -> {
            boolean ok = true;
            if (courseIdx[0] < 0) { error(courseL, R.string.err_choose_course); ok = false; }
            if (times[1] <= times[0]) { error(endL, R.string.err_end_before_start); ok = false; }
            if (!ok) return false;
            Course target = courses.get(courseIdx[0]);
            LectureSlot s = existing != null ? existing : new LectureSlot();
            s.day = dayVal[0];
            s.start = times[0];
            s.end = times[1];
            s.room = text(room);
            s.kind = LectureSlot.KINDS[kindIdx[0]];
            if (existing != null) {
                for (Course c : sem.courses) if (c != target) c.lectures.remove(existing);
                if (!target.lectures.contains(existing)) target.lectures.add(existing);
            } else {
                target.lectures.add(s);
            }
            repo.save();
            return true;
        }, delete);
    }

    // ------------------------------------------------------------------ task

    /**
     * Adds or edits a reminder/task. A task needs no date; reminders are offered only once it has a
     * date, and same-day reminders only once it also has a time.
     *
     * @param presetCourse  course to link a new task to (may be null)
     * @param presetCategory category for a new task (may be null)
     */
    public static void editTask(AppCompatActivity a, Task existing, Course presetCourse, String presetCategory) {
        Repository repo = Repository.get(a);
        AppData d = repo.data();
        Settings st = d.settings;
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_task, null);
        MaterialButtonToggleGroup category = v.findViewById(R.id.d_category);
        TextInputLayout titleL = v.findViewById(R.id.d_title_layout);
        TextInputEditText title = v.findViewById(R.id.d_title);
        TextInputLayout kindL = v.findViewById(R.id.d_kind_layout);
        MaterialAutoCompleteTextView kind = v.findViewById(R.id.d_kind);
        MaterialAutoCompleteTextView courseDd = v.findViewById(R.id.d_course);
        TextInputLayout dateL = v.findViewById(R.id.d_date_layout);
        TextInputEditText date = v.findViewById(R.id.d_date);
        View timesRow = v.findViewById(R.id.d_times);
        TextInputLayout timeL = v.findViewById(R.id.d_time_layout);
        TextInputEditText time = v.findViewById(R.id.d_time);
        TextInputLayout endL = v.findViewById(R.id.d_end_layout);
        TextInputEditText end = v.findViewById(R.id.d_end);
        TextInputLayout roomL = v.findViewById(R.id.d_room_layout);
        TextInputEditText room = v.findViewById(R.id.d_room);
        TextInputEditText notes = v.findViewById(R.id.d_notes);
        TextView noReminder = v.findViewById(R.id.d_no_reminder);
        View reminderBox = v.findViewById(R.id.d_reminder_box);
        MaterialSwitch custom = v.findViewById(R.id.d_custom);
        TextView defaults = v.findViewById(R.id.d_default_summary);
        View customBox = v.findViewById(R.id.d_custom_box);
        com.google.android.material.chip.ChipGroup daysGroup = v.findViewById(R.id.d_days);
        com.google.android.material.chip.ChipGroup minutesGroup = v.findViewById(R.id.d_minutes);
        View minutesTitle = v.findViewById(R.id.d_minutes_title);
        View minutesHint = v.findViewById(R.id.d_minutes_hint);
        MaterialCheckBox done = v.findViewById(R.id.d_done);
        clearErrorOnType(titleL);

        // Category
        int[] catIds = {R.id.d_cat_exam, R.id.d_cat_assignment, R.id.d_cat_practical, R.id.d_cat_general};
        String startCat = existing != null ? existing.category : presetCategory != null ? presetCategory : Task.GENERAL;
        for (int i = 0; i < catIds.length; i++) if (Task.CATEGORIES[i].equals(startCat)) category.check(catIds[i]);
        if (category.getCheckedButtonId() == View.NO_ID) category.check(R.id.d_cat_general);

        // Exam kind
        String[] kinds = new String[Task.KINDS.length];
        for (int i = 0; i < kinds.length; i++) kinds[i] = TaskUi.kindLabel(a, Task.KINDS[i]);
        kind.setSimpleItems(kinds);
        final int[] kindIdx = {0};
        if (existing != null) for (int i = 0; i < kinds.length; i++) if (Task.KINDS[i].equals(existing.kind)) kindIdx[0] = i;
        kind.setText(kinds[kindIdx[0]], false);
        kind.setOnItemClickListener((p, view, pos, id) -> kindIdx[0] = pos);

        // Course: none, the current semester's courses, and the linked course if it is elsewhere.
        List<Course> courses = new ArrayList<>();
        Semester cur = d.currentSemester();
        if (cur != null) courses.addAll(cur.courses);
        String linked = existing != null ? existing.courseId : presetCourse != null ? presetCourse.id : null;
        Course linkedCourse = linked == null ? null : d.findCourse(linked);
        if (linkedCourse != null && !courses.contains(linkedCourse)) courses.add(0, linkedCourse);
        String[] courseNames = new String[courses.size() + 1];
        courseNames[0] = a.getString(R.string.task_no_course);
        for (int i = 0; i < courses.size(); i++) courseNames[i + 1] = courses.get(i).displayName();
        courseDd.setSimpleItems(courseNames);
        final int[] courseIdx = {linkedCourse == null ? 0 : courses.indexOf(linkedCourse) + 1};
        courseDd.setText(courseNames[courseIdx[0]], false);
        courseDd.setOnItemClickListener((p, view, pos, id) -> courseIdx[0] = pos);

        // Date and times; null / NO_TIME mean "not set".
        final String[] iso = {existing != null ? existing.date : null};
        final int[] times = {existing != null ? existing.time : Task.NO_TIME, existing != null ? existing.endTime : Task.NO_TIME};
        List<Integer> selDays = new ArrayList<>(existing != null && existing.customReminders ? existing.daysBefore : st.examDaysBefore);
        List<Integer> selMinutes = new ArrayList<>(existing != null && existing.customReminders ? existing.minutesBefore : st.examMinutesBefore);
        offsetChips(daysGroup, TaskUi.DAY_OPTIONS, selDays, true);
        offsetChips(minutesGroup, TaskUi.MINUTE_OPTIONS, selMinutes, false);
        custom.setChecked(existing != null && existing.customReminders);
        ((TextView) v.findViewById(R.id.d_days_title)).setText(a.getString(R.string.exam_days_before_title_time, Fmt.time(a, st.examDayTime)));

        Runnable refresh = () -> {
            int checked = category.getCheckedButtonId();
            boolean exam = checked == R.id.d_cat_exam;
            boolean event = exam || checked == R.id.d_cat_practical;
            boolean hasDate = iso[0] != null;
            boolean hasTime = hasDate && times[0] >= 0;
            kindL.setVisibility(exam ? View.VISIBLE : View.GONE);
            titleL.setHelperText(exam ? a.getString(R.string.task_title_helper_exam) : null);
            roomL.setVisibility(event ? View.VISIBLE : View.GONE);
            endL.setVisibility(event && hasTime ? View.VISIBLE : View.INVISIBLE);
            timesRow.setVisibility(hasDate ? View.VISIBLE : View.GONE);
            dateL.setHint(event ? R.string.task_date_event : R.string.task_date);
            date.setText(hasDate ? Fmt.date(a, iso[0]) : "");
            time.setText(hasTime ? Fmt.time(a, times[0]) : "");
            end.setText(hasTime && times[1] > times[0] ? Fmt.time(a, times[1]) : "");
            dateL.setEndIconVisible(hasDate);
            timeL.setEndIconVisible(hasTime);
            // Reminders only exist for dated entries: never suggest an alarm for an undated one.
            noReminder.setVisibility(hasDate ? View.GONE : View.VISIBLE);
            reminderBox.setVisibility(hasDate ? View.VISIBLE : View.GONE);
            List<Integer> noMinutes = new ArrayList<>();
            defaults.setText(a.getString(st.examReminders ? R.string.exam_default_reminders : R.string.task_reminders_off,
                    TaskUi.summary(a, st.examDaysBefore, hasTime ? st.examMinutesBefore : noMinutes)));
            customBox.setVisibility(custom.isChecked() ? View.VISIBLE : View.GONE);
            defaults.setVisibility(custom.isChecked() ? View.GONE : View.VISIBLE);
            minutesTitle.setVisibility(hasTime ? View.VISIBLE : View.GONE);
            minutesGroup.setVisibility(hasTime ? View.VISIBLE : View.GONE);
            minutesHint.setVisibility(hasTime ? View.GONE : View.VISIBLE);
        };
        category.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) refresh.run();
        });
        custom.setOnCheckedChangeListener((b, on) -> refresh.run());

        dateL.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        dateL.setEndIconDrawable(R.drawable.ic_close);
        dateL.setEndIconContentDescription(R.string.task_clear_date);
        dateL.setEndIconOnClickListener(x -> {
            iso[0] = null;
            times[0] = Task.NO_TIME;
            times[1] = Task.NO_TIME;
            refresh.run();
        });
        timeL.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        timeL.setEndIconDrawable(R.drawable.ic_close);
        timeL.setEndIconContentDescription(R.string.task_clear_time);
        timeL.setEndIconOnClickListener(x -> {
            times[0] = Task.NO_TIME;
            times[1] = Task.NO_TIME;
            refresh.run();
        });
        endL.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        endL.setEndIconDrawable(R.drawable.ic_close);
        endL.setEndIconOnClickListener(x -> {
            times[1] = Task.NO_TIME;
            refresh.run();
        });
        date.setOnClickListener(x -> {
            long sel;
            try {
                sel = LocalDate.parse(iso[0]).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            } catch (Exception e) {
                sel = MaterialDatePicker.todayInUtcMilliseconds();
            }
            MaterialDatePicker<Long> p = MaterialDatePicker.Builder.datePicker().setTitleText(R.string.task_date).setSelection(sel).build();
            p.addOnPositiveButtonClickListener(ms -> {
                iso[0] = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString();
                refresh.run();
            });
            p.show(a.getSupportFragmentManager(), "task-date");
        });
        View.OnClickListener pick = x -> {
            boolean isStart = x == time;
            int curT = isStart ? (times[0] >= 0 ? times[0] : 9 * 60) : (times[1] > times[0] ? times[1] : Math.min(24 * 60 - 1, times[0] + 120));
            MaterialTimePicker p = new MaterialTimePicker.Builder()
                    .setTimeFormat(android.text.format.DateFormat.is24HourFormat(a) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                    .setHour(curT / 60).setMinute(curT % 60)
                    .setTitleText(isStart ? R.string.task_time : R.string.end_time)
                    .build();
            p.addOnPositiveButtonClickListener(b -> {
                int m = p.getHour() * 60 + p.getMinute();
                if (isStart) {
                    int dur = times[1] > times[0] && times[0] >= 0 ? times[1] - times[0] : 0;
                    times[0] = m;
                    times[1] = dur > 0 && m + dur < 24 * 60 ? m + dur : Task.NO_TIME;
                } else {
                    times[1] = m;
                }
                endL.setError(null);
                refresh.run();
            });
            p.show(a.getSupportFragmentManager(), "task-time");
        };
        time.setOnClickListener(pick);
        end.setOnClickListener(pick);

        if (existing != null) {
            title.setText(existing.title);
            room.setText(existing.room);
            notes.setText(existing.notes);
            done.setChecked(existing.done);
        } else {
            done.setVisibility(View.GONE);
        }
        refresh.run();

        Runnable delete = existing == null ? null : () -> {
            d.tasks.remove(existing);
            repo.save();
        };
        form(a, a.getString(existing == null ? R.string.add_task : R.string.edit_task), v, R.string.save, () -> {
            int checked = category.getCheckedButtonId();
            String cat = Task.GENERAL;
            for (int i = 0; i < catIds.length; i++) if (catIds[i] == checked) cat = Task.CATEGORIES[i];
            String t = text(title);
            boolean event = Task.EXAM.equals(cat) || Task.PRACTICAL.equals(cat);
            if (t.isEmpty() && !Task.EXAM.equals(cat)) {
                error(titleL, R.string.err_required);
                return false;
            }
            if (event && times[1] >= 0 && times[1] <= times[0]) {
                error(endL, R.string.err_end_before_start);
                return false;
            }
            Task x = existing != null ? existing : new Task();
            x.category = cat;
            x.title = t;
            x.kind = Task.EXAM.equals(cat) ? Task.KINDS[kindIdx[0]] : Task.KIND_OTHER;
            x.courseId = courseIdx[0] == 0 ? null : courses.get(courseIdx[0] - 1).id;
            x.date = iso[0];
            x.time = iso[0] != null ? times[0] : Task.NO_TIME;
            x.endTime = event && x.time >= 0 && times[1] > x.time ? times[1] : Task.NO_TIME;
            x.room = event ? text(room) : "";
            x.notes = text(notes);
            x.done = existing != null && done.isChecked();
            x.customReminders = iso[0] != null && custom.isChecked();
            x.daysBefore.clear();
            x.minutesBefore.clear();
            if (x.customReminders) {
                x.daysBefore.addAll(selDays);
                x.minutesBefore.addAll(selMinutes);
            }
            if (existing == null) d.tasks.add(x);
            repo.save();
            return true;
        }, delete);
    }

    /** Fills a chip group with reminder offsets; {@code selected} is kept in sync with the checked chips. */
    static void offsetChips(com.google.android.material.chip.ChipGroup group, int[] options, List<Integer> selected, boolean days) {
        Context c = group.getContext();
        group.removeAllViews();
        List<Integer> all = new ArrayList<>();
        for (int o : options) all.add(o);
        for (int s : selected) if (!all.contains(s)) all.add(s);
        all.sort((x, y) -> days ? x - y : y - x);
        for (int value : all) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(c);
            chip.setText(days ? TaskUi.dayLabel(c, value) : TaskUi.minuteLabel(c, value));
            chip.setCheckable(true);
            chip.setChecked(selected.contains(value));
            chip.setOnCheckedChangeListener((b, on) -> {
                selected.remove(Integer.valueOf(value));
                if (on) selected.add(value);
            });
            group.addView(chip);
        }
    }

    // ------------------------------------------------------------------ grade definitions

    public static void editGradeDef(AppCompatActivity a, GradeScale scale, GradeDef existing, Consumer<GradeDef> onSaved, Runnable onDelete) {
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_grade_def, null);
        TextInputLayout letterL = v.findViewById(R.id.d_letter_layout);
        TextInputEditText letter = v.findViewById(R.id.d_letter);
        TextInputLayout pointsL = v.findViewById(R.id.d_points_layout);
        TextInputEditText points = v.findViewById(R.id.d_points);
        TextInputLayout minL = v.findViewById(R.id.d_min_layout);
        TextInputEditText min = v.findViewById(R.id.d_min);
        for (TextInputLayout l : new TextInputLayout[]{letterL, pointsL, minL}) clearErrorOnType(l);
        if (existing != null) {
            letter.setText(existing.letter);
            setNum(points, existing.points);
            setNum(min, existing.minPercent);
        }
        form(a, a.getString(existing == null ? R.string.add_grade : R.string.edit_grade), v, R.string.save, () -> {
            String l = text(letter);
            double p = Fmt.parse(text(points));
            double m = Fmt.parse(text(min));
            boolean ok = true;
            if (l.isEmpty() || l.length() > 4) { error(letterL, R.string.err_letter); ok = false; }
            else {
                for (GradeDef g : scale.grades) {
                    if (g != existing && g.letter.equalsIgnoreCase(l)) { error(letterL, R.string.err_letter_exists); ok = false; }
                }
            }
            if (Double.isNaN(p) || p < 0 || p > 10) { error(pointsL, R.string.err_points); ok = false; }
            if (Double.isNaN(m) || m < 0 || m > 100) { error(minL, R.string.err_percent); ok = false; }
            if (!ok) return false;
            onSaved.accept(new GradeDef(l, p, m));
            return true;
        }, onDelete);
    }

    // ------------------------------------------------------------------ generic inputs

    public interface NumbersCallback {
        boolean accept(double first, double second);
    }

    /**
     * One or two numeric fields. Pass {@code hint2 == 0} for a single field.
     * Empty fields are reported as NaN; the callback returns false to keep the dialog open.
     */
    public static void numbers(AppCompatActivity a, CharSequence title, CharSequence message,
                               @StringRes int hint1, double value1, @StringRes int hint2, double value2,
                               boolean decimals, NumbersCallback cb) {
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_input, null);
        TextView msg = v.findViewById(R.id.d_message);
        TextInputLayout l1 = v.findViewById(R.id.d_value1_layout);
        TextInputEditText e1 = v.findViewById(R.id.d_value1);
        TextInputLayout l2 = v.findViewById(R.id.d_value2_layout);
        TextInputEditText e2 = v.findViewById(R.id.d_value2);
        if (message != null) {
            msg.setVisibility(View.VISIBLE);
            msg.setText(message);
        }
        int type = InputType.TYPE_CLASS_NUMBER | (decimals ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0);
        l1.setHint(hint1);
        e1.setInputType(type);
        setNum(e1, value1);
        clearErrorOnType(l1);
        if (hint2 != 0) {
            l2.setHint(hint2);
            e2.setInputType(type);
            setNum(e2, value2);
            clearErrorOnType(l2);
        } else {
            l2.setVisibility(View.GONE);
        }
        form(a, title, v, R.string.save, () -> {
            double a1 = Fmt.parse(text(e1));
            double a2 = hint2 != 0 ? Fmt.parse(text(e2)) : Double.NaN;
            boolean ok = cb.accept(a1, a2);
            if (!ok) l1.setError(a.getString(R.string.err_invalid_value));
            return ok;
        }, null);
        e1.requestFocus();
    }

    public interface TextsCallback {
        void accept(String first, String second);
    }

    public static void texts(AppCompatActivity a, CharSequence title, @StringRes int hint1, String value1,
                             @StringRes int hint2, String value2, TextsCallback cb) {
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_input, null);
        TextInputLayout l1 = v.findViewById(R.id.d_value1_layout);
        TextInputEditText e1 = v.findViewById(R.id.d_value1);
        TextInputLayout l2 = v.findViewById(R.id.d_value2_layout);
        TextInputEditText e2 = v.findViewById(R.id.d_value2);
        int type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
        l1.setHint(hint1);
        e1.setInputType(type);
        e1.setText(value1);
        if (hint2 != 0) {
            l2.setHint(hint2);
            e2.setInputType(type);
            e2.setText(value2);
        } else {
            l2.setVisibility(View.GONE);
        }
        form(a, title, v, R.string.save, () -> {
            cb.accept(text(e1), text(e2));
            return true;
        }, null);
    }
}
