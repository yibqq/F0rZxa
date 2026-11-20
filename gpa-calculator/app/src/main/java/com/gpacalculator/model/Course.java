package com.gpacalculator.model;

import java.util.Objects;

/** An immutable course entry. */
public final class Course {
    public static final int MIN_CREDITS = 1;
    public static final int MAX_CREDITS = 12;
    public static final int MAX_NAME_LENGTH = 40;

    public final long id;
    public final String name;
    public final int credits;
    public final Grade grade;

    public Course(long id, String name, int credits, Grade grade) {
        if (credits < MIN_CREDITS || credits > MAX_CREDITS) {
            throw new IllegalArgumentException("Credits out of range: " + credits);
        }
        this.id = id;
        String trimmed = name == null ? "" : name.trim();
        this.name = trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
        this.credits = credits;
        this.grade = Objects.requireNonNull(grade, "grade");
    }

    public static boolean isValidCredits(int credits) {
        return credits >= MIN_CREDITS && credits <= MAX_CREDITS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Course)) return false;
        Course other = (Course) o;
        return id == other.id && credits == other.credits
                && name.equals(other.name) && grade == other.grade;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, credits, grade);
    }
}
