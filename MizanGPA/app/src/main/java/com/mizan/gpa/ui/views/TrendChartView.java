package com.mizan.gpa.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.mizan.gpa.R;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Bars for each semester's GPA and a line for the cumulative GPA. Projected semesters (using expected
 * grades) are drawn hatched/dashed. The time axis runs right-to-left in RTL locales.
 */
public class TrendChartView extends View {
    public static final class Point {
        public String label;
        public double bar;
        public double line;
        public boolean projected;

        public Point(String label, double bar, double line, boolean projected) {
            this.label = label;
            this.bar = bar;
            this.line = line;
            this.projected = projected;
        }
    }

    private final List<Point> points = new ArrayList<>();
    private double max = 5;
    private float anim = 1f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barProjected = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotHole = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valueText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final float[] radii = new float[8];
    private final android.text.TextPaint labelPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private float[] xs = new float[0];
    private float[] ys = new float[0];

    public TrendChartView(Context c) {
        this(c, null);
    }

    public TrendChartView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        int bar = ContextCompat.getColor(c, R.color.chart_bar);
        int line = ContextCompat.getColor(c, R.color.chart_line);
        int onVar = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow);
        barPaint.setColor(bar);
        barProjected.setColor(Ui.withAlpha(bar, 0.45f));
        linePaint.setColor(line);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Ui.dp(c, 2.5f));
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        dashPaint.set(linePaint);
        dashPaint.setPathEffect(new DashPathEffect(new float[]{Ui.dp(c, 5), Ui.dp(c, 4)}, 0));
        dotPaint.setColor(line);
        dotHole.setColor(surface);
        gridPaint.setColor(Ui.withAlpha(onVar, 0.18f));
        gridPaint.setStrokeWidth(Ui.dp(c, 1));
        axisText.setColor(onVar);
        axisText.setTextSize(Ui.dp(c, 11));
        valueText.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface));
        valueText.setTextSize(Ui.dp(c, 11));
        valueText.setTextAlign(Paint.Align.CENTER);
        valueText.setFakeBoldText(true);
    }

    public void setData(List<Point> data, double maxPoints) {
        points.clear();
        points.addAll(data);
        max = maxPoints <= 0 ? 5 : maxPoints;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(800);
        va.setInterpolator(new DecelerateInterpolator(1.5f));
        va.addUpdateListener(an -> {
            anim = (float) an.getAnimatedValue();
            invalidate();
        });
        va.start();
        StringBuilder cd = new StringBuilder();
        for (Point p : data) cd.append(p.label).append(": ").append(Fmt.gpa(p.bar)).append(", ").append(Fmt.gpa(p.line)).append(". ");
        setContentDescription(cd);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (points.isEmpty()) return;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float axisW = Ui.dp(getContext(), 30);
        float labelH = Ui.dp(getContext(), 22);
        float topPad = Ui.dp(getContext(), 18);
        float left = rtl ? getPaddingLeft() : getPaddingLeft() + axisW;
        float right = getWidth() - getPaddingRight() - (rtl ? axisW : 0);
        float bottom = getHeight() - getPaddingBottom() - labelH;
        float top = getPaddingTop() + topPad;
        float h = bottom - top;

        // Grid lines at 0, 25, 50, 75, 100% of the scale.
        axisText.setTextAlign(rtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
        for (int i = 0; i <= 4; i++) {
            float y = bottom - h * i / 4f;
            canvas.drawLine(left, y, right, y, gridPaint);
            String t = Fmt.num(max * i / 4.0);
            float tx = rtl ? right + Ui.dp(getContext(), 6) : left - Ui.dp(getContext(), 6);
            canvas.drawText(t, tx, y + axisText.getTextSize() / 3f, axisText);
        }

        int n = points.size();
        float slot = (right - left) / n;
        float barW = Math.min(slot * 0.5f, Ui.dp(getContext(), 34));
        float radius = Ui.dp(getContext(), 6);
        if (xs.length < n) {
            xs = new float[n];
            ys = new float[n];
        }
        labelPaint.set(axisText);
        axisText.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            Point p = points.get(i);
            int pos = rtl ? n - 1 - i : i;
            float cx = left + slot * pos + slot / 2f;
            xs[i] = cx;
            float bh = (float) (Math.max(0, Math.min(max, p.bar)) / max) * h * anim;
            rect.set(cx - barW / 2f, bottom - bh, cx + barW / 2f, bottom);
            if (bh > 0.5f) {
                float r = Math.min(radius, bh);
                path.reset();
                radii[0] = radii[1] = radii[2] = radii[3] = r;
                path.addRoundRect(rect, radii, Path.Direction.CW);
                canvas.drawPath(path, p.projected ? barProjected : barPaint);
            }
            ys[i] = bottom - (float) (Math.max(0, Math.min(max, p.line)) / max) * h * anim;
            String label = TextUtils.ellipsize(p.label, labelPaint, slot - Ui.dp(getContext(), 4),
                    TextUtils.TruncateAt.END).toString();
            canvas.drawText(label, cx, bottom + labelH - Ui.dp(getContext(), 6), axisText);
        }
        // Cumulative line; segments leading to projected points are dashed.
        for (int i = 1; i < n; i++) {
            path.reset();
            path.moveTo(xs[i - 1], ys[i - 1]);
            path.lineTo(xs[i], ys[i]);
            canvas.drawPath(path, points.get(i).projected ? dashPaint : linePaint);
        }
        float r = Ui.dp(getContext(), 4.5f);
        for (int i = 0; i < n; i++) {
            canvas.drawCircle(xs[i], ys[i], r, dotPaint);
            if (points.get(i).projected) canvas.drawCircle(xs[i], ys[i], r * 0.5f, dotHole);
            if (anim >= 1f) canvas.drawText(Fmt.gpa(points.get(i).line), xs[i], ys[i] - Ui.dp(getContext(), 8), valueText);
        }
    }
}
