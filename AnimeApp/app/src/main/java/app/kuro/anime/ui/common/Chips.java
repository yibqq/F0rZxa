package app.kuro.anime.ui.common;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import app.kuro.anime.R;

public final class Chips {
    private Chips() {}

    public static TextView add(LinearLayout parent, CharSequence text, boolean withChevron, Runnable onClick) {
        Context c = parent.getContext();
        TextView chip = new TextView(c, null, 0, R.style.Kuro_Chip);
        chip.setText(text);
        if (withChevron) {
            chip.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_down, 0);
            chip.setCompoundDrawableTintList(ContextCompat.getColorStateList(c, R.color.chip_text));
            chip.setCompoundDrawablePadding(Layouts.dp(c, 4));
            chip.setPaddingRelative(Layouts.dp(c, 14), 0, Layouts.dp(c, 8), 0);
        }
        chip.setOnClickListener(v -> onClick.run());
        Motion.pressable(chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                c.getResources().getDimensionPixelSize(R.dimen.chip_height));
        lp.setMarginEnd(Layouts.dp(c, 8));
        parent.addView(chip, lp);
        return chip;
    }
}
