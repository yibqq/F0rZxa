package com.gpacalculator.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/** Inset-grouped card that clips its rows (and their press highlight) to its rounded background. */
public class GroupCard extends LinearLayout {
    public GroupCard(Context context) {
        this(context, null);
    }

    public GroupCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipToOutline(true);
    }
}
