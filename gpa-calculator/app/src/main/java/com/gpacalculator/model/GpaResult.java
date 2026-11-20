package com.gpacalculator.model;

import java.math.BigDecimal;

/** Outcome of a GPA calculation, kept at full precision. */
public final class GpaResult {
    public final BigDecimal credits;
    public final BigDecimal qualityPoints;
    /** Unrounded GPA, or {@code null} when no credits were attempted. */
    public final BigDecimal gpa;

    public GpaResult(BigDecimal credits, BigDecimal qualityPoints, BigDecimal gpa) {
        this.credits = credits;
        this.qualityPoints = qualityPoints;
        this.gpa = gpa;
    }

    public boolean hasGpa() {
        return gpa != null;
    }
}
