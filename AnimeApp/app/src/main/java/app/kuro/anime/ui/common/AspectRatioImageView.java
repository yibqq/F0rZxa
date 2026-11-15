package app.kuro.anime.ui.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

import app.kuro.anime.R;

/** ImageView whose height follows its width (posters 2:3, episode stills 16:9) — never distorted. */
public class AspectRatioImageView extends AppCompatImageView {
    private float ratio = 1.5f;

    public AspectRatioImageView(Context context) { this(context, null); }

    public AspectRatioImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AspectRatioImageView);
            ratio = a.getFloat(R.styleable.AspectRatioImageView_heightRatio, 1.5f);
            a.recycle();
        }
        setScaleType(ScaleType.CENTER_CROP);
        setClipToOutline(true);
    }

    public void setHeightRatio(float r) {
        if (r != ratio) {
            ratio = r;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, Math.round(w * ratio));
    }
}
