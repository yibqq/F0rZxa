package com.mizan.gpa.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

/** An animated 270° gauge showing a GPA relative to the scale maximum. */
public class GpaRingView extends View {
    private static final float START = 135f;
    private static final float SWEEP = 270f;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private double value = Double.NaN;
    private double max = 5;
    private float progress;
    private float shown;
    private String caption = "";
    private ValueAnimator animator;

    public GpaRingView(Context c) {
        this(c, null);
    }

    public GpaRingView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        subPaint.setTextAlign(Paint.Align.CENTER);
        int onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer);
        valuePaint.setColor(onSurface);
        subPaint.setColor(Ui.withAlpha(onSurface, 0.7f));
        track.setColor(Ui.withAlpha(onSurface, 0.12f));
        arc.setColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary));
    }

    public void setColors(int arcColor, int textColor) {
        arc.setColor(arcColor);
        valuePaint.setColor(textColor);
        subPaint.setColor(Ui.withAlpha(textColor, 0.7f));
        track.setColor(Ui.withAlpha(textColor, 0.12f));
        invalidate();
    }

    public void setValue(double gpa, double maxPoints, String caption) {
        this.max = maxPoints <= 0 ? 5 : maxPoints;
        this.caption = caption == null ? "" : caption;
        boolean changed = Double.compare(gpa, value) != 0;
        this.value = gpa;
        float target = Double.isNaN(gpa) ? 0f : (float) Math.max(0, Math.min(1, gpa / max));
        if (!changed && progress == target) {
            // Same value: keep any running animation going.
            invalidate();
            return;
        }
        if (animator != null) animator.cancel();
        progress = target;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(900);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(an -> {
            shown = (float) an.getAnimatedValue();
            invalidate();
        });
        animator.start();
        setContentDescription(Fmt.gpa(gpa) + " / " + Fmt.gpa(max));
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = Math.min(getDefaultSize(getSuggestedMinimumWidth(), w), getDefaultSize(getSuggestedMinimumHeight(), h));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float stroke = size * 0.085f;
        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);
        float pad = stroke / 2f + 2;
        float left = (getWidth() - size) / 2f, top = (getHeight() - size) / 2f;
        oval.set(left + pad, top + pad, left + size - pad, top + size - pad);
        canvas.drawArc(oval, START, SWEEP, false, track);
        float sweep = SWEEP * progress * shown;
        if (sweep > 0.5f) canvas.drawArc(oval, START, sweep, false, arc);

        double animatedValue = Double.isNaN(value) ? Double.NaN : value * shown;
        valuePaint.setTextSize(size * 0.25f);
        subPaint.setTextSize(size * 0.095f);
        float cy = getHeight() / 2f;
        canvas.drawText(Double.isNaN(value) ? Fmt.DASH : Fmt.gpa(animatedValue), getWidth() / 2f, cy + size * 0.07f, valuePaint);
        canvas.drawText(caption, getWidth() / 2f, cy + size * 0.25f, subPaint);
    }
}
