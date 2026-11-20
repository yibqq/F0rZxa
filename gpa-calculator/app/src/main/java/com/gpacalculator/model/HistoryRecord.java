package com.gpacalculator.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/** A saved calculation. */
public final class HistoryRecord {
    public final long id;
    public final long createdAt;
    public final GradeScale scale;
    public final List<Course> courses;
    public final BigDecimal semesterGpa;
    public final int semesterCredits;
    /** Previous record values; all three are null when cumulative GPA was not used. */
    public final BigDecimal previousGpa;
    public final Integer previousCredits;
    public final BigDecimal cumulativeGpa;

    public HistoryRecord(long id, long createdAt, GradeScale scale, List<Course> courses,
                         BigDecimal semesterGpa, int semesterCredits,
                         BigDecimal previousGpa, Integer previousCredits, BigDecimal cumulativeGpa) {
        this.id = id;
        this.createdAt = createdAt;
        this.scale = scale;
        this.courses = Collections.unmodifiableList(courses);
        this.semesterGpa = semesterGpa;
        this.semesterCredits = semesterCredits;
        this.previousGpa = previousGpa;
        this.previousCredits = previousCredits;
        this.cumulativeGpa = cumulativeGpa;
    }

    public boolean hasCumulative() {
        return cumulativeGpa != null && previousGpa != null && previousCredits != null;
    }

    public int totalCredits() {
        return semesterCredits + (hasCumulative() ? previousCredits : 0);
    }

    public HistoryRecord withId(long newId) {
        return new HistoryRecord(newId, createdAt, scale, courses, semesterGpa, semesterCredits,
                previousGpa, previousCredits, cumulativeGpa);
    }

    /** True when both records would render identically in the history list. */
    public boolean sameContent(HistoryRecord other) {
        return id == other.id && createdAt == other.createdAt && scale == other.scale
                && courses.equals(other.courses)
                && semesterGpa.compareTo(other.semesterGpa) == 0
                && hasCumulative() == other.hasCumulative()
                && (!hasCumulative() || cumulativeGpa.compareTo(other.cumulativeGpa) == 0);
    }
}
