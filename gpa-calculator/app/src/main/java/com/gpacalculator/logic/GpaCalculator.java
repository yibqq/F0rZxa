package com.gpacalculator.logic;

import com.gpacalculator.model.Course;
import com.gpacalculator.model.GpaResult;
import com.gpacalculator.model.GradeScale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * GPA arithmetic. Everything is computed with {@link BigDecimal} so values such as
 * 3.75 × 3 are exact; rounding happens only once, when a value is displayed.
 */
public final class GpaCalculator {
    /** Digits kept for intermediate division results. */
    private static final int WORKING_SCALE = 10;
    /** Digits shown to the user. */
    public static final int DISPLAY_SCALE = 2;

    private GpaCalculator() {
    }

    /** Semester GPA = Σ(grade points × credits) ÷ Σ credits. */
    public static GpaResult semester(List<Course> courses, GradeScale scale) {
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal points = BigDecimal.ZERO;
        for (Course course : courses) {
            BigDecimal courseCredits = BigDecimal.valueOf(course.credits);
            credits = credits.add(courseCredits);
            points = points.add(course.grade.points(scale).multiply(courseCredits));
        }
        return new GpaResult(credits, points, divide(points, credits));
    }

    /**
     * Cumulative GPA = (previous GPA × previous credits + semester quality points)
     * ÷ (previous credits + semester credits).
     */
    public static GpaResult cumulative(GpaResult semester, BigDecimal previousGpa, BigDecimal previousCredits) {
        BigDecimal previousPoints = previousGpa.multiply(previousCredits);
        BigDecimal credits = semester.credits.add(previousCredits);
        BigDecimal points = semester.qualityPoints.add(previousPoints);
        return new GpaResult(credits, points, divide(points, credits));
    }

    /** Rounds half-up to two decimals, e.g. 3.335 → "3.34". */
    public static String format(BigDecimal value) {
        return round(value).toPlainString();
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal divide(BigDecimal points, BigDecimal credits) {
        if (credits.signum() == 0) {
            return null;
        }
        return points.divide(credits, WORKING_SCALE, RoundingMode.HALF_UP);
    }
}
