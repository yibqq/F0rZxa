package com.gpacalculator.ui.util;

import android.text.InputFilter;

import com.gpacalculator.logic.NumberInput;

/** Input filters that reject keystrokes which could never form a valid value. */
public final class NumberFilters {
    private NumberFilters() {
    }

    public static InputFilter gpa() {
        return (source, start, end, dest, dstart, dend) -> {
            CharSequence result = candidate(source, start, end, dest, dstart, dend);
            return NumberInput.isGpaDraft(result) ? null : "";
        };
    }

    public static InputFilter wholeNumber(int maxDigits) {
        return (source, start, end, dest, dstart, dend) -> {
            CharSequence result = candidate(source, start, end, dest, dstart, dend);
            return NumberInput.isWholeNumberDraft(result, maxDigits) ? null : "";
        };
    }

    private static CharSequence candidate(CharSequence source, int start, int end,
                                          android.text.Spanned dest, int dstart, int dend) {
        return new StringBuilder(dest.length() - (dend - dstart) + (end - start))
                .append(dest, 0, dstart)
                .append(source, start, end)
                .append(dest, dend, dest.length());
    }
}
