package com.mizan.gpa.export;

import android.content.Context;

import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Assessment;
import com.mizan.gpa.data.AttendanceRecord;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Exam;
import com.mizan.gpa.data.GradeScale;
import com.mizan.gpa.data.Json;
import com.mizan.gpa.data.LectureSlot;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.time.LocalDate;
import java.util.Calendar;

/** Realistic demo data (three semesters) so the app can be explored before entering real data. */
public final class SampleData {
    private SampleData() {}

    public static AppData create(Context ctx, Settings keep) {
        boolean ar = Fmt.locale(ctx).getLanguage().equals("ar");
        AppData d = new AppData();
        try {
            d.settings = Settings.fromJson(keep.toJson());
        } catch (Exception e) {
            d.settings = new Settings();
        }
        Settings st = d.settings;
        st.priorCredits = 0;
        st.priorGpa = 0;
        st.targetGpa = st.scale.maxPoints() * 0.9;
        GradeScale scale = st.scale;
        int year = LocalDate.now().getYear();

        Semester s1 = semester(ar ? "خريف " + (year - 1) : "Fall " + (year - 1));
        add(s1, ar ? "تفاضل وتكامل 1" : "Calculus I", "MATH 101", 4, scale, 96);
        add(s1, ar ? "مقدمة في البرمجة" : "Intro to Programming", "CS 101", 3, scale, 88);
        add(s1, ar ? "فيزياء عامة 1" : "Physics I", "PHYS 101", 4, scale, 91);
        add(s1, ar ? "مهارات الكتابة" : "Academic Writing", "ENGL 101", 2, scale, 79);
        add(s1, ar ? "الثقافة الإسلامية" : "Islamic Culture", "IC 101", 2, scale, 84);

        Semester s2 = semester(ar ? "ربيع " + year : "Spring " + year);
        add(s2, ar ? "تفاضل وتكامل 2" : "Calculus II", "MATH 102", 4, scale, 92);
        add(s2, ar ? "هياكل البيانات" : "Data Structures", "CS 201", 3, scale, 85);
        add(s2, ar ? "الرياضيات المتقطعة" : "Discrete Mathematics", "MATH 151", 3, scale, 97);
        add(s2, ar ? "فيزياء عامة 2" : "Physics II", "PHYS 102", 4, scale, 74);
        add(s2, ar ? "مهارات الاتصال" : "Communication Skills", "COMM 101", 2, scale, 89);

        Semester s3 = semester(ar ? "خريف " + year : "Fall " + year);
        Course algo = add(s3, ar ? "تصميم الخوارزميات" : "Algorithms", "CS 301", 3, scale, -1);
        Course db = add(s3, ar ? "قواعد البيانات" : "Databases", "CS 340", 3, scale, -1);
        Course la = add(s3, ar ? "الجبر الخطي" : "Linear Algebra", "MATH 241", 3, scale, -1);
        Course net = add(s3, ar ? "شبكات الحاسب" : "Computer Networks", "CS 360", 3, scale, -1);
        Course stat = add(s3, ar ? "الإحصاء والاحتمالات" : "Statistics", "STAT 101", 3, scale, -1);

        marks(algo, ar, 27, 9, 18, true);
        marks(db, ar, 24, 8, 17, true);
        marks(la, ar, 29, 10, 19, false);
        marks(net, ar, 16, 6, 12, true);
        marks(stat, ar, 22, 7, 16, false);

        slot(algo, Calendar.SUNDAY, 8 * 60, 9 * 60 + 15, "A-101");
        slot(algo, Calendar.TUESDAY, 8 * 60, 9 * 60 + 15, "A-101");
        slot(db, Calendar.MONDAY, 10 * 60, 11 * 60 + 15, "B-204");
        slot(db, Calendar.WEDNESDAY, 10 * 60, 11 * 60 + 15, "B-204");
        LectureSlot lab = slot(db, Calendar.THURSDAY, 13 * 60, 15 * 60, "Lab 3");
        lab.kind = LectureSlot.LAB;
        slot(la, Calendar.SUNDAY, 11 * 60, 12 * 60 + 15, "C-012");
        slot(la, Calendar.TUESDAY, 11 * 60, 12 * 60 + 15, "C-012");
        slot(net, Calendar.MONDAY, 8 * 60, 9 * 60 + 15, "A-210");
        slot(net, Calendar.WEDNESDAY, 8 * 60, 9 * 60 + 15, "A-210");
        slot(stat, Calendar.THURSDAY, 9 * 60 + 30, 10 * 60 + 45, "D-105");
        slot(stat, Calendar.SUNDAY, 13 * 60, 14 * 60 + 15, "D-105");

        attendance(algo, 1, 0, 14);
        attendance(db, 3, 1, 18);
        attendance(la, 0, 1, 12);
        attendance(net, 6, 2, 10);
        attendance(stat, 2, 0, 12);

        LocalDate today = LocalDate.now();
        exam(net, ar ? "الفصلي الثاني" : "Midterm 2", Exam.MIDTERM, today.plusDays(1), 10 * 60, 11 * 60 + 30, "A-210");
        exam(la, "", Exam.QUIZ, today.plusDays(3), 11 * 60, 0, "C-012");
        exam(algo, "", Exam.FINAL, today.plusDays(12), 8 * 60, 10 * 60, ar ? "القاعة الكبرى" : "Main hall");
        exam(db, "", Exam.FINAL, today.plusDays(15), 13 * 60, 15 * 60, "B-204");
        exam(stat, ar ? "الفصلي الأول" : "Midterm 1", Exam.MIDTERM, today.minusDays(20), 9 * 60, 10 * 60, "D-105");

        d.semesters.add(s1);
        d.semesters.add(s2);
        d.semesters.add(s3);
        st.currentSemesterId = s3.id;
        return d;
    }

