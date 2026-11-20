package com.gpacalculator.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.gpacalculator.R;
import com.gpacalculator.ui.util.Dimens;
import com.gpacalculator.ui.util.Haptics;

/** Swipe toward the leading edge to reveal a red "Delete" action, as in iOS lists. */
final class SwipeToDeleteCallback extends ItemTouchHelper.Callback {
    private static final float THRESHOLD = 0.4f;

    interface OnSwipedListener {
        void onSwipedToDelete(int adapterPosition);
    }

    private final OnSwipedListener listener;
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius;
    private final float labelMargin;
    private final String text;
    private RecyclerView.ViewHolder armedHolder;

    SwipeToDeleteCallback(Context context, OnSwipedListener listener) {
        this.listener = listener;
        background.setColor(context.getColor(R.color.destructive));
        label.setColor(context.getColor(R.color.on_tint));
        label.setTextSize(Dimens.sp(context, 15));
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        radius = context.getResources().getDimension(R.dimen.card_radius);
        labelMargin = Dimens.dpF(context, 22);
        text = context.getString(R.string.delete);
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder holder) {
        if (holder.getItemViewType() != HistoryAdapter.TYPE_RECORD) return 0;
        // START = toward the leading edge (left in LTR), mirroring automatically in RTL.
        return makeMovementFlags(0, ItemTouchHelper.START);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder a,
                          @NonNull RecyclerView.ViewHolder b) {
        return false;
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder holder) {
        return THRESHOLD;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
        armedHolder = null;
        listener.onSwipedToDelete(holder.getBindingAdapterPosition());
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder holder,
                            float dX, float dY, int actionState, boolean isActive) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f
                && holder instanceof HistoryAdapter.RecordHolder) {
            View item = holder.itemView;
            View card = ((HistoryAdapter.RecordHolder) holder).card;
            float left = item.getLeft() + card.getLeft();
            float top = item.getTop() + card.getTop() + item.getTranslationY();
            rect.set(left, top, left + card.getWidth(), top + card.getHeight());
            c.drawRoundRect(rect, radius, radius, background);

            float textWidth = label.measureText(text);
            Paint.FontMetrics fm = label.getFontMetrics();
            float baseline = rect.centerY() - (fm.ascent + fm.descent) / 2f;
            float x = dX < 0 ? rect.right - labelMargin - textWidth : rect.left + labelMargin;
            float progress = Math.min(1f, Math.abs(dX) / (textWidth + labelMargin * 2));
            label.setAlpha((int) (255 * progress));
            c.drawText(text, x, baseline, label);

            boolean armed = Math.abs(dX) >= card.getWidth() * THRESHOLD;
            if (isActive && armed && armedHolder != holder) {
                armedHolder = holder;
                Haptics.threshold(rv);
            } else if (!armed && armedHolder == holder) {
                armedHolder = null;
            }
        }
        super.onChildDraw(c, rv, holder, dX, dY, actionState, isActive);
    }
}
