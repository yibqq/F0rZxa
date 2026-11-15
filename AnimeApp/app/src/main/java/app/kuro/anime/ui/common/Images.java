package app.kuro.anime.ui.common;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

/**
 * Image loading via Glide (memory + disk cache, downsampled to the view size).
 * Rounded corners come from the view's outline, so no bitmap transformations are needed.
 */
public final class Images {
    private static final int SURFACE = 0xFF171717;

    private Images() {}

    public static void poster(ImageView view, @Nullable String url, @Nullable String colorHex) {
        ColorDrawable placeholder = new ColorDrawable(placeholderColor(colorHex));
        Glide.with(view)
                .load(url)
                .placeholder(placeholder)
                .error(placeholder)
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(160))
                .into(view);
    }

    public static void backdrop(ImageView view, @Nullable String url, @Nullable String colorHex) {
        ColorDrawable placeholder = new ColorDrawable(placeholderColor(colorHex));
        Glide.with(view)
                .load(url)
                .placeholder(placeholder)
                .error(placeholder)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(220))
                .into(view);
    }

    public static void avatar(ImageView view, @Nullable String url) {
        ColorDrawable placeholder = new ColorDrawable(0xFF242424);
        Glide.with(view).load(url).placeholder(placeholder).error(placeholder).circleCrop()
                .transition(DrawableTransitionOptions.withCrossFade(160)).into(view);
    }

    public static void clear(ImageView view) {
        Glide.with(view).clear(view);
        view.setImageDrawable(null);
    }

    /** A deep, desaturated version of the artwork colour so placeholders hint at the image. */
    public static int placeholderColor(@Nullable String hex) {
        if (hex == null) return SURFACE;
        try {
            int c = Color.parseColor(hex);
            return blend(c, SURFACE, 0.22f);
        } catch (IllegalArgumentException e) {
            return SURFACE;
        }
    }

    /** Readable accent derived from artwork: guaranteed light enough on black. */
    public static int accentColor(@Nullable String hex) {
        if (hex == null) return Color.WHITE;
        try {
            float[] hsv = new float[3];
            Color.colorToHSV(Color.parseColor(hex), hsv);
            hsv[1] = Math.min(hsv[1], 0.55f);
            hsv[2] = Math.max(hsv[2], 0.85f);
            return Color.HSVToColor(hsv);
        } catch (IllegalArgumentException e) {
            return Color.WHITE;
        }
    }

    public static int blend(int a, int b, float ratioA) {
        float r = 1f - ratioA;
        return Color.rgb(
                (int) (Color.red(a) * ratioA + Color.red(b) * r),
                (int) (Color.green(a) * ratioA + Color.green(b) * r),
                (int) (Color.blue(a) * ratioA + Color.blue(b) * r));
    }
}
