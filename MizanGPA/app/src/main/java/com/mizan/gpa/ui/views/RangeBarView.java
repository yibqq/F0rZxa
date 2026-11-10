package com.mizan.gpa.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.mizan.gpa.util.Ui;

/** A horizontal scale from 0 to max with the worst..best range highlighted and a marker at the expected value. */
public class RangeBarView extends View {
    private double min = Double.NaN, max = Double.NaN, mark = Double.NaN, scaleMax = 5;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint range = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public RangeBarView(Context c) {
        this(c, null);
    }

    public RangeBarView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        track.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest));
        range.setColor(Ui.withAlpha(MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary), 0.45f));
        marker.setColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary));
        markerRing.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow));
    }

    public void setRange(double worst, double best, double expected, double scaleMax) {
        this.min = worst;
        this.max = best;
        this.mark = expected;
        this.scaleMax = scaleMax <= 0 ? 5 : scaleMax;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), w), Ui.dp(getContext(), 22));
    }

    private float x(double v, float left, float width, boolean rtl) {
        float f = (float) Math.max(0, Math.min(1, v / scaleMax));
        return rtl ? left + width * (1 - f) : left + width * f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float pad = Ui.dp(getContext(), 8);
        float left = pad, width = getWidth() - pad * 2;
        float cy = getHeight() / 2f, th = Ui.dp(getContext(), 8);
        r.set(left, cy - th / 2, left + width, cy + th / 2);
        canvas.drawRoundRect(r, th, th, track);
        if (!Double.isNaN(min) && !Double.isNaN(max)) {
            float a = x(min, left, width, rtl), b = x(max, left, width, rtl);
            r.set(Math.min(a, b), cy - th / 2, Math.max(a, b), cy + th / 2);
            if (r.width() < th) r.inset(-(th - r.width()) / 2, 0);
            canvas.drawRoundRect(r, th, th, range);
        }
        if (!Double.isNaN(mark)) {
            float m = x(mark, left, width, rtl);
            canvas.drawCircle(m, cy, Ui.dp(getContext(), 8), markerRing);
            canvas.drawCircle(m, cy, Ui.dp(getContext(), 6), marker);
        }
    }
}
