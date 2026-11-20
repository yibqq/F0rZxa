package com.gpacalculator.model;

import java.math.BigDecimal;

/**
 * Letter grades and their grade points on each scale.
 *
 * <p>The 5.0 scale is the 4.0 scale shifted by one point for every grade
 * (F = 1.00), which is the convention used by universities that grade on 5.</p>
 */
public enum Grade {
    A_PLUS("A+", "4.00", "5.00"),
    A("A", "3.75", "4.75"),
    B_PLUS("B+", "3.50", "4.50"),
    B("B", "3.00", "4.00"),
    C_PLUS("C+", "2.50", "3.50"),
    C("C", "2.00", "3.00"),
    D_PLUS("D+", "1.50", "2.50"),
    D("D", "1.00", "2.00"),
    F("F", "0.00", "1.00");

    private final String code;
    private final BigDecimal fourPoints;
    private final BigDecimal fivePoints;

    Grade(String code, String fourPoints, String fivePoints) {
        this.code = code;
        this.fourPoints = new BigDecimal(fourPoints);
        this.fivePoints = new BigDecimal(fivePoints);
    }

    /** Display label and stable persistence code, e.g. "B+". */
    public String code() {
        return code;
    }

    public BigDecimal points(GradeScale scale) {
        return scale == GradeScale.FIVE ? fivePoints : fourPoints;
    }

    /** Returns the grade for a persisted code, or {@code null} if unknown. */
    public static Grade fromCode(String code) {
        for (Grade grade : values()) {
            if (grade.code.equals(code)) {
                return grade;
            }
        }
        return null;
    }
}
