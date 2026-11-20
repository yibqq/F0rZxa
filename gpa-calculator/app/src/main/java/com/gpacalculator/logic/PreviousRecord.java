package com.gpacalculator.logic;

import com.gpacalculator.model.GradeScale;

import java.math.BigDecimal;

/** Validated "previous GPA + completed credits" input used for cumulative GPA. */
public final class PreviousRecord {
    public static final int MAX_CREDITS = 999;

    public enum Status {
        /** One or both fields are still empty. */
        INCOMPLETE,
        GPA_OUT_OF_RANGE,
        CREDITS_OUT_OF_RANGE,
        VALID
    }

    public final Status status;
    public final BigDecimal gpa;
    public final BigDecimal credits;

    private PreviousRecord(Status status, BigDecimal gpa, BigDecimal credits) {
        this.status = status;
        this.gpa = gpa;
        this.credits = credits;
    }

    public boolean isValid() {
        return status == Status.VALID;
    }

    public static PreviousRecord parse(CharSequence gpaText, CharSequence creditsText, GradeScale scale) {
        boolean gpaBlank = NumberInput.isBlank(gpaText);
        boolean creditsBlank = NumberInput.isBlank(creditsText);
        BigDecimal gpa = NumberInput.parse(gpaText);
        BigDecimal credits = NumberInput.parse(creditsText);

        if (!gpaBlank && (gpa == null || gpa.signum() < 0 || gpa.compareTo(scale.max()) > 0)) {
            return new PreviousRecord(Status.GPA_OUT_OF_RANGE, null, null);
        }
        if (!creditsBlank && (credits == null || !isWholeNumber(credits)
                || credits.compareTo(BigDecimal.valueOf(MAX_CREDITS)) > 0)) {
            return new PreviousRecord(Status.CREDITS_OUT_OF_RANGE, null, null);
        }
        if (gpaBlank || creditsBlank) {
            return new PreviousRecord(Status.INCOMPLETE, null, null);
        }
        return new PreviousRecord(Status.VALID, gpa, credits);
    }

    private static boolean isWholeNumber(BigDecimal value) {
        return value.signum() >= 0 && value.stripTrailingZeros().scale() <= 0;
    }
}
