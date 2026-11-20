package com.gpacalculator.logic;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Parsing and validation for numbers typed by the user. Accepts Arabic-Indic digits. */
public final class NumberInput {
    private static final Pattern DECIMAL = Pattern.compile("\\d+(\\.\\d*)?|\\.\\d+");
    private static final Pattern GPA_DRAFT = Pattern.compile("\\d?(\\.\\d{0,3})?");

    private NumberInput() {
    }

    /** Converts Arabic-Indic and Persian digits to ASCII and any decimal separator to '.'. */
    public static String normalize(CharSequence input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '٠' && c <= '٩') {
                out.append((char) ('0' + (c - '٠')));
            } else if (c >= '۰' && c <= '۹') {
                out.append((char) ('0' + (c - '۰')));
            } else if (c == ',' || c == '٫') {
                out.append('.');
            } else if (!Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static boolean isBlank(CharSequence input) {
        return normalize(input).isEmpty();
    }

    /** Parses a non-negative decimal, returning {@code null} for empty or malformed text. */
    public static BigDecimal parse(CharSequence input) {
        String normalized = normalize(input);
        if (normalized.isEmpty() || !DECIMAL.matcher(normalized).matches()) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Whether partially typed text could still become a valid GPA (one integer digit, up to three decimals). */
    public static boolean isGpaDraft(CharSequence input) {
        return GPA_DRAFT.matcher(normalize(input)).matches();
    }

    /** Whether partially typed text is a whole number of at most {@code maxDigits} digits. */
    public static boolean isWholeNumberDraft(CharSequence input, int maxDigits) {
        String normalized = normalize(input);
        if (normalized.length() > maxDigits) {
            return false;
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
