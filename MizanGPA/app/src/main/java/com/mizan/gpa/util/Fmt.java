package com.mizan.gpa.util;

import android.content.Context;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Locale;

/** Number, date and time formatting. Numbers always use Western digits for clarity. */
public final class Fmt {
    private static final DecimalFormatSymbols SYM = DecimalFormatSymbols.getInstance(Locale.US);
    private static final DecimalFormat GPA = new DecimalFormat("0.00", SYM);
    private static final DecimalFormat NUM = new DecimalFormat("0.##", SYM);
    private static final DecimalFormat PCT = new DecimalFormat("0.#", SYM);
    public static final String DASH = "—";

    private Fmt() {}

    public static String gpa(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) ? DASH : GPA.format(v);
    }

    public static String num(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) ? DASH : NUM.format(v);
    }

    /** Percentage, kept left-to-right so "56.7%" never flips to "%56.7" inside Arabic text. */
    public static String pct(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) ? DASH : ltr(PCT.format(v) + "%");
    }

    /** Wraps Latin snippets such as grade letters ("A+") in a left-to-right isolate for RTL sentences. */
    public static String ltr(String s) {
        return "\u2066" + s + "\u2069";
    }

    public static String signed(double v) {
        if (Double.isNaN(v)) return DASH;
        String s = GPA.format(Math.abs(v));
        return (v > 0.0049 ? "+" : v < -0.0049 ? "−" : "±") + s;
    }

    /** Parses a user-typed number, accepting Arabic-Indic digits and the Arabic decimal comma. */
    public static double parse(CharSequence text) {
        if (text == null) return Double.NaN;
        StringBuilder b = new StringBuilder();
        for (char ch : text.toString().trim().toCharArray()) {
            if (ch >= '٠' && ch <= '٩') b.append((char) ('0' + ch - '٠'));
            else if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + ch - '۰'));
            else if (ch == '٫' || ch == ',') b.append('.');
            else b.append(ch);
        }
        if (b.length() == 0) return Double.NaN;
        try {
            return Double.parseDouble(b.toString());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static String time(Context ctx, int minutes) {
        int h = (minutes / 60) % 24;
        int m = minutes % 60;
        if (android.text.format.DateFormat.is24HourFormat(ctx)) {
            return String.format(Locale.US, "%02d:%02d", h, m);
        }
        int h12 = h % 12 == 0 ? 12 : h % 12;
        String ampm = h < 12 ? ctx.getString(com.mizan.gpa.R.string.am) : ctx.getString(com.mizan.gpa.R.string.pm);
        return String.format(Locale.US, "%d:%02d %s", h12, m, ampm);
    }

    public static String timeRange(Context ctx, int start, int end) {
        return time(ctx, start) + " – " + time(ctx, end);
    }

    public static Locale locale(Context ctx) {
        return ctx.getResources().getConfiguration().getLocales().get(0);
    }

    /** Calendar day constant (SUNDAY = 1) to a localized name. */
    public static String dayName(Context ctx, int calendarDay, boolean shortName) {
        return toDayOfWeek(calendarDay).getDisplayName(shortName ? TextStyle.SHORT : TextStyle.FULL, locale(ctx));
    }

    public static DayOfWeek toDayOfWeek(int calendarDay) {
        // Calendar: SUNDAY=1..SATURDAY=7; DayOfWeek: MONDAY=1..SUNDAY=7
        return calendarDay == 1 ? DayOfWeek.SUNDAY : DayOfWeek.of(calendarDay - 1);
    }

    public static int toCalendarDay(DayOfWeek d) {
        return d == DayOfWeek.SUNDAY ? 1 : d.getValue() + 1;
    }

    public static String today() {
        return LocalDate.now().toString();
    }

    public static String date(Context ctx, String iso) {
        try {
            LocalDate d = LocalDate.parse(iso);
            String day = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale(ctx));
            return day + " · " + DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(locale(ctx)).withDecimalStyle(java.time.format.DecimalStyle.STANDARD).format(d);
        } catch (Exception e) {
            return iso;
        }
    }

    public static String dateLong(Context ctx, LocalDate d) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale(ctx))
                .withDecimalStyle(java.time.format.DecimalStyle.STANDARD).format(d);
    }
}
