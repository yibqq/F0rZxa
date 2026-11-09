package com.mizan.gpa.ui;

import android.content.Context;

import com.mizan.gpa.R;
import com.mizan.gpa.data.Assessment;
import com.mizan.gpa.data.AttendanceRecord;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeDef;
import com.mizan.gpa.data.GradeScale;
import com.mizan.gpa.data.LectureSlot;
import com.mizan.gpa.util.Fmt;

/** Localized names for stored constants. */
public final class Labels {
    private Labels() {}

    public static String assessmentType(Context c, String type) {
        switch (type) {
            case Assessment.QUIZ: return c.getString(R.string.type_quiz);
            case Assessment.MIDTERM: return c.getString(R.string.type_midterm);
            case Assessment.ASSIGNMENT: return c.getString(R.string.type_assignment);
            case Assessment.PROJECT: return c.getString(R.string.type_project);
            case Assessment.LAB: return c.getString(R.string.type_lab);
            case Assessment.PARTICIPATION: return c.getString(R.string.type_participation);
            case Assessment.FINAL: return c.getString(R.string.type_final);
            default: return c.getString(R.string.type_other);
        }
    }

    public static int assessmentIcon(String type) {
        switch (type) {
            case Assessment.MIDTERM:
            case Assessment.FINAL: return R.drawable.ic_description;
            case Assessment.PROJECT: return R.drawable.ic_lightbulb;
            case Assessment.PARTICIPATION: return R.drawable.ic_person;
            case Assessment.LAB: return R.drawable.ic_tune;
            default: return R.drawable.ic_assignment;
        }
    }

    public static String status(Context c, String status) {
        switch (status) {
            case AttendanceRecord.PRESENT: return c.getString(R.string.att_present);
            case AttendanceRecord.LATE: return c.getString(R.string.att_late);
            case AttendanceRecord.EXCUSED: return c.getString(R.string.att_excused);
            default: return c.getString(R.string.att_absent);
        }
    }

    public static int statusIcon(String status) {
        switch (status) {
            case AttendanceRecord.PRESENT: return R.drawable.ic_check_circle;
            case AttendanceRecord.LATE: return R.drawable.ic_clock;
            case AttendanceRecord.EXCUSED: return R.drawable.ic_help;
            default: return R.drawable.ic_cancel;
        }
    }

    public static String kind(Context c, String kind) {
        switch (kind) {
            case LectureSlot.LAB: return c.getString(R.string.kind_lab);
            case LectureSlot.TUTORIAL: return c.getString(R.string.kind_tutorial);
            default: return c.getString(R.string.kind_lecture);
        }
    }

    public static String gradeOption(GradeDef g) {
        return g.letter + "  ·  " + Fmt.gpa(g.points);
    }

    public static String presetName(Context c, String preset) {
        switch (preset) {
            case GradeScale.PRESET_SA5: return c.getString(R.string.preset_sa5);
            case GradeScale.PRESET_SA4: return c.getString(R.string.preset_sa4);
            case GradeScale.PRESET_US4: return c.getString(R.string.preset_us4);
            default: return c.getString(R.string.preset_custom);
        }
    }

    public static String credits(Context c, double credits) {
        return c.getResources().getQuantityString(R.plurals.credit_hours, (int) Math.ceil(credits), Fmt.num(credits));
    }

    public static String courseSubtitle(Context c, Course course) {
        StringBuilder b = new StringBuilder();
        if (course.code != null && !course.code.trim().isEmpty() && !course.code.trim().equals(course.displayName())) {
            b.append(course.code.trim()).append(" · ");
        }
        b.append(credits(c, course.credits));
        if (!course.countInGpa) b.append(" · ").append(c.getString(R.string.not_in_gpa));
        return b.toString();
    }
}
