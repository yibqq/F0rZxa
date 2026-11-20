package com.gpacalculator.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.gpacalculator.model.Course;
import com.gpacalculator.model.GpaResult;
import com.gpacalculator.model.Grade;
import com.gpacalculator.model.GradeScale;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GpaCalculatorTest {

    private static Course course(int credits, Grade grade) {
        return new Course(0, "", credits, grade);
    }

    @Test
    public void emptyListHasNoGpa() {
        GpaResult result = GpaCalculator.semester(new ArrayList<>(), GradeScale.FOUR);
        assertFalse(result.hasGpa());
        assertNull(result.gpa);
        assertEquals(0, result.credits.signum());
    }

    @Test
    public void weightedAverageOnFourScale() {
        // (3×4.00 + 4×3.50 + 2×2.00) / 9 = 30 / 9 = 3.333…
        List<Course> courses = Arrays.asList(
                course(3, Grade.A_PLUS), course(4, Grade.B_PLUS), course(2, Grade.C));
        GpaResult result = GpaCalculator.semester(courses, GradeScale.FOUR);
        assertEquals(0, new BigDecimal("9").compareTo(result.credits));
        assertEquals(0, new BigDecimal("30.00").compareTo(result.qualityPoints));
        assertEquals("3.33", GpaCalculator.format(result.gpa));
    }

    @Test
    public void sameCoursesOnFiveScale() {
        // (3×5.00 + 4×4.50 + 2×3.00) / 9 = 39 / 9 = 4.333…
        List<Course> courses = Arrays.asList(
                course(3, Grade.A_PLUS), course(4, Grade.B_PLUS), course(2, Grade.C));
        assertEquals("4.33", GpaCalculator.format(GpaCalculator.semester(courses, GradeScale.FIVE).gpa));
    }

    @Test
    public void decimalGradePointsAreExact() {
        // 3 × 3.75 = 11.25 exactly, with no binary floating-point drift.
        List<Course> courses = Arrays.asList(course(3, Grade.A), course(3, Grade.A), course(3, Grade.A));
        GpaResult result = GpaCalculator.semester(courses, GradeScale.FOUR);
        assertEquals(0, new BigDecimal("33.75").compareTo(result.qualityPoints));
        assertEquals("3.75", GpaCalculator.format(result.gpa));
    }

    @Test
    public void roundsHalfUp() {
        assertEquals("3.34", GpaCalculator.format(new BigDecimal("3.335")));
        assertEquals("3.33", GpaCalculator.format(new BigDecimal("3.3349999")));
        assertEquals("4.00", GpaCalculator.format(new BigDecimal("3.9999")));
    }

    @Test
    public void perfectGradesNeverExceedScaleMaximum() {
        List<Course> courses = Arrays.asList(course(1, Grade.A_PLUS), course(7, Grade.A_PLUS), course(12, Grade.A_PLUS));
        assertEquals("4.00", GpaCalculator.format(GpaCalculator.semester(courses, GradeScale.FOUR).gpa));
        assertEquals("5.00", GpaCalculator.format(GpaCalculator.semester(courses, GradeScale.FIVE).gpa));
    }

    @Test
    public void failingGradesOnEachScale() {
        List<Course> courses = Arrays.asList(course(3, Grade.F));
        assertEquals("0.00", GpaCalculator.format(GpaCalculator.semester(courses, GradeScale.FOUR).gpa));
        assertEquals("1.00", GpaCalculator.format(GpaCalculator.semester(courses, GradeScale.FIVE).gpa));
    }

    @Test
    public void cumulativeCombinesPreviousRecord() {
        // Semester: 15 credits at 4.00 = 60 points. Previous: 3.20 over 45 credits = 144 points.
        // Cumulative = 204 / 60 = 3.40
        List<Course> courses = Arrays.asList(course(3, Grade.A_PLUS), course(3, Grade.A_PLUS),
                course(3, Grade.A_PLUS), course(3, Grade.A_PLUS), course(3, Grade.A_PLUS));
        GpaResult semester = GpaCalculator.semester(courses, GradeScale.FOUR);
        GpaResult cumulative = GpaCalculator.cumulative(semester, new BigDecimal("3.20"), new BigDecimal("45"));
        assertEquals(0, new BigDecimal("60").compareTo(cumulative.credits));
        assertEquals("3.40", GpaCalculator.format(cumulative.gpa));
    }

    @Test
    public void cumulativeWithZeroPreviousCreditsEqualsSemester() {
        List<Course> courses = Arrays.asList(course(3, Grade.B), course(2, Grade.C_PLUS));
        GpaResult semester = GpaCalculator.semester(courses, GradeScale.FIVE);
        GpaResult cumulative = GpaCalculator.cumulative(semester, new BigDecimal("2.5"), BigDecimal.ZERO);
        assertEquals(0, semester.gpa.compareTo(cumulative.gpa));
    }

    @Test
    public void cumulativeRepeatingDecimal() {
        // Semester 3 credits × 3.00 = 9; previous 3.67 × 30 = 110.1; total 119.1 / 33 = 3.60909…
        GpaResult semester = GpaCalculator.semester(Arrays.asList(course(3, Grade.B)), GradeScale.FOUR);
        GpaResult cumulative = GpaCalculator.cumulative(semester, new BigDecimal("3.67"), new BigDecimal("30"));
        assertEquals("3.61", GpaCalculator.format(cumulative.gpa));
    }
}
