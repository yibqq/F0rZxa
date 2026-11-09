package com.mizan.gpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.mizan.gpa.calc.AttendanceCalculator;
import com.mizan.gpa.calc.GpaCalculator;
import com.mizan.gpa.calc.GradeResolver;
import com.mizan.gpa.calc.MarksCalculator;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Assessment;
import com.mizan.gpa.data.AttendanceRecord;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeScale;
import com.mizan.gpa.data.LectureSlot;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalculatorsTest {
    private static final double D = 1e-9;

    private static Course course(String name, double credits, String grade) {
        Course c = new Course();
        c.name = name;
        c.credits = credits;
        c.finalGrade = grade;
        return c;
    }

    private static Assessment mark(String type, double score, double outOf) {
        Assessment a = new Assessment();
        a.type = type;
        a.score = score;
        a.outOf = outOf;
        a.weight = outOf;
        return a;
    }

    @Test
    public void semesterGpaFivePointScale() {
        AppData d = new AppData();
        Semester s = new Semester();
        s.courses.add(course("A", 3, "A+"));   // 5.00 * 3 = 15
        s.courses.add(course("B", 4, "B"));    // 4.00 * 4 = 16
        s.courses.add(course("C", 2, "C+"));   // 3.50 * 2 = 7
        d.semesters.add(s);
        GpaCalculator.Totals t = GpaCalculator.semester(s, d.settings, GradeResolver.Mode.OFFICIAL);
        assertEquals(9, t.credits, D);
        assertEquals(38.0 / 9.0, t.gpa(), D);
    }

    @Test
    public void fourPointScaleAndExclusions() {
        AppData d = new AppData();
        d.settings.scale = GradeScale.preset(GradeScale.PRESET_SA4);
        Semester s = new Semester();
        s.courses.add(course("A", 3, "A"));    // 3.75 * 3
        s.courses.add(course("F", 3, "F"));    // 0 * 3 (still counts)
        Course pf = course("PF", 2, "A+");
        pf.countInGpa = false;                 // excluded
        s.courses.add(pf);
        s.courses.add(course("IP", 3, null));  // in progress, excluded officially
        d.semesters.add(s);
        GpaCalculator.Totals t = GpaCalculator.semester(s, d.settings, GradeResolver.Mode.OFFICIAL);
        assertEquals(6, t.credits, D);
        assertEquals(3.75 * 3 / 6, t.gpa(), D);
    }

    @Test
    public void cumulativeIncludesPriorStudies() {
        AppData d = new AppData();
        d.settings.priorCredits = 30;
        d.settings.priorGpa = 4.0;              // 120 points
        Semester s = new Semester();
        s.courses.add(course("A", 15, "A+"));  // 75 points
        d.semesters.add(s);
        GpaCalculator.Totals t = GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL);
        assertEquals(45, t.credits, D);
        assertEquals(195.0 / 45.0, t.gpa(), D);
    }

    @Test
    public void emptyGpaIsNaN() {
        AppData d = new AppData();
        assertTrue(Double.isNaN(GpaCalculator.cumulative(d, GradeResolver.Mode.OFFICIAL).gpa()));
    }

    @Test
    public void letterFromPercentWithAndWithoutRounding() {
        GradeScale s = GradeScale.preset(GradeScale.PRESET_SA5);
        assertEquals("B+", s.forPercent(89.5, false).letter);
        assertEquals("A", s.forPercent(89.5, true).letter);
        assertEquals("A+", s.forPercent(95, false).letter);
        assertEquals("F", s.forPercent(59.99, false).letter);
        assertEquals("D", s.forPercent(60, false).letter);
        assertEquals("A+", s.forPercent(100, false).letter);
    }

    @Test
    public void marksAndProjection() {
        Course c = new Course();
        c.assessments.add(mark(Assessment.MIDTERM, 24, 30));  // 24/30
        c.assessments.add(mark(Assessment.QUIZ, 8, 10));      // 8/10
        Assessment fin = new Assessment();
        fin.type = Assessment.FINAL;
        fin.outOf = 60;
        fin.weight = 60;
        c.assessments.add(fin);
        MarksCalculator.Result r = MarksCalculator.compute(c);
        assertEquals(32, r.earned, D);
        assertEquals(40, r.gradedWeight, D);
        assertEquals(100, r.totalWeight, D);
        assertEquals(60, r.remainingWeight, D);
        assertEquals(80, r.currentPercent(), D);
        assertEquals(80, r.projectedPercent(), D);
        assertEquals(32, r.securedPercent(), D);
        assertEquals(92, r.maxPercent(), D);
        assertSame(fin, r.lastRemaining);
        assertFalse(r.isComplete());
    }

    @Test
    public void requiredFinalScore() {
        Course c = new Course();
        c.assessments.add(mark(Assessment.MIDTERM, 30, 40));   // 30 of 40
        Assessment fin = new Assessment();
        fin.type = Assessment.FINAL;
        fin.outOf = 50;      // final graded out of 50 ...
        fin.weight = 60;     // ... but worth 60 marks
        c.assessments.add(fin);
        GradeScale scale = GradeScale.preset(GradeScale.PRESET_SA5);
        List<MarksCalculator.Requirement> req = MarksCalculator.requirements(MarksCalculator.compute(c), scale, false);
        // A+ needs 95: 65 more of 60 -> impossible.
        assertEquals(MarksCalculator.State.IMPOSSIBLE, req.get(0).state);
        // B (index 3) needs 80: 50 more of 60 marks = 41.667 out of 50.
        MarksCalculator.Requirement b = req.get(3);
        assertEquals("B", b.grade.letter);
        assertEquals(MarksCalculator.State.POSSIBLE, b.state);
        assertEquals(50, b.neededMarks, D);
        assertEquals(50.0 / 60.0 * 50.0, b.neededScore, 1e-6);
        // F is always secured.
        assertEquals(MarksCalculator.State.SECURED, req.get(req.size() - 1).state);
    }

    @Test
    public void unassignedMarksCountAsRemaining() {
        Course c = new Course();
        c.assessments.add(mark(Assessment.MIDTERM, 20, 25));
        MarksCalculator.Result r = MarksCalculator.compute(c);
        assertEquals(75, r.remainingWeight, D);
        assertNull(r.lastRemaining);
    }

    @Test
    public void expectedBestWorstResolution() {
        GradeScale scale = GradeScale.preset(GradeScale.PRESET_SA5);
        Course c = new Course();
        c.assessments.add(mark(Assessment.MIDTERM, 36, 40)); // 90% so far; secured 36; max 96
        assertEquals("A", GradeResolver.resolve(c, scale, false, GradeResolver.Mode.EXPECTED).letter);
        assertEquals("A+", GradeResolver.resolve(c, scale, false, GradeResolver.Mode.BEST).letter);
        assertEquals("F", GradeResolver.resolve(c, scale, false, GradeResolver.Mode.WORST).letter);
        assertNull(GradeResolver.resolve(c, scale, false, GradeResolver.Mode.OFFICIAL));
        c.expectedGrade = "B";
        assertEquals("B", GradeResolver.resolve(c, scale, false, GradeResolver.Mode.EXPECTED).letter);
        Map<String, String> what = new HashMap<>();
        what.put(c.id, "C");
        assertEquals("C", GradeResolver.resolve(c, scale, false, GradeResolver.Mode.EXPECTED, what).letter);
        c.finalGrade = "A";
        assertEquals("A", GradeResolver.resolve(c, scale, false, GradeResolver.Mode.WORST).letter);
    }

    @Test
    public void noMarksBestIsTopWorstIsBottom() {
        GradeScale scale = GradeScale.preset(GradeScale.PRESET_SA4);
        Course c = new Course();
        assertEquals("A+", GradeResolver.resolve(c, scale, false, GradeResolver.Mode.BEST).letter);
        assertEquals("F", GradeResolver.resolve(c, scale, false, GradeResolver.Mode.WORST).letter);
        assertNull(GradeResolver.resolve(c, scale, false, GradeResolver.Mode.EXPECTED));
    }

    @Test
    public void requiredGpaForTarget() {
        GpaCalculator.Totals cur = new GpaCalculator.Totals();
        cur.credits = 60;
        cur.points = 60 * 3.5;
        // Target 3.75 with 15 more hours: (3.75*75 - 210) / 15 = 4.75
        assertEquals(4.75, GpaCalculator.requiredGpa(cur, 3.75, 15), D);
        // Credits at 5.0 needed: (3.75*60 - 210) / (5 - 3.75) = 12
        assertEquals(12, GpaCalculator.creditsNeededAtMax(cur, 3.75, 5.0), D);
        assertEquals(0, GpaCalculator.creditsNeededAtMax(cur, 3.0, 5.0), D);
        assertTrue(Double.isInfinite(GpaCalculator.creditsNeededAtMax(cur, 5.0, 5.0)));
    }

    @Test
    public void allowedAbsencesReachVersusExceed() {
        // 45 meetings, 25% -> threshold 11.25: 11 allowed either way.
        assertEquals(11, AttendanceCalculator.allowedAbsences(45, 25, true));
        assertEquals(11, AttendanceCalculator.allowedAbsences(45, 25, false));
        // 40 meetings, 25% -> threshold 10: reaching 10 deprives in "reach" mode.
        assertEquals(9, AttendanceCalculator.allowedAbsences(40, 25, true));
        assertEquals(10, AttendanceCalculator.allowedAbsences(40, 25, false));
        assertEquals(0, AttendanceCalculator.allowedAbsences(0, 25, true));
    }

    @Test
    public void attendanceStatsAndStatus() {
        Settings st = new Settings();
        st.absenceLimit = 25;
        st.deprivedOnReach = true;
        st.warnRemaining = 2;
        st.latesPerAbsence = 3;
        Semester sem = new Semester();
        sem.weeks = 10;
        Course c = new Course();
        c.lectures.add(new LectureSlot());
        c.lectures.add(new LectureSlot());     // 2 per week x 10 weeks = 20 meetings, allowed = 4
        for (int i = 0; i < 2; i++) c.attendance.add(rec(AttendanceRecord.ABSENT, 1));
        for (int i = 0; i < 3; i++) c.attendance.add(rec(AttendanceRecord.LATE, 1)); // = 1 absence
        c.attendance.add(rec(AttendanceRecord.EXCUSED, 1));                          // not counted
        AttendanceCalculator.Stats s = AttendanceCalculator.compute(c, sem, st);
        assertEquals(20, s.total);
        assertTrue(s.totalFromSchedule);
        assertEquals(3, s.counted);
        assertEquals(4, s.allowed);
        assertEquals(1, s.remaining);
        assertEquals(15.0, s.percent, D);
        assertEquals(AttendanceCalculator.Status.WARNING, s.status);

        c.attendance.add(rec(AttendanceRecord.ABSENT, 1));
        assertEquals(AttendanceCalculator.Status.CRITICAL, AttendanceCalculator.compute(c, sem, st).status);
        c.attendance.add(rec(AttendanceRecord.ABSENT, 2));   // a double session
        AttendanceCalculator.Stats dep = AttendanceCalculator.compute(c, sem, st);
        assertEquals(6, dep.counted);
        assertEquals(AttendanceCalculator.Status.DEPRIVED, dep.status);

        st.countExcused = true;
        assertEquals(7, AttendanceCalculator.compute(c, sem, st).counted);
    }

    @Test
    public void attendanceUsesManualTotalAndCourseLimit() {
        Settings st = new Settings();
        Course c = new Course();
        c.totalSessions = 30;
        c.absenceLimit = 20;                   // 6 -> reach mode allows 5
        AttendanceCalculator.Stats s = AttendanceCalculator.compute(c, new Semester(), st);
        assertFalse(s.totalFromSchedule);
        assertEquals(5, s.allowed);
        assertEquals(AttendanceCalculator.Status.SAFE, s.status);
        Course none = new Course();
        assertEquals(AttendanceCalculator.Status.UNKNOWN, AttendanceCalculator.compute(none, new Semester(), st).status);
    }

    @Test
    public void trendUsesExpectedForInProgressSemester() {
        AppData d = new AppData();
        Semester s1 = new Semester();
        s1.courses.add(course("A", 3, "A+"));
        Semester s2 = new Semester();
        Course ip = course("B", 3, null);
        ip.expectedGrade = "B";
        s2.courses.add(ip);
        d.semesters.add(s1);
        d.semesters.add(s2);
        List<GpaCalculator.TrendPoint> t = GpaCalculator.trend(d);
        assertEquals(2, t.size());
        assertFalse(t.get(0).projected);
        assertTrue(t.get(1).projected);
        assertEquals(4.5, t.get(1).cumulativeGpa, D);
    }

    private static AttendanceRecord rec(String status, int sessions) {
        AttendanceRecord r = new AttendanceRecord();
        r.status = status;
        r.sessions = sessions;
        r.date = "2026-10-01";
        return r;
    }
}
