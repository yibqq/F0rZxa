package com.gpacalculator.model;

import java.math.BigDecimal;

/** The two supported grading scales. */
public enum GradeScale {
    FOUR("4", new BigDecimal("4.00")),
    FIVE("5", new BigDecimal("5.00"));

    private final String id;
    private final BigDecimal max;

    GradeScale(String id, BigDecimal max) {
        this.id = id;
        this.max = max;
    }

    /** Stable identifier used for persistence. */
    public String id() {
        return id;
    }

    /** Highest attainable GPA on this scale. */
    public BigDecimal max() {
        return max;
    }

    public static GradeScale fromId(String id) {
        for (GradeScale scale : values()) {
            if (scale.id.equals(id)) {
                return scale;
            }
        }
        return FOUR;
    }
}
