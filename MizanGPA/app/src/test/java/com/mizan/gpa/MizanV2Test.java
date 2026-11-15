package com.mizan.gpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.mizan.gpa.calc.AttendanceCalculator;
import com.mizan.gpa.calc.GpaCalculator;
import com.mizan.gpa.calc.GradeResolver;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.AttendanceRecord;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeScale;
import com.mizan.gpa.data.LectureSlot;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.data.Task;
import com.mizan.gpa.ui.TaskUi;
import com.mizan.gpa.util.Fmt;

import org.json.JSONException;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

/** Baseline, calculator, rounding, attendance deduplication, undated absences, CRN, tasks and migration. */
public class MizanV2Test {
    private static final double D = 1e-9;

    private static Course course(String name, double credits, String grade) {
        Course c = new Course();
        c.name = name;
        c.credits = credits;
        c.finalGrade = grade;
        return c;
    }

    private static AppData withSemester(Course... courses) {
        AppData d = new AppData();
        Semester s = new Semester();
        s.courses.addAll(Arrays.asList(courses));
        d.semesters.add(s);
        return d;
    }

    // ------------------------------------------------------------------ baseline

    @Test
    public void baselineIsWeightedByItsHours() {
        AppData d = withSemester(course("A", 15, "A+"));          // 15 h x 5.00 = 75
        d.settings.priorGpa = 4.0;
        d.settings.priorCredits = 45;                              // 180 points
        assertEquals(GpaCalculator.Baseline.VALID, GpaCalculator.baseline(d.settings));
        GpaCalculator.Totals t = GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL);
        assertEquals(60, t.credits, D);
        assertEquals(255.0 / 60.0, t.gpa(), D);
    }

    @Test
    public void baselineWithoutHoursIsNeverUsed() {
        AppData d = withSemester(course("A", 3, "B"));             // 4.00
        d.settings.priorGpa = 4.8;
        d.settings.priorCredits = 0;
        assertEquals(GpaCalculator.Baseline.NO_CREDITS, GpaCalculator.baseline(d.settings));
        GpaCalculator.Totals t = GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL);
        assertEquals(3, t.credits, D);
        assertEquals(4.0, t.gpa(), D);                             // not influenced by 4.8
    }

    @Test
    public void baselineAboveScaleIsNotClampedOrUsed() {
        AppData d = withSemester(course("A", 3, "A"));             // 4-point: 3.75
        d.settings.scale = GradeScale.preset(GradeScale.PRESET_SA4);
        d.settings.priorGpa = 4.5;                                 // impossible on a 4-point scale
        d.settings.priorCredits = 30;
        assertEquals(GpaCalculator.Baseline.OUT_OF_SCALE, GpaCalculator.baseline(d.settings));
        assertEquals(3.75, GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL).gpa(), D);
    }

    @Test
    public void semesterInBaselineIsNotCountedTwice() {
        Semester old = new Semester();
        old.courses.add(course("Old", 15, "C"));                   // 3.00
        old.inBaseline = true;
        Semester now = new Semester();
        now.courses.add(course("New", 15, "A+"));                  // 5.00
        AppData d = new AppData();
        d.semesters.add(old);
        d.semesters.add(now);
        d.settings.priorGpa = 3.0;
        d.settings.priorCredits = 15;                              // = the old semester
        GpaCalculator.Totals t = GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL);
        assertEquals(30, t.credits, D);                            // 15 baseline + 15 new, not 45
        assertEquals(4.0, t.gpa(), D);
        // Without a valid baseline, the flag must not make the semester disappear.
        d.settings.priorCredits = 0;
        d.settings.priorGpa = 0;
        assertEquals(30, GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL).credits, D);
    }

    // ------------------------------------------------------------------ calculator & rounding

    @Test
    public void calculatorWeightsAndSkipsIncompleteRows() {
        GradeScale five = GradeScale.preset(GradeScale.PRESET_SA5);
        GpaCalculator.Calculation c = GpaCalculator.calculate(Arrays.asList(
                new GpaCalculator.Entry(3, "A+"),      // 15
                new GpaCalculator.Entry(4, "B"),       // 16
                new GpaCalculator.Entry(2, null),      // no grade -> skipped
                new GpaCalculator.Entry(Double.NaN, "A"),
                new GpaCalculator.Entry(0, "A"),
                new GpaCalculator.Entry(3, "Z")), five); // not in the scale
        assertEquals(7, c.totals.credits, D);
        assertEquals(31.0 / 7.0, c.totals.gpa(), D);
        assertEquals(4, c.ignored);
        // Same letters on the 4-point scale use that scale's points.
        GpaCalculator.Calculation four = GpaCalculator.calculate(Arrays.asList(
                new GpaCalculator.Entry(3, "A+"), new GpaCalculator.Entry(4, "B")), GradeScale.preset(GradeScale.PRESET_SA4));
        assertEquals((3 * 4.0 + 4 * 3.0) / 7.0, four.totals.gpa(), D);
        assertTrue(Double.isNaN(GpaCalculator.calculate(Arrays.asList(), five).totals.gpa()));
    }

    @Test
    public void gpaRoundsHalfUp() {
        assertEquals("3.13", Fmt.gpa(3.125));
        assertEquals("3.15", Fmt.gpa(3.145));     // binary 3.1449999… must still round up
        assertEquals("2.01", Fmt.gpa(2.005));
        assertEquals("4.00", Fmt.gpa(4));
        assertEquals("4.43", Fmt.gpa(31.0 / 7.0));
        assertEquals(Fmt.DASH, Fmt.gpa(Double.NaN));
    }

    // ------------------------------------------------------------------ attendance

    @Test
    public void onlyOneAbsencePerDay() {
        Course c = new Course();
        assertEquals(Course.RecordResult.ADDED, c.recordAttendance("2026-11-10", AttendanceRecord.ABSENT).result);
        for (int i = 0; i < 5; i++) {
            assertEquals(Course.RecordResult.DUPLICATE, c.recordAttendance("2026-11-10", AttendanceRecord.ABSENT).result);
        }
        assertEquals(1, c.attendance.size());
        assertEquals(Course.RecordResult.ADDED, c.recordAttendance("2026-11-11", AttendanceRecord.ABSENT).result);
        assertEquals(2, c.attendance.size());
    }

    @Test
    public void differentStatusReplacesTheDayAndCanBeUndone() {
        Course c = new Course();
        c.recordAttendance("2026-11-10", AttendanceRecord.PRESENT);
        Course.Recorded r = c.recordAttendance("2026-11-10", AttendanceRecord.ABSENT);
        assertEquals(Course.RecordResult.UPDATED, r.result);
        assertEquals(1, c.attendance.size());
        assertEquals(AttendanceRecord.ABSENT, c.attendance.get(0).status);
        c.undo(r);
        assertEquals(AttendanceRecord.PRESENT, c.attendance.get(0).status);
        assertNotNull(c.recordOn("2026-11-10", null));
        assertNull(c.recordOn("2026-11-10", c.attendance.get(0)));
    }

    @Test
    public void duplicateAbsencesInSavedDataAreMerged() throws JSONException {
        String json = "{\"version\":1,\"settings\":{},\"semesters\":[{\"name\":\"S\",\"courses\":[{\"name\":\"C\",\"attendance\":["
                + "{\"date\":\"2026-11-10\",\"status\":\"absent\"},"
                + "{\"date\":\"2026-11-10\",\"status\":\"absent\",\"sessions\":2},"
                + "{\"date\":\"2026-11-10\",\"status\":\"absent\"},"
                + "{\"date\":\"2026-11-11\",\"status\":\"absent\"},"
                + "{\"date\":\"2026-11-12\",\"status\":\"present\"}]}]}]}";
        Course c = AppData.parse(json).semesters.get(0).courses.get(0);
        assertEquals(3, c.attendance.size());
        assertEquals(2, c.attendance.get(0).sessions);   // the larger session count is kept
        // Persistence layer: normalize() enforces it again before every save.
        AppData d = new AppData();
        Semester s = new Semester();
        s.courses.add(c);
        d.semesters.add(s);
        AttendanceRecord extra = new AttendanceRecord();
        extra.date = "2026-11-11";
        extra.status = AttendanceRecord.ABSENT;
        c.attendance.add(extra);
        d.normalize();
        assertEquals(3, c.attendance.size());
    }

    @Test
    public void undatedAbsencesCountTowardTheLimit() {
        Settings st = new Settings();
        Semester sem = new Semester();
        sem.weeks = 10;
        Course c = new Course();
        c.lectures.add(new LectureSlot());
        c.lectures.add(new LectureSlot());                   // 20 meetings, 25% reach -> 4 allowed
        c.recordAttendance("2026-11-10", AttendanceRecord.ABSENT);
        c.undatedAbsences = 3;
        AttendanceCalculator.Stats s = AttendanceCalculator.compute(c, sem, st);
        assertEquals(4, s.absent);
        assertEquals(3, s.undated);
        assertEquals(1, s.datedAbsent());
        assertEquals(4, s.counted);
        assertEquals(0, s.remaining);
        assertEquals(AttendanceCalculator.Status.CRITICAL, s.status);
    }

    // ------------------------------------------------------------------ CRN

    @Test
    public void crnFormatAndLookup() {
        assertEquals("30412", AppData.normalizeCrn(" ٣٠٤ ١٢ "));
        assertEquals(AppData.CrnProblem.NONE, AppData.checkCrn("", 5));
        assertEquals(AppData.CrnProblem.NONE, AppData.checkCrn("30412", 5));
        assertEquals(AppData.CrnProblem.WRONG_LENGTH, AppData.checkCrn("3041", 5));
        assertEquals(AppData.CrnProblem.NOT_DIGITS, AppData.checkCrn("30A12", 5));
        assertEquals(AppData.CrnProblem.NONE, AppData.checkCrn("CS-101A", 0));    // no rule: free format
        assertEquals(AppData.CrnProblem.INVALID_CHARACTERS, AppData.checkCrn("30/12", 0));

        AppData d = new AppData();
        Semester fall = new Semester(), spring = new Semester();
        Course old = course("Data Structures", 3, "B");
        old.crn = "30412";
        fall.courses.add(old);
        d.semesters.add(fall);
        d.semesters.add(spring);
        // Reuse info from the student's own records.
        assertSame(old, d.findCourseByCrn("30412", null));
        assertNull(d.findCourseByCrn("99999", null));
        // A repeated CRN in another semester is fine; in the same semester it is a duplicate.
        assertNull(AppData.crnConflict(spring, "30412", null));
        assertSame(old, AppData.crnConflict(fall, " 30412", null));
        assertNull(AppData.crnConflict(fall, "30412", old));       // editing the same course
    }

    // ------------------------------------------------------------------ tasks

    @Test
    public void taskStates() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 15, 12, 0);
        Task a = new Task();
        a.category = Task.ASSIGNMENT;
        a.date = "2026-11-14";
        assertEquals(TaskUi.State.OVERDUE, TaskUi.state(a, now));
        Task e = new Task();
        e.category = Task.EXAM;
        e.date = "2026-11-15";
        e.time = 9 * 60;
        assertEquals(TaskUi.State.FINISHED, TaskUi.state(e, now));     // exams finish, never "overdue"
        e.endTime = 13 * 60;
        assertEquals(TaskUi.State.UPCOMING, TaskUi.state(e, now));     // still running
        Task g = new Task();
        assertEquals(TaskUi.State.UNDATED, TaskUi.state(g, now));
        g.done = true;
        assertEquals(TaskUi.State.DONE, TaskUi.state(g, now));
        // Date-only assignment due today is not overdue until the day ends.
        a.date = "2026-11-15";
        assertEquals(TaskUi.State.UPCOMING, TaskUi.state(a, now));

        AppData d = new AppData();
        Task later = new Task();
        later.date = "2026-11-20";
        later.title = "B";
        Task overdue = new Task();
        overdue.date = "2026-11-01";
        overdue.title = "A";
        Task undated = new Task();
        undated.title = "C";
        d.tasks.addAll(Arrays.asList(undated, later, overdue));
        java.util.List<Task> open = TaskUi.filter(d, null, null, false, TaskUi.SORT_DATE, now);
        assertSame(overdue, open.get(0));
        assertSame(later, open.get(1));
        assertSame(undated, open.get(2));
    }

    // ------------------------------------------------------------------ migration

    @Test
    public void version1DataMigratesSafely() throws JSONException {
        String json = "{\"format\":\"mizan-gpa\",\"version\":1,"
                + "\"settings\":{\"priorGpa\":4.1,\"priorCredits\":40,\"colorTheme\":\"teal\"},"
                + "\"semesters\":[{\"id\":\"s1\",\"name\":\"Fall\",\"courses\":[{\"id\":\"c1\",\"name\":\"Networks\",\"credits\":3,"
                + "\"exams\":[{\"id\":\"e1\",\"title\":\"Midterm 2\",\"type\":\"midterm\",\"date\":\"2026-12-01\",\"start\":600,\"end\":690,"
                + "\"room\":\"A-1\",\"note\":\"Ch. 1-4\",\"customReminders\":true,\"daysBefore\":[2],\"minutesBefore\":[30]},"
                + "{\"id\":\"e2\",\"type\":\"practical\",\"date\":\"2026-12-05\",\"start\":480}]}]}]}";
        AppData d = AppData.parse(json);
        assertEquals(2, d.tasks.size());
        Task t = d.findTask("e1");
        assertEquals(Task.EXAM, t.category);
        assertEquals(Task.KIND_MIDTERM, t.kind);
        assertEquals("c1", t.courseId);
        assertEquals(600, t.time);
        assertEquals(690, t.endTime);
        assertEquals("Ch. 1-4", t.notes);
        assertTrue(t.customReminders);
        assertEquals(Integer.valueOf(30), t.minutesBefore.get(0));
        assertEquals(Task.PRACTICAL, d.findTask("e2").category);
        // Existing users skip onboarding and keep the full interface, colors and baseline.
        assertTrue(d.settings.onboarded);
        assertEquals(Settings.MODE_STANDARD, d.settings.appMode);
        assertEquals("teal", d.settings.colorTheme);
        assertEquals(4.1, d.settings.priorGpa, D);
        assertEquals(40, d.settings.priorCredits, D);
        // Saving and loading again does not duplicate the migrated tasks.
        AppData again = AppData.parse(d.serialize(0));
        assertEquals(2, again.tasks.size());
        assertTrue(again.toJson().getJSONArray("semesters").getJSONObject(0).getJSONArray("courses").getJSONObject(0).isNull("exams")
                || !again.toJson().getJSONArray("semesters").getJSONObject(0).getJSONArray("courses").getJSONObject(0).has("exams"));
    }

    @Test
    public void freshInstallStartsWithOnboarding() throws JSONException {
        AppData fresh = new AppData();
        assertFalse(fresh.settings.onboarded);
        assertEquals("burgundy", fresh.settings.colorTheme);
        // An old file without any semesters (nothing entered yet) still gets the welcome flow.
        AppData empty = AppData.parse("{\"version\":1,\"settings\":{},\"semesters\":[]}");
        assertFalse(empty.settings.onboarded);
    }
}
