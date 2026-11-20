package com.gpacalculator.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import com.gpacalculator.R;
import com.gpacalculator.ui.util.Dimens;
import com.gpacalculator.ui.util.Haptics;
import com.gpacalculator.ui.util.Motion;

/** iOS-style segmented control with a sliding, spring-animated thumb. */
public final class SegmentedControl extends View {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int index);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius;
    private final float thumbRadius;
    private final float inset;
    private final float shadowOffset;

    private String[] options = new String[0];
    private String[] descriptions;
    private int selected;
    private float thumbPosition;
    private float thumbScale = 1f;
    private int pendingIndex = -1;
    private boolean pressingThumb;
    private ValueAnimator positionAnimator;
    private ValueAnimator scaleAnimator;
    private OnSelectionChangedListener listener;

    public SegmentedControl(Context context) {
        this(context, null);
    }

    public SegmentedControl(Context context, AttributeSet attrs) {
        super(context, attrs);
        radius = Dimens.dpF(context, 9);
        thumbRadius = Dimens.dpF(context, 7);
        inset = Dimens.dpF(context, 2);
        shadowOffset = Dimens.dpF(context, 1);

        trackPaint.setColor(context.getColor(R.color.segment_track));
        thumbPaint.setColor(context.getColor(R.color.segment_thumb));
        shadowPaint.setColor(context.getColor(R.color.shadow));

        float textSize = Dimens.sp(context, 13);
        int label = context.getColor(R.color.label);
        textPaint.setTextSize(textSize);
        textPaint.setColor(label);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        selectedTextPaint.setTextSize(textSize);
        selectedTextPaint.setColor(label);
        selectedTextPaint.setTextAlign(Paint.Align.CENTER);
        selectedTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        setClickable(true);
        setFocusable(true);
    }

    public void setOptions(String[] labels, String[] contentDescriptions) {
        options = labels.clone();
        descriptions = contentDescriptions == null ? null : contentDescriptions.clone();
        selected = Math.min(selected, Math.max(0, options.length - 1));
        thumbPosition = selected;
        updateDescription();
        invalidate();
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener l) {
        listener = l;
    }

    public int getSelectedIndex() {
        return selected;
    }

    public void setSelectedIndex(int index, boolean animate) {
        if (index < 0 || index >= options.length) return;
        selected = index;
        if (positionAnimator != null) positionAnimator.cancel();
        if (animate && isLaidOut()) {
            positionAnimator = ValueAnimator.ofFloat(thumbPosition, index);
            positionAnimator.setDuration(Motion.SPRING_DURATION);
            positionAnimator.setInterpolator(Motion.SPRING);
            positionAnimator.addUpdateListener(a -> {
                thumbPosition = (float) a.getAnimatedValue();
                invalidate();
            });
            positionAnimator.start();
        } else {
            thumbPosition = index;
            invalidate();
        }
        updateDescription();
    }

    private void selectFromUser(int index) {
        if (index == selected) return;
        setSelectedIndex(index, true);
        Haptics.selection(this);
        if (listener != null) listener.onSelectionChanged(index);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize(Dimens.dp(getContext(), 200), widthMeasureSpec);
        int h = resolveSize(Dimens.dp(getContext(), 32), heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    private boolean isRtl() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    private float segmentWidth() {
        return options.length == 0 ? 0 : (getWidth() - 2 * inset) / options.length;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = options.length;
        if (n == 0) return;
        float w = getWidth();
        float h = getHeight();

        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        float segW = segmentWidth();
        float pos = Math.max(0f, Math.min(n - 1f, thumbPosition));
        float visual = isRtl() ? (n - 1 - pos) : pos;
        float left = inset + visual * segW;
        rect.set(left, inset, left + segW, h - inset);
        if (thumbScale != 1f) {
            float dx = rect.width() * (1 - thumbScale) / 2f;
            float dy = rect.height() * (1 - thumbScale) / 2f;
            rect.inset(dx, dy);
        }
        rect.offset(0, shadowOffset);
        canvas.drawRoundRect(rect, thumbRadius, thumbRadius, shadowPaint);
        rect.offset(0, -shadowOffset);
        canvas.drawRoundRect(rect, thumbRadius, thumbRadius, thumbPaint);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;
        for (int i = 0; i < n; i++) {
            int v = isRtl() ? n - 1 - i : i;
            float cx = inset + segW * v + segW / 2f;
            canvas.drawText(options[i], cx, baseline, i == selected ? selectedTextPaint : textPaint);
        }
    }

    private int indexAt(float x) {
        int n = options.length;
        int v = (int) ((x - inset) / segmentWidth());
        v = Math.max(0, Math.min(n - 1, v));
        return isRtl() ? n - 1 - v : v;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || options.length == 0) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressingThumb = indexAt(event.getX()) == selected;
                if (pressingThumb) animateThumbScale(0.95f);
                return true;
            case MotionEvent.ACTION_UP:
                if (pressingThumb) animateThumbScale(1f);
                if (event.getY() >= -getHeight() && event.getY() <= getHeight() * 2) {
                    pendingIndex = indexAt(event.getX());
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (pressingThumb) animateThumbScale(1f);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        boolean handled = super.performClick();
        int target = pendingIndex >= 0 ? pendingIndex : (selected + 1) % Math.max(1, options.length);
        pendingIndex = -1;
        selectFromUser(target);
        return handled;
    }

    private void animateThumbScale(float to) {
        if (scaleAnimator != null) scaleAnimator.cancel();
        scaleAnimator = ValueAnimator.ofFloat(thumbScale, to);
        scaleAnimator.setDuration(to < 1f ? 160 : Motion.SPRING_DURATION);
        scaleAnimator.setInterpolator(to < 1f ? Motion.EASE_OUT : Motion.SPRING_BOUNCY);
        scaleAnimator.addUpdateListener(a -> {
            thumbScale = (float) a.getAnimatedValue();
            invalidate();
        });
        scaleAnimator.start();
    }

    private void updateDescription() {
        if (options.length == 0) return;
        String label = descriptions != null ? descriptions[selected] : options[selected];
        setContentDescription(label);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.Button.class.getName());
    }
}
