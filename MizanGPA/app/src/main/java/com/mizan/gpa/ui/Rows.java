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

    /**
     * Task card: completion box, course color bar, title, category/course/when line, optional notes
     * (no empty block when there are none), countdown or state badge, and a bell only when a reminder
     * will actually be delivered.
     */
    public static View taskCard(ViewGroup parent, androidx.appcompat.app.AppCompatActivity a, com.mizan.gpa.data.Task t, boolean showCourse) {
        Context ctx = parent.getContext();
        com.mizan.gpa.data.AppData d = com.mizan.gpa.data.Repository.get(ctx).data();
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_task, parent, false);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        TaskUi.State state = TaskUi.state(t, now);
        boolean closed = state == TaskUi.State.DONE || state == TaskUi.State.FINISHED;

        com.google.android.material.checkbox.MaterialCheckBox box = v.findViewById(R.id.k_done);
        box.setChecked(t.done);
        box.setContentDescription(ctx.getString(t.done ? R.string.task_mark_open : R.string.task_mark_done) + ": " + TaskUi.title(ctx, t));
        box.setOnClickListener(x -> {
            t.done = box.isChecked();
            com.mizan.gpa.data.Repository.get(ctx).save();
        });
        Ui.roundedFill(v.findViewById(R.id.k_bar), TaskUi.color(ctx, d, t), 2);

        TextView title = v.findViewById(R.id.k_title);
        title.setText(TaskUi.title(ctx, t));
        title.setPaintFlags(t.done ? title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                : title.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);

        StringBuilder meta = new StringBuilder(TaskUi.categoryLabel(ctx, t.category));
        if (com.mizan.gpa.data.Task.EXAM.equals(t.category) && t.title != null && !t.title.trim().isEmpty()) {
            meta.append(" · ").append(TaskUi.kindLabel(ctx, t.kind));
        }
        String course = TaskUi.courseName(d, t);
        if (showCourse && course != null) meta.append(" · ").append(course);
        meta.append(" · ").append(TaskUi.when(ctx, t));
        if (t.room != null && !t.room.trim().isEmpty()) meta.append(" · ").append(t.room.trim());
        ((TextView) v.findViewById(R.id.k_meta)).setText(meta);

        TextView notes = v.findViewById(R.id.k_notes);
        boolean hasNotes = t.notes != null && !t.notes.trim().isEmpty();
        notes.setVisibility(hasNotes ? View.VISIBLE : View.GONE);
        if (hasNotes) notes.setText(t.notes.trim());

        TextView badge = v.findViewById(R.id.k_badge);
        String cd = TaskUi.countdown(ctx, t, now, false);
        if (cd.isEmpty()) badge.setVisibility(View.GONE);
        else Ui.badge(badge, cd, TaskUi.urgencyColor(ctx, t, now, Ui.attr(ctx, androidx.appcompat.R.attr.colorPrimary)));
        boolean reminds = com.mizan.gpa.calc.TaskReminders.hasUpcoming(t, d.settings, now)
                && com.mizan.gpa.notify.Notifier.canPost(ctx);
        v.findViewById(R.id.k_bell).setVisibility(reminds ? View.VISIBLE : View.GONE);
        v.setAlpha(closed ? 0.7f : 1f);

        v.setOnClickListener(x -> Dialogs.editTask(a, t, null, null));
        View menu = v.findViewById(R.id.k_menu);
        menu.setOnClickListener(x -> {
            androidx.appcompat.widget.PopupMenu p = new androidx.appcompat.widget.PopupMenu(ctx, menu);
            p.getMenu().add(0, 1, 0, t.done ? R.string.task_mark_open : R.string.task_mark_done);
            boolean canRecordMark = t.isEvent() && course != null && (state == TaskUi.State.FINISHED || t.done);
            if (canRecordMark) p.getMenu().add(0, 2, 0, R.string.exam_record_mark);
            if (showCourse && course != null) p.getMenu().add(0, 3, 0, R.string.open_course);
            p.getMenu().add(0, 4, 0, R.string.edit);
            p.getMenu().add(0, 5, 0, R.string.delete);
            p.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1:
                        t.done = !t.done;
                        com.mizan.gpa.data.Repository.get(ctx).save();
                        return true;
                    case 2:
                        Dialogs.editAssessment(a, d.findCourse(t.courseId), null, TaskUi.title(ctx, t), assessmentType(t));
                        return true;
                    case 3:
                        ctx.startActivity(CourseActivity.intent(ctx, t.courseId, CourseActivity.TAB_TASKS));
                        return true;
                    case 4:
                        Dialogs.editTask(a, t, null, null);
                        return true;
                    default:
                        Dialogs.confirm(ctx, ctx.getString(R.string.task_delete_title, TaskUi.title(ctx, t)), null, R.string.delete, () -> {
                            d.tasks.remove(t);
                            com.mizan.gpa.data.Repository.get(ctx).save();
                        });
                        return true;
                }
            });
            p.show();
        });
        return v;
    }

    private static String assessmentType(com.mizan.gpa.data.Task t) {
        if (com.mizan.gpa.data.Task.PRACTICAL.equals(t.category)) return com.mizan.gpa.data.Assessment.LAB;
        switch (t.kind) {
            case com.mizan.gpa.data.Task.KIND_MIDTERM: return com.mizan.gpa.data.Assessment.MIDTERM;
            case com.mizan.gpa.data.Task.KIND_FINAL: return com.mizan.gpa.data.Assessment.FINAL;
            case com.mizan.gpa.data.Task.KIND_QUIZ: return com.mizan.gpa.data.Assessment.QUIZ;
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
