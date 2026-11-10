package com.mizan.gpa.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.mizan.gpa.R;
import com.mizan.gpa.calc.AttendanceCalculator;
import com.mizan.gpa.calc.GradeResolver;
import com.mizan.gpa.calc.MarksCalculator;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeDef;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

/** Binds the shared row and card layouts. */
public final class Rows {
    private Rows() {}

    /** Grade shown for a course: official, else expected/predicted (with its label). */
    public static final class GradeInfo {
        public GradeDef grade;
        public int label;
    }

    public static GradeInfo gradeInfo(Course c, Settings st) {
        GradeInfo g = new GradeInfo();
        GradeDef official = st.scale.find(c.finalGrade);
        if (official != null) {
            g.grade = official;
            g.label = R.string.label_final;
            return g;
        }
        GradeDef manual = st.scale.find(c.expectedGrade);
        if (manual != null) {
            g.grade = manual;
            g.label = R.string.label_expected;
            return g;
        }
        g.grade = GradeResolver.predictedFromMarks(c, st.scale, st.roundPercent);
        g.label = g.grade != null ? R.string.label_predicted : R.string.label_in_progress;
        return g;
    }

    public static View courseCard(ViewGroup parent, Course c, int index, Semester s, Settings st) {
        Context ctx = parent.getContext();
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_course, parent, false);
        Ui.roundedFill(v.findViewById(R.id.course_color), Ui.courseColor(c.color, index), 3);
        ((TextView) v.findViewById(R.id.course_name)).setText(c.displayName());
        ((TextView) v.findViewById(R.id.course_info)).setText(Labels.courseSubtitle(ctx, c));
        TextView extra = v.findViewById(R.id.course_extra);
        StringBuilder b = new StringBuilder();
        MarksCalculator.Result m = MarksCalculator.compute(c);
        if (m.hasMarks()) b.append(ctx.getString(R.string.course_marks_short, Fmt.num(m.earned), Fmt.num(m.gradedWeight)));
        if (!c.isCompleted()) {
            AttendanceCalculator.Stats a = AttendanceCalculator.compute(c, s, st);
            if (a.recorded() > 0 || a.hasTotal()) {
                if (b.length() > 0) b.append("  ·  ");
                b.append(a.hasTotal()
                        ? ctx.getString(R.string.course_absences_short, a.counted, a.allowed)
                        : ctx.getString(R.string.course_absences_count, a.counted));
            }
        }
        extra.setText(b);
        extra.setVisibility(b.length() > 0 ? View.VISIBLE : View.GONE);
        GradeInfo gi = gradeInfo(c, st);
        TextView badge = v.findViewById(R.id.course_grade);
        Ui.badge(badge, gi.grade != null ? gi.grade.letter : Fmt.DASH, Ui.gradeColor(ctx, gi.grade, st.scale));
        ((TextView) v.findViewById(R.id.course_grade_label)).setText(gi.label);
        return v;
    }

    public static View alert(ViewGroup parent, Insights.Alert a, View.OnClickListener click) {
        Context ctx = parent.getContext();
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_alert, parent, false);
        int color, bg;
        switch (a.level) {
            case DANGER:
                color = ContextCompat.getColor(ctx, R.color.status_danger);
                break;
            case WARNING:
                color = ContextCompat.getColor(ctx, R.color.status_warning);
                break;
            case SUCCESS:
                color = ContextCompat.getColor(ctx, R.color.status_excellent);
                break;
            default:
                color = Ui.attr(ctx, androidx.appcompat.R.attr.colorPrimary);
                break;
        }
        bg = Ui.withAlpha(color, 0.12f);
        MaterialCardView card = (MaterialCardView) v;
        card.setCardBackgroundColor(bg);
        ImageView icon = v.findViewById(R.id.alert_icon);
        icon.setImageResource(a.icon);
        icon.setImageTintList(Ui.csl(color));
        ((TextView) v.findViewById(R.id.alert_title)).setText(a.title);
        TextView text = v.findViewById(R.id.alert_text);
        text.setText(a.text);
        text.setVisibility(a.text == null || a.text.isEmpty() ? View.GONE : View.VISIBLE);
        if (click != null) {
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(click);
        }
        return v;
    }

    /** Exam card with a date block, countdown badge and an overflow menu. */
    public static View examCard(ViewGroup parent, androidx.appcompat.app.AppCompatActivity a, Semester sem, Exams.Entry en, boolean showCourse) {
        Context ctx = parent.getContext();
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_exam, parent, false);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        boolean past = Exams.isPast(en.exam, now);
        int color = en.color();
        View box = v.findViewById(R.id.x_date_box);
        Ui.roundedFill(box, Ui.withAlpha(color, past ? 0.10f : 0.18f), 14);
        TextView day = v.findViewById(R.id.x_day);
        TextView month = v.findViewById(R.id.x_month);
        day.setText(String.valueOf(en.start.getDayOfMonth()));
        month.setText(en.start.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Fmt.locale(ctx)));
        int ink = Ui.blend(color, Ui.attr(ctx, com.google.android.material.R.attr.colorOnSurface), 0.45f);
        day.setTextColor(ink);
        month.setTextColor(ink);
        TextView course = v.findViewById(R.id.x_course);
        TextView title = v.findViewById(R.id.x_title);
        String name = Exams.title(ctx, en.exam);
        String typed = name.equals(Exams.typeLabel(ctx, en.exam.type)) ? name : name + " · " + Exams.typeLabel(ctx, en.exam.type);
        if (showCourse) {
            course.setText(en.course.displayName());
            title.setText(typed);
        } else {
            course.setText(name);
            title.setText(Exams.typeLabel(ctx, en.exam.type));
            title.setVisibility(name.equals(Exams.typeLabel(ctx, en.exam.type)) ? View.GONE : View.VISIBLE);
        }
        String when = Exams.when(ctx, en.exam);
        if (en.exam.room != null && !en.exam.room.trim().isEmpty()) when += " · " + en.exam.room.trim();
        ((TextView) v.findViewById(R.id.x_when)).setText(when);
        Ui.badge(v.findViewById(R.id.x_badge), Exams.countdown(ctx, en.exam, now, false),
                Exams.urgencyColor(ctx, en.exam, now, Ui.attr(ctx, androidx.appcompat.R.attr.colorPrimary)));
        v.setAlpha(past ? 0.72f : 1f);
        v.setOnClickListener(x -> Dialogs.editExam(a, sem, showCourse ? null : en.course, en.exam));
        View menu = v.findViewById(R.id.x_menu);
        menu.setOnClickListener(x -> {
            androidx.appcompat.widget.PopupMenu p = new androidx.appcompat.widget.PopupMenu(ctx, menu);
            if (past) p.getMenu().add(0, 1, 0, R.string.exam_record_mark);
            if (showCourse) p.getMenu().add(0, 2, 0, R.string.open_course);
            p.getMenu().add(0, 3, 0, R.string.edit);
            p.getMenu().add(0, 4, 0, R.string.delete);
            p.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1:
                        Dialogs.editAssessment(a, en.course, null, name, assessmentType(en.exam.type));
                        return true;
                    case 2:
                        ctx.startActivity(CourseActivity.intent(ctx, en.course.id, CourseActivity.TAB_EXAMS));
                        return true;
                    case 3:
                        Dialogs.editExam(a, sem, showCourse ? null : en.course, en.exam);
                        return true;
                    default:
                        en.course.exams.remove(en.exam);
                        com.mizan.gpa.data.Repository.get(ctx).save();
                        return true;
                }
            });
            p.show();
        });
        return v;
    }

    private static String assessmentType(String examType) {
        switch (examType) {
            case com.mizan.gpa.data.Exam.MIDTERM: return com.mizan.gpa.data.Assessment.MIDTERM;
            case com.mizan.gpa.data.Exam.FINAL: return com.mizan.gpa.data.Assessment.FINAL;
            case com.mizan.gpa.data.Exam.QUIZ: return com.mizan.gpa.data.Assessment.QUIZ;
            case com.mizan.gpa.data.Exam.PRACTICAL: return com.mizan.gpa.data.Assessment.LAB;
            default: return com.mizan.gpa.data.Assessment.OTHER;
        }
    }

    /** Generic item_simple row. */
    public static View simple(ViewGroup parent, int icon, int iconColor, CharSequence title, CharSequence sub, CharSequence value) {
        Context ctx = parent.getContext();
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_simple, parent, false);
        ImageView iv = v.findViewById(R.id.s_icon);
        if (icon != 0) {
            iv.setImageResource(icon);
            iv.setImageTintList(Ui.csl(iconColor));
            Ui.dot(iv, Ui.withAlpha(iconColor, 0.14f));
        } else {
            iv.setVisibility(View.GONE);
        }
        ((TextView) v.findViewById(R.id.s_title)).setText(title);
        TextView s = v.findViewById(R.id.s_sub);
        s.setText(sub);
        s.setVisibility(sub == null || sub.length() == 0 ? View.GONE : View.VISIBLE);
        TextView val = v.findViewById(R.id.s_value);
        val.setText(value);
        val.setVisibility(value == null || value.length() == 0 ? View.GONE : View.VISIBLE);
        v.findViewById(R.id.s_menu).setVisibility(View.GONE);
        return v;
    }

    public static void kv(View row, CharSequence key, CharSequence value) {
        ((TextView) row.findViewById(R.id.kv_key)).setText(key);
        ((TextView) row.findViewById(R.id.kv_value)).setText(value);
    }

    public static void empty(View root, int icon, int title, int text, int action, View.OnClickListener onAction) {
        View e = root.findViewById(R.id.empty);
        e.setVisibility(View.VISIBLE);
        ((ImageView) e.findViewById(R.id.empty_icon)).setImageResource(icon);
        ((TextView) e.findViewById(R.id.empty_title)).setText(title);
        TextView t = e.findViewById(R.id.empty_text);
        if (text != 0) t.setText(text);
        t.setVisibility(text != 0 ? View.VISIBLE : View.GONE);
        com.google.android.material.button.MaterialButton b = e.findViewById(R.id.empty_action);
        if (action != 0 && onAction != null) {
            b.setVisibility(View.VISIBLE);
            b.setText(action);
            b.setOnClickListener(onAction);
        } else {
            b.setVisibility(View.GONE);
        }
    }

    public static void hideEmpty(View root) {
        root.findViewById(R.id.empty).setVisibility(View.GONE);
    }
}
