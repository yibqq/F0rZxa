package com.gpacalculator.ui.util;

import android.content.Context;
import android.text.format.DateUtils;

import com.gpacalculator.R;
import com.gpacalculator.logic.GpaCalculator;

import java.math.BigDecimal;

/** Display formatting. Numbers always use Western digits so GPAs read the same in every language. */
public final class Formatters {
    private Formatters() {
    }

    public static String gpa(Context context, BigDecimal value) {
        return value == null ? context.getString(R.string.placeholder) : GpaCalculator.format(value);
    }

    public static String number(int value) {
        return Integer.toString(value);
    }

    public static String creditHours(Context context, int credits) {
        return context.getResources().getQuantityString(R.plurals.credit_hours, credits, number(credits));
    }

    public static String courses(Context context, int count) {
        return context.getResources().getQuantityString(R.plurals.courses, count, number(count));
    }

    /** "Today, 3:24 PM", "Yesterday, 9:10 AM" or "Nov 18, 2026". */
    public static String relativeDate(Context context, long millis) {
        String time = DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME);
        if (DateUtils.isToday(millis)) {
            return context.getString(R.string.today_at, time);
        }
        if (DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS)) {
            return context.getString(R.string.yesterday_at, time);
        }
        return DateUtils.formatDateTime(context, millis,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_MONTH);
    }

    public static String longDate(Context context, long millis) {
        return DateUtils.formatDateTime(context, millis,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_MONTH);
    }

    public static String dateTime(Context context, long millis) {
        return DateUtils.formatDateTime(context, millis,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_MONTH
                        | DateUtils.FORMAT_SHOW_TIME);
    }
}
