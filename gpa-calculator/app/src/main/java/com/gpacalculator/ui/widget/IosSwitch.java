package com.gpacalculator.ui.widget;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Switch;

import com.gpacalculator.R;
import com.gpacalculator.ui.util.Dimens;
import com.gpacalculator.ui.util.Haptics;
import com.gpacalculator.ui.util.Motion;

/** 51×31 iOS toggle. The thumb stretches while pressed and springs into place. */
public final class IosSwitch extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(IosSwitch view, boolean checked, boolean fromUser);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private final int offColor;
    private final int onColor;
    private final float trackWidth;
    private final float trackHeight;
    private final float pad;
    private final float stretch;
    private final float shadowOffset;

    private boolean checked;
    private float progress;
    private float press;
    private ValueAnimator progressAnimator;
    private ValueAnimator pressAnimator;
    private OnCheckedChangeListener listener;

    public IosSwitch(Context context) {
        this(context, null);
    }

    public IosSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        offColor = context.getColor(R.color.switch_off);
        onColor = context.getColor(R.color.success);
        thumbPaint.setColor(context.getColor(R.color.switch_thumb));
        shadowPaint.setColor(context.getColor(R.color.shadow));
        trackWidth = Dimens.dpF(context, 51);
        trackHeight = Dimens.dpF(context, 31);
        pad = Dimens.dpF(context, 2);
        stretch = Dimens.dpF(context, 7);
        shadowOffset = Dimens.dpF(context, 1.5f);
        setClickable(true);
        setFocusable(true);
    }

    public boolean isChecked() {
        return checked;
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener l) {
        listener = l;
    }

    /** Programmatic change; never plays haptics. */
    public void setChecked(boolean value, boolean animate) {
        setCheckedInternal(value, animate && isLaidOut(), false);
    }

    /** Toggle as if the user tapped it. */
    public void toggleFromUser() {
        setCheckedInternal(!checked, true, true);
    }

    private void setCheckedInternal(boolean value, boolean animate, boolean fromUser) {
        if (checked == value) return;
        checked = value;
        if (progressAnimator != null) progressAnimator.cancel();
        float target = value ? 1f : 0f;
        if (animate) {
            progressAnimator = ValueAnimator.ofFloat(progress, target);
            progressAnimator.setDuration(Motion.SPRING_DURATION);
            progressAnimator.setInterpolator(Motion.SPRING);
            progressAnimator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            progressAnimator.start();
        } else {
            progress = target;
            invalidate();
        }
        if (fromUser) Haptics.toggle(this, value);
        if (listener != null) listener.onCheckedChanged(this, value, fromUser);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(Math.round(trackWidth), widthMeasureSpec),
                resolveSize(Math.round(trackHeight), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float left = (getWidth() - trackWidth) / 2f;
        float top = (getHeight() - trackHeight) / 2f;
        float p = Math.max(0f, Math.min(1f, progress));

        rect.set(left, top, left + trackWidth, top + trackHeight);
        trackPaint.setColor((int) argb.evaluate(p, offColor, onColor));
        canvas.drawRoundRect(rect, trackHeight / 2f, trackHeight / 2f, trackPaint);

        float d = trackHeight - 2 * pad;
        float thumbW = d + press * stretch;
        float travel = trackWidth - 2 * pad - thumbW;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float thumbLeft = rtl
                ? left + trackWidth - pad - thumbW - p * travel
                : left + pad + p * travel;
        rect.set(thumbLeft, top + pad, thumbLeft + thumbW, top + pad + d);

        rect.offset(0, shadowOffset);
        canvas.drawRoundRect(rect, d / 2f, d / 2f, shadowPaint);
        rect.offset(0, -shadowOffset);
        canvas.drawRoundRect(rect, d / 2f, d / 2f, thumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                animatePress(1f);
                return true;
            case MotionEvent.ACTION_UP:
                animatePress(0f);
                if (event.getX() >= 0 && event.getX() <= getWidth()
                        && event.getY() >= 0 && event.getY() <= getHeight()) {
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                animatePress(0f);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        boolean handled = super.performClick();
        toggleFromUser();
        return handled;
    }

    private void animatePress(float to) {
        if (pressAnimator != null) pressAnimator.cancel();
        pressAnimator = ValueAnimator.ofFloat(press, to);
        pressAnimator.setDuration(to > 0 ? 160 : 300);
        pressAnimator.setInterpolator(Motion.EASE_OUT);
        pressAnimator.addUpdateListener(a -> {
            press = (float) a.getAnimatedValue();
            invalidate();
        });
        pressAnimator.start();
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return Switch.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(true);
        info.setChecked(checked);
    }
}
