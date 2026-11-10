package com.mizan.gpa.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.mizan.gpa.R;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Fonts;
import com.mizan.gpa.util.Themes;
import com.mizan.gpa.util.Ui;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Weekly timetable: one column per day, hours down the side, lectures as tinted cards with a colored
 * edge. Overlapping lectures share their column. Mirrors itself in RTL locales and marks the current time.
 */
public class TimetableView extends View {
    public static final class Block {
        public final int day, start, end, color;
        public final String title, subtitle;
        /** Shorter title (the course code) used when a word of the title does not fit the column. */
        public String shortTitle;
        public final Object tag;
        int lane, lanes = 1;
        final RectF rect = new RectF();

        public Block(int day, int start, int end, int color, String title, String subtitle, Object tag) {
            this.day = day;
            this.start = start;
            this.end = end;
            this.color = color;
            this.title = title;
            this.subtitle = subtitle;
            this.tag = tag;
        }
    }

    public interface OnBlockClickListener {
        void onBlockClick(Block block);
    }

    private final List<Block> blocks = new ArrayList<>();
    private int[] days = new int[0];
    private int today;
    private int fromHour = 8, toHour = 16;
    private OnBlockClickListener listener;

    private final float axisW, headerH, hourH, gap, radius, edge;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint now = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hourText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint dayText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titleText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final GestureDetector gestures;
    private final boolean night;
    private final int onSurface, onSurfaceVariant, primary, onPrimary, surfaceHigh;

    public TimetableView(Context c) {
        this(c, null);
    }

