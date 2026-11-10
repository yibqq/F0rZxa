package com.mizan.gpa.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A squircle (superellipse |x|^n + |y|^n = 1) filled with a diagonal gradient. Unlike a rounded
 * rectangle, its curvature changes smoothly, which gives the softer "continuous corner" look.
 */
public class SquircleDrawable extends Drawable {
    /** Exponent of the superellipse: 2 is a circle, higher values get closer to a square. */
    public static final float DEFAULT_EXPONENT = 4.2f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int from, to;
    private final float exponent;

    public SquircleDrawable(int from, int to) {
        this(from, to, DEFAULT_EXPONENT);
    }

    public SquircleDrawable(int from, int to, float exponent) {
        this.from = from;
        this.to = to;
        this.exponent = exponent;
    }

    /** The squircle outline of the given bounds. */
    public static Path path(Path out, float left, float top, float right, float bottom, float n) {
        out.reset();
        float cx = (left + right) / 2f, cy = (top + bottom) / 2f;
        float a = (right - left) / 2f, b = (bottom - top) / 2f;
        int steps = 96;
        for (int i = 0; i <= steps; i++) {
            double t = 2 * Math.PI * i / steps;
            double c = Math.cos(t), s = Math.sin(t);
            float x = (float) (cx + a * Math.signum(c) * Math.pow(Math.abs(c), 2.0 / n));
            float y = (float) (cy + b * Math.signum(s) * Math.pow(Math.abs(s), 2.0 / n));
            if (i == 0) out.moveTo(x, y);
            else out.lineTo(x, y);
        }
        out.close();
        return out;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect r) {
        path(path, r.left, r.top, r.right, r.bottom, exponent);
        paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, from, to, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawPath(path, paint);
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        // The squircle is convex, so it can serve as the view outline for shadows and clipping.
        if (android.os.Build.VERSION.SDK_INT >= 30) outline.setPath(path);
        else outline.setConvexPath(path);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
