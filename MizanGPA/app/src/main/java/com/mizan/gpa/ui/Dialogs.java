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
import com.mizan.gpa.data.Exam;
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
            if (!ok) return false;
            Course course = existing != null ? existing : new Course();
            course.name = n;
            course.code = c;
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

    /** Adds a record for today with the given status and returns it (for undo). */
    public static AttendanceRecord quickRecord(Context ctx, Course course, String status) {
        Repository repo = Repository.get(ctx);
        AppData d = repo.data();
        Semester s = d.semesterOf(course.id);
        AttendanceCalculator.Status before = AttendanceCalculator.compute(course, s, d.settings).status;
        AttendanceRecord r = new AttendanceRecord();
        r.date = Fmt.today();
        r.status = status;
        course.attendance.add(r);
        repo.save();
        Notifier.attendanceChanged(ctx, course, s, d.settings, before);
        return r;
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
        form(a, a.getString(existing == null ? R.string.record_attendance : R.string.edit_record), v, R.string.save, () -> {
            double n = Fmt.parse(text(sessions));
            if (Double.isNaN(n) || n < 1 || n > 20) {
                error(sessionsL, R.string.err_sessions);
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

    // ------------------------------------------------------------------ exam

    public static void editExam(AppCompatActivity a, Semester sem, Course fixedCourse, Exam existing) {
        Repository repo = Repository.get(a);
        Settings st = repo.settings();
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_exam, null);
        TextInputLayout courseL = v.findViewById(R.id.d_course_layout);
        MaterialAutoCompleteTextView courseDd = v.findViewById(R.id.d_course);
        TextInputEditText title = v.findViewById(R.id.d_title);
        MaterialAutoCompleteTextView type = v.findViewById(R.id.d_type);
        TextInputEditText date = v.findViewById(R.id.d_date);
        TextInputEditText start = v.findViewById(R.id.d_start);
        TextInputLayout endL = v.findViewById(R.id.d_end_layout);
        TextInputEditText end = v.findViewById(R.id.d_end);
        TextInputEditText room = v.findViewById(R.id.d_room);
        TextInputEditText note = v.findViewById(R.id.d_note);
        MaterialSwitch custom = v.findViewById(R.id.d_custom);
        TextView defaults = v.findViewById(R.id.d_default_summary);
        View customBox = v.findViewById(R.id.d_custom_box);
        com.google.android.material.chip.ChipGroup daysGroup = v.findViewById(R.id.d_days);
        com.google.android.material.chip.ChipGroup minutesGroup = v.findViewById(R.id.d_minutes);

        List<Course> courses = sem.courses;
        Course owner = null;
        if (existing != null) for (Course c : courses) if (c.exams.contains(existing)) owner = c;
        if (owner == null) owner = fixedCourse;
        final int[] courseIdx = {owner != null ? courses.indexOf(owner) : (courses.isEmpty() ? -1 : 0)};
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

        String[] types = new String[Exam.TYPES.length];
        for (int i = 0; i < types.length; i++) types[i] = Exams.typeLabel(a, Exam.TYPES[i]);
        type.setSimpleItems(types);
        final int[] typeIdx = {0};
        if (existing != null) for (int i = 0; i < types.length; i++) if (Exam.TYPES[i].equals(existing.type)) typeIdx[0] = i;
        type.setText(types[typeIdx[0]], false);
        type.setOnItemClickListener((p, view, pos, id) -> typeIdx[0] = pos);

        final String[] iso = {existing != null ? existing.date : LocalDate.now().plusDays(7).toString()};
        // times[1] <= times[0] means "no end time".
        final int[] times = {existing != null ? existing.start : 9 * 60, existing != null && existing.hasEnd() ? existing.end : 0};
        Runnable showTimes = () -> {
            date.setText(Fmt.date(a, iso[0]));
            start.setText(Fmt.time(a, times[0]));
            end.setText(times[1] > times[0] ? Fmt.time(a, times[1]) : "");
        };
        showTimes.run();
        if (existing != null) {
            title.setText(existing.title);
            room.setText(existing.room);
            note.setText(existing.note);
        }
        date.setOnClickListener(x -> {
            long sel;
            try {
                sel = LocalDate.parse(iso[0]).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            } catch (Exception e) {
                sel = MaterialDatePicker.todayInUtcMilliseconds();
            }
            MaterialDatePicker<Long> p = MaterialDatePicker.Builder.datePicker().setTitleText(R.string.date).setSelection(sel).build();
            p.addOnPositiveButtonClickListener(ms -> {
                iso[0] = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString();
                showTimes.run();
            });
            p.show(a.getSupportFragmentManager(), "exam-date");
        });
        View.OnClickListener pick = x -> {
            boolean isStart = x == start;
            int cur = isStart ? times[0] : (times[1] > times[0] ? times[1] : Math.min(24 * 60 - 1, times[0] + 120));
            MaterialTimePicker p = new MaterialTimePicker.Builder()
                    .setTimeFormat(android.text.format.DateFormat.is24HourFormat(a) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                    .setHour(cur / 60).setMinute(cur % 60)
                    .setTitleText(isStart ? R.string.start_time : R.string.end_time)
                    .build();
            p.addOnPositiveButtonClickListener(b -> {
                int m = p.getHour() * 60 + p.getMinute();
                if (isStart) {
                    int dur = times[1] > times[0] ? times[1] - times[0] : 0;
                    times[0] = m;
                    times[1] = dur > 0 ? Math.min(24 * 60 - 1, m + dur) : 0;
                } else {
                    times[1] = m;
                }
                showTimes.run();
                endL.setError(null);
            });
            p.show(a.getSupportFragmentManager(), "exam-time");
        };
        start.setOnClickListener(pick);
        end.setOnClickListener(pick);
        endL.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        endL.setEndIconOnClickListener(x -> {
            times[1] = 0;
            showTimes.run();
        });

        // Reminders: the defaults from Settings, or a custom set for this exam.
        defaults.setText(a.getString(R.string.exam_default_reminders, Exams.summary(a, st.examDaysBefore, st.examMinutesBefore)));
        ((TextView) v.findViewById(R.id.d_days_title)).setText(a.getString(R.string.exam_days_before_title_time, Fmt.time(a, st.examDayTime)));
        List<Integer> selDays = new ArrayList<>(existing != null && existing.customReminders ? existing.daysBefore : st.examDaysBefore);
        List<Integer> selMinutes = new ArrayList<>(existing != null && existing.customReminders ? existing.minutesBefore : st.examMinutesBefore);
        offsetChips(daysGroup, Exams.DAY_OPTIONS, selDays, true);
        offsetChips(minutesGroup, Exams.MINUTE_OPTIONS, selMinutes, false);
        custom.setChecked(existing != null && existing.customReminders);
        Runnable toggle = () -> {
            customBox.setVisibility(custom.isChecked() ? View.VISIBLE : View.GONE);
            defaults.setVisibility(custom.isChecked() ? View.GONE : View.VISIBLE);
        };
        toggle.run();
        custom.setOnCheckedChangeListener((b, on) -> toggle.run());

        Runnable delete = existing == null ? null : () -> {
            for (Course c : sem.courses) c.exams.remove(existing);
            repo.save();
        };
        form(a, a.getString(existing == null ? R.string.add_exam : R.string.edit_exam), v, R.string.save, () -> {
            if (courseIdx[0] < 0) {
                error(courseL, R.string.err_choose_course);
                return false;
            }
            Course target = courses.get(courseIdx[0]);
            Exam e = existing != null ? existing : new Exam();
            e.title = text(title);
            e.type = Exam.TYPES[typeIdx[0]];
            e.date = iso[0];
            e.start = times[0];
            e.end = times[1] > times[0] ? times[1] : 0;
            e.room = text(room);
            e.note = text(note);
            e.customReminders = custom.isChecked();
            e.daysBefore.clear();
            e.minutesBefore.clear();
            if (e.customReminders) {
                e.daysBefore.addAll(selDays);
                e.minutesBefore.addAll(selMinutes);
            }
            for (Course c : sem.courses) if (c != target) c.exams.remove(e);
            if (!target.exams.contains(e)) target.exams.add(e);
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
            chip.setText(days ? Exams.dayLabel(c, value) : Exams.minuteLabel(c, value));
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
        l2.setHint(hint2);
        e2.setInputType(type);
        e2.setText(value2);
        form(a, title, v, R.string.save, () -> {
            cb.accept(text(e1), text(e2));
            return true;
        }, null);
    }
}