    public TimetableView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        axisW = Ui.dp(c, 46);
        headerH = Ui.dp(c, 46);
        hourH = Ui.dp(c, 72);
        gap = Ui.dp(c, 3);
        radius = Ui.dp(c, 12);
        edge = Ui.dp(c, 4);
        night = Themes.isNight(c);
        onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        onPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary);
        surfaceHigh = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow);
        grid.setColor(Ui.withAlpha(onSurfaceVariant, 0.22f));
        grid.setStrokeWidth(Ui.dp(c, 1));
        grid.setPathEffect(new DashPathEffect(new float[]{Ui.dp(c, 4), Ui.dp(c, 4)}, 0));
        now.setColor(ContextCompat.getColor(c, R.color.status_danger));
        now.setStrokeWidth(Ui.dp(c, 2));
        hourText.setTextSize(Ui.dp(c, 11));
        hourText.setColor(onSurfaceVariant);
        dayText.setTextSize(Ui.dp(c, 13));
        titleText.setTextSize(Ui.dp(c, 11.5f));
        subText.setTextSize(Ui.dp(c, 10f));
        Fonts.apply(hourText, c, 400);
        Fonts.apply(dayText, c, 700);
        Fonts.apply(titleText, c, 700);
        Fonts.apply(subText, c, 400);
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                for (Block b : blocks) {
                    if (b.rect.contains(e.getX(), e.getY())) {
                        playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        if (listener != null) listener.onBlockClick(b);
                        return true;
                    }
                }
                return false;
            }
        });
    }

    public void setOnBlockClickListener(OnBlockClickListener l) {
        listener = l;
    }

    /**
     * @param orderedDays all seven days in display order (Calendar constants)
     * @param todayDay    today's Calendar day
     */
    public void setData(List<Block> data, int[] orderedDays, int todayDay) {
        blocks.clear();
        blocks.addAll(data);
        today = todayDay;
        // Show the span of days that have lectures, at least five days wide.
        int first = -1, last = -1;
        for (int i = 0; i < orderedDays.length; i++) {
            for (Block b : data) {
                if (b.day == orderedDays[i]) {
                    if (first < 0) first = i;
                    last = i;
                }
            }
        }
        if (first < 0) {
            first = 0;
            last = 4;
        }
        while (last - first < 4) {
            if (last < orderedDays.length - 1) last++;
            else first--;
        }
        days = new int[last - first + 1];
        System.arraycopy(orderedDays, first, days, 0, days.length);

        int min = 24 * 60, max = 0;
        for (Block b : data) {
            min = Math.min(min, b.start);
            max = Math.max(max, b.end);
        }
        if (data.isEmpty()) {
            min = 8 * 60;
            max = 14 * 60;
        }
        fromHour = Math.max(0, min / 60);
        toHour = Math.min(24, (max + 59) / 60);
        if (toHour - fromHour < 4) toHour = Math.min(24, fromHour + 4);
        assignLanes();
        requestLayout();
        invalidate();
    }

    /** Splits a day column between lectures that overlap in time. */
    private void assignLanes() {
        for (int d : days) {
            List<Block> dayBlocks = new ArrayList<>();
            for (Block b : blocks) if (b.day == d) dayBlocks.add(b);
            dayBlocks.sort((x, y) -> Integer.compare(x.start, y.start));
            List<Block> cluster = new ArrayList<>();
            int clusterEnd = -1;
            for (Block b : dayBlocks) {
                if (b.start >= clusterEnd && !cluster.isEmpty()) {
                    finishCluster(cluster);
                    cluster.clear();
                }
                cluster.add(b);
                clusterEnd = Math.max(clusterEnd, b.end);
            }
            finishCluster(cluster);
        }
    }

    private static void finishCluster(List<Block> cluster) {
        List<Integer> laneEnds = new ArrayList<>();
        for (Block b : cluster) {
            int lane = -1;
            for (int i = 0; i < laneEnds.size(); i++) {
                if (laneEnds.get(i) <= b.start) {
                    lane = i;
                    break;
                }
            }
            if (lane < 0) {
                lane = laneEnds.size();
                laneEnds.add(b.end);
            } else {
                laneEnds.set(lane, b.end);
            }
            b.lane = lane;
        }
        for (Block b : cluster) b.lanes = Math.max(1, laneEnds.size());
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = getDefaultSize(getSuggestedMinimumWidth(), widthSpec);
        int h = (int) (headerH + (toHour - fromHour) * hourH + Ui.dp(getContext(), 8));
        setMeasuredDimension(w, h);
    }

    private float columnX(int index, float colW, boolean rtl) {
        return rtl ? getWidth() - axisW - (index + 1) * colW : axisW + index * colW;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (days.length == 0) return;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float colW = (getWidth() - axisW) / days.length;
        float top = headerH;

        // Day columns and headers.
        for (int i = 0; i < days.length; i++) {
            float x = columnX(i, colW, rtl);
            boolean isToday = days[i] == today;
            tmp.set(x + gap, top, x + colW - gap, getHeight() - Ui.dp(getContext(), 4));
            fill.setColor(isToday ? Ui.withAlpha(primary, night ? 0.12f : 0.07f) : Ui.withAlpha(surfaceHigh, 0.9f));
            canvas.drawRoundRect(tmp, radius, radius, fill);
            String name = Fmt.dayName(getContext(), days[i], true);
            float cx = x + colW / 2f;
            float cy = headerH / 2f;
            if (isToday) {
                float w = Math.min(colW - gap * 2, dayText.measureText(name) + Ui.dp(getContext(), 20));
                tmp.set(cx - w / 2f, cy - Ui.dp(getContext(), 14), cx + w / 2f, cy + Ui.dp(getContext(), 14));
                fill.setColor(primary);
                canvas.drawRoundRect(tmp, tmp.height() / 2f, tmp.height() / 2f, fill);
                dayText.setColor(onPrimary);
            } else {
                dayText.setColor(onSurface);
            }
            dayText.setTextAlign(Paint.Align.CENTER);
            CharSequence fitted = TextUtils.ellipsize(name, dayText, colW - gap * 2, TextUtils.TruncateAt.END);
            canvas.drawText(fitted.toString(), cx, cy - (dayText.descent() + dayText.ascent()) / 2f, dayText);
        }

        // Hour lines and labels.
        hourText.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
        for (int h = fromHour; h <= toHour; h++) {
            float y = top + (h - fromHour) * hourH;
            float lx = rtl ? getWidth() - axisW : axisW;
            float rx = rtl ? 0 : getWidth();
            canvas.drawLine(Math.min(lx, rx), y, Math.max(lx, rx), y, grid);
            if (h < toHour || h == fromHour) {
                String label = Fmt.hourLabel(getContext(), h);
                float tx = rtl ? getWidth() - Ui.dp(getContext(), 4) : Ui.dp(getContext(), 4);
                canvas.drawText(label, tx, y + hourText.getTextSize() + Ui.dp(getContext(), 2), hourText);
            }
        }

        // Lectures.
        for (Block b : blocks) {
            int index = -1;
            for (int i = 0; i < days.length; i++) if (days[i] == b.day) index = i;
            if (index < 0) continue;
            float x = columnX(index, colW, rtl);
            float laneW = (colW - gap * 2) / b.lanes;
            int lanePos = rtl ? b.lanes - 1 - b.lane : b.lane;
            float left = x + gap + lanePos * laneW + gap / 2f;
            float right = left + laneW - gap;
            float y1 = top + (b.start - fromHour * 60) / 60f * hourH + gap / 2f;
            float y2 = top + (b.end - fromHour * 60) / 60f * hourH - gap / 2f;
            b.rect.set(left, y1, right, y2);
            fill.setColor(Ui.blend(surfaceHigh, b.color, night ? 0.32f : 0.20f));
            canvas.drawRoundRect(b.rect, radius, radius, fill);
            // Colored edge on the reading-start side.
            fill.setColor(b.color);
            canvas.save();
            canvas.clipRect(rtl ? right - edge : left, y1, rtl ? right : left + edge, y2);
            canvas.drawRoundRect(b.rect, radius, radius, fill);
            canvas.restore();

            float pad = Ui.dp(getContext(), 6);
            float textW = right - left - edge - pad * 2;
            float textLeft = rtl ? left + pad : left + edge + pad;
            float avail = y2 - y1 - pad * 2;
            if (textW < Ui.dp(getContext(), 12) || avail < Ui.dp(getContext(), 10)) continue;
            titleText.setColor(night ? onSurface : Ui.blend(b.color, 0xFF000000, 0.55f));
            subText.setColor(onSurfaceVariant);
            String label = b.title;
            if (b.shortTitle != null && !b.shortTitle.isEmpty()) {
                for (String word : b.title.split("\\s+")) {
                    if (titleText.measureText(word) > textW) {
                        label = b.shortTitle;
                        break;
                    }
                }
            }
            StaticLayout title = layout(label, titleText, textW, avail, rtl);
            float used = title.getHeight();
            canvas.save();
            canvas.translate(textLeft, y1 + pad);
            title.draw(canvas);
            canvas.restore();
            float remaining = avail - used - Ui.dp(getContext(), 2);
            if (b.subtitle != null && remaining >= subText.getTextSize()) {
                StaticLayout sub = layout(b.subtitle, subText, textW, remaining, rtl);
                canvas.save();
                canvas.translate(textLeft, y1 + pad + used + Ui.dp(getContext(), 2));
                sub.draw(canvas);
                canvas.restore();
            }
        }

        // Current time on today's column.
        LocalTime t = LocalTime.now();
        int minutes = t.getHour() * 60 + t.getMinute();
        for (int i = 0; i < days.length; i++) {
            if (days[i] != today || minutes < fromHour * 60 || minutes > toHour * 60) continue;
            float x = columnX(i, colW, rtl);
            float y = top + (minutes - fromHour * 60) / 60f * hourH;
            canvas.drawLine(x + gap, y, x + colW - gap, y, now);
            fill.setColor(now.getColor());
            canvas.drawCircle(rtl ? x + colW - gap : x + gap, y, Ui.dp(getContext(), 4), fill);
        }
    }

    private static StaticLayout layout(String text, TextPaint p, float width, float maxHeight, boolean rtl) {
        int lines = Math.max(1, (int) (maxHeight / (p.getFontSpacing())));
        return StaticLayout.Builder.obtain(text, 0, text.length(), p, (int) Math.max(1, width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                // One direction for every line so titles, times and rooms all align to the same edge.
                .setTextDirection(rtl ? android.text.TextDirectionHeuristics.RTL : android.text.TextDirectionHeuristics.LTR)
                .setMaxLines(lines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build();
    }

    @Override
    @SuppressWarnings("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent e) {
        return gestures.onTouchEvent(e) || super.onTouchEvent(e);
    }
}
