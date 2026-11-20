package com.gpacalculator.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.gpacalculator.R;

/** LinearLayout that stops growing past a maximum content width, keeping tablet layouts readable. */
public class MaxWidthLinearLayout extends LinearLayout {
    private int maxContentWidth;

    public MaxWidthLinearLayout(Context context) {
        this(context, null);
    }

    public MaxWidthLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MaxWidthLinearLayout);
        maxContentWidth = a.getDimensionPixelSize(R.styleable.MaxWidthLinearLayout_contentMaxWidth, 0);
        a.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxContentWidth > 0) {
            int limit = maxContentWidth + getPaddingLeft() + getPaddingRight();
            int mode = MeasureSpec.getMode(widthMeasureSpec);
            int size = MeasureSpec.getSize(widthMeasureSpec);
            if (mode == MeasureSpec.UNSPECIFIED || size > limit) {
                int newMode = mode == MeasureSpec.UNSPECIFIED ? MeasureSpec.AT_MOST : mode;
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(limit, newMode);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
