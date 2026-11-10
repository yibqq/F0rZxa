package com.mizan.gpa.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.mizan.gpa.R;
import com.mizan.gpa.calc.AttendanceCalculator;
import com.mizan.gpa.data.GradeDef;
import com.mizan.gpa.data.GradeScale;

public final class Ui {
    private Ui() {}

    public static final int[] COURSE_COLORS = {
            0xFF0E9F8E, 0xFF5B6CF0, 0xFFF08A24, 0xFFE0457B, 0xFF8E5BD8,
            0xFF2F9BE0, 0xFF7CB342, 0xFFD9A400, 0xFF6D7F8C, 0xFFD3542F
    };

    public static int courseColor(int stored, int index) {
        return stored != 0 ? stored : COURSE_COLORS[Math.abs(index) % COURSE_COLORS.length];
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static int attr(View v, @AttrRes int attr) {
        return MaterialColors.getColor(v, attr);
    }

    public static int attr(Context c, @AttrRes int attr) {
        return MaterialColors.getColor(c, attr, Color.GRAY);
    }

    /** Color for a grade based on how close its points are to the scale maximum. */
    public static int gradeColor(Context c, GradeDef g, GradeScale scale) {
        if (g == null) return attr(c, com.google.android.material.R.attr.colorOutline);
        if (scale.isFailing(g)) return ContextCompat.getColor(c, R.color.status_danger);
        double max = scale.maxPoints(), min = scale.minPoints();
        double ratio = max - min > 0 ? (g.points - min) / (max - min) : 1;
        if (ratio >= 0.85) return ContextCompat.getColor(c, R.color.status_excellent);
        if (ratio >= 0.6) return ContextCompat.getColor(c, R.color.status_good);
        if (ratio >= 0.35) return ContextCompat.getColor(c, R.color.status_warning);
        return ContextCompat.getColor(c, R.color.status_danger);
    }

    /** Color for a GPA value relative to the scale. */
    public static int gpaColor(Context c, double gpa, GradeScale scale) {
        if (Double.isNaN(gpa)) return attr(c, com.google.android.material.R.attr.colorOutline);
        double max = scale.maxPoints(), min = scale.minPoints();
        double ratio = max - min > 0 ? (gpa - min) / (max - min) : 1;
        if (ratio >= 0.8) return ContextCompat.getColor(c, R.color.status_excellent);
        if (ratio >= 0.55) return ContextCompat.getColor(c, R.color.status_good);
        if (ratio >= 0.3) return ContextCompat.getColor(c, R.color.status_warning);
        return ContextCompat.getColor(c, R.color.status_danger);
    }

    public static int statusColor(Context c, AttendanceCalculator.Status s) {
        switch (s) {
            case DEPRIVED:
            case CRITICAL:
                return ContextCompat.getColor(c, R.color.status_danger);
            case WARNING:
                return ContextCompat.getColor(c, R.color.status_warning);
            case SAFE:
                return ContextCompat.getColor(c, R.color.status_excellent);
            default:
                return attr(c, com.google.android.material.R.attr.colorOutline);
        }
    }

    public static String statusLabel(Context c, AttendanceCalculator.Status s) {
        switch (s) {
            case DEPRIVED: return c.getString(R.string.status_deprived);
            case CRITICAL: return c.getString(R.string.status_critical);
            case WARNING: return c.getString(R.string.status_warning);
            case SAFE: return c.getString(R.string.status_safe);
            default: return c.getString(R.string.status_unknown);
        }
    }

    /** Makes a pill-shaped colored badge out of a TextView. */
    public static void badge(TextView tv, String text, int color) {
        tv.setText(text);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(tv.getContext(), 100));
        bg.setColor(withAlpha(color, 0.16f));
        tv.setBackground(bg);
        tv.setTextColor(color);
    }

    public static void dot(View v, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        v.setBackground(bg);
    }

    public static void roundedFill(View v, int color, float radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(v.getContext(), radiusDp));
        bg.setColor(color);
        v.setBackground(bg);
    }

    public static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(alpha * 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Mixes {@code b} into {@code a}; ratio 0 = a, 1 = b. */
    public static int blend(int a, int b, float ratio) {
        float r = 1 - ratio;
        return Color.argb(
                Math.round(Color.alpha(a) * r + Color.alpha(b) * ratio),
                Math.round(Color.red(a) * r + Color.red(b) * ratio),
                Math.round(Color.green(a) * r + Color.green(b) * ratio),
                Math.round(Color.blue(a) * r + Color.blue(b) * ratio));
    }

    public static ColorStateList csl(int color) {
        return ColorStateList.valueOf(color);
    }

    /** Adds the navigation bar height to the bottom padding (for edge-to-edge scrolling content). */
    public static void padForNavBar(View v) {
        final int start = v.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), start + i.bottom);
            return insets;
        });
    }

    /** Lifts a floating button above the navigation bar. */
    public static void liftForNavBar(View v) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        final int start = lp.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            p.bottomMargin = start + i.bottom;
            view.setLayoutParams(p);
            return insets;
        });
    }

    /** Pads left/right for system bars and display cutouts (landscape). */
    public static void padSides(View v) {
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(i.left, view.getPaddingTop(), i.right, view.getPaddingBottom());
            return insets;
        });
    }

    public static void snack(Activity a, CharSequence msg) {
        View root = a.findViewById(R.id.snack_anchor_root);
        if (root == null) root = a.findViewById(android.R.id.content);
        Snackbar s = Snackbar.make(root, msg, Snackbar.LENGTH_LONG);
        View anchor = a.findViewById(R.id.nav);
        if (anchor instanceof com.google.android.material.bottomnavigation.BottomNavigationView) s.setAnchorView(anchor);
        s.show();
    }

    public static boolean isRtl(Context c) {
        return c.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }
}