    private static Semester semester(String name) {
        Semester s = new Semester();
        s.name = name;
        s.weeks = 15;
        return s;
    }

    private static Course add(Semester s, String name, String code, double credits, GradeScale scale, double percent) {
        Course c = new Course();
        c.name = name;
        c.code = code;
        c.credits = credits;
        c.color = Ui.COURSE_COLORS[s.courses.size() % Ui.COURSE_COLORS.length];
        if (percent >= 0) c.finalGrade = scale.forPercent(percent, false).letter;
        s.courses.add(c);
        return c;
    }

    private static void marks(Course c, boolean ar, double mid, double quiz, double assignment, boolean project) {
        c.assessments.add(a(ar ? "الاختبار الفصلي" : "Midterm", Assessment.MIDTERM, mid, 30, 30));
        c.assessments.add(a(ar ? "اختبار قصير 1" : "Quiz 1", Assessment.QUIZ, quiz, 10, 10));
        if (project) c.assessments.add(a(ar ? "المشروع" : "Project", Assessment.PROJECT, assignment, 20, 20));
        else c.assessments.add(a(ar ? "الواجبات" : "Assignments", Assessment.ASSIGNMENT, assignment, 20, 20));
        c.assessments.add(a(ar ? "الاختبار النهائي" : "Final exam", Assessment.FINAL, Double.NaN, 40, 40));
    }

    private static Assessment a(String name, String type, double score, double outOf, double weight) {
        Assessment x = new Assessment();
        x.name = name;
        x.type = type;
        x.score = score;
        x.outOf = outOf;
        x.weight = weight;
        return x;
    }

    private static void exam(Course c, String title, String type, LocalDate date, int start, int end, String room) {
        Exam e = new Exam();
        e.title = title;
        e.type = type;
        e.date = date.toString();
        e.start = start;
        e.end = end;
        e.room = room;
        c.exams.add(e);
    }

    private static LectureSlot slot(Course c, int day, int start, int end, String room) {
        LectureSlot s = new LectureSlot();
        s.id = Json.newId();
        s.day = day;
        s.start = start;
        s.end = end;
        s.room = room;
        c.lectures.add(s);
        return s;
    }

    private static void attendance(Course c, int absent, int late, int present) {
        LocalDate day = LocalDate.now().minusDays(1);
        int made = 0, total = absent + late + present;
        while (made < total) {
            int cal = Fmt.toCalendarDay(day.getDayOfWeek());
            boolean meets = false;
            for (LectureSlot s : c.lectures) if (s.day == cal) meets = true;
            if (meets) {
                AttendanceRecord r = new AttendanceRecord();
                r.date = day.toString();
                // Spread absences and late arrivals through the term.
                if (made % 4 == 1 && absent > 0) {
                    r.status = AttendanceRecord.ABSENT;
                    absent--;
                } else if (made % 5 == 2 && late > 0) {
                    r.status = AttendanceRecord.LATE;
                    late--;
                } else if (present > 0) {
                    r.status = AttendanceRecord.PRESENT;
                    present--;
                } else if (absent > 0) {
                    r.status = AttendanceRecord.ABSENT;
                    absent--;
                } else {
                    r.status = AttendanceRecord.LATE;
                    late--;
                }
                c.attendance.add(r);
                made++;
            }
            day = day.minusDays(1);
        }
    }
}
