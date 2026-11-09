package com.mizan.gpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Assessment;
import com.mizan.gpa.data.AttendanceRecord;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeScale;
import com.mizan.gpa.data.LectureSlot;
import com.mizan.gpa.data.Semester;

import org.json.JSONException;
import org.junit.Test;

public class JsonRoundTripTest {
    @Test
    public void roundTripKeepsEverything() throws JSONException {
        AppData d = new AppData();
        d.settings.scale = GradeScale.preset(GradeScale.PRESET_US4);
        d.settings.priorGpa = 3.2;
        d.settings.priorCredits = 18;
        d.settings.latesPerAbsence = 3;
        Semester s = new Semester();
        s.name = "خريف 2026";
        s.weeks = 14;
        Course c = new Course();
        c.name = "Calculus";
        c.code = "MATH 101";
        c.credits = 4;
        c.expectedGrade = "A-";
        Assessment a = new Assessment();
        a.name = "Midterm";
        a.score = 27.5;
        a.outOf = 30;
        a.weight = 25;
        c.assessments.add(a);
        Assessment f = new Assessment();
        f.name = "Final";
        c.assessments.add(f);
        AttendanceRecord r = new AttendanceRecord();
        r.date = "2026-11-02";
        r.status = AttendanceRecord.LATE;
        c.attendance.add(r);
        LectureSlot l = new LectureSlot();
        l.day = 3;
        l.start = 600;
        l.end = 690;
        c.lectures.add(l);
        s.courses.add(c);
        d.semesters.add(s);
        d.settings.currentSemesterId = s.id;

        AppData back = AppData.parse(d.serialize(2));
        assertEquals(3.2, back.settings.priorGpa, 1e-9);
        assertEquals(3, back.settings.latesPerAbsence);
        assertEquals(GradeScale.PRESET_US4, back.settings.scale.preset);
        assertEquals(11, back.settings.scale.grades.size());
        assertEquals(s.id, back.currentSemester().id);
        Semester bs = back.semesters.get(0);
        assertEquals("خريف 2026", bs.name);
        assertEquals(14, bs.weeks);
        Course bc = bs.courses.get(0);
        assertEquals("MATH 101", bc.code);
        assertNull(bc.finalGrade);
        assertEquals("A-", bc.expectedGrade);
        assertEquals(27.5, bc.assessments.get(0).score, 1e-9);
        assertTrue(Double.isNaN(bc.assessments.get(1).score));
        assertEquals(AttendanceRecord.LATE, bc.attendance.get(0).status);
        assertEquals(600, bc.lectures.get(0).start);
    }

    @Test(expected = JSONException.class)
    public void rejectsForeignJson() throws JSONException {
        AppData.parse("{\"hello\":1}");
    }
}
