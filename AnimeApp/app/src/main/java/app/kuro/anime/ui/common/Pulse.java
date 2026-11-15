package app.kuro.anime.ui.common;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;

/** Gentle opacity pulse used by skeleton placeholders (respects "remove animations"). */
public final class Pulse {
    private Pulse() {}

    public static void start(View v) {
        stop(v);
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        ObjectAnimator a = ObjectAnimator.ofFloat(v, View.ALPHA, 1f, 0.45f);
        a.setDuration(850);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.start();
        v.setTag(app.kuro.anime.R.id.tag_pulse, a);
    }

    public static void stop(View v) {
        Object t = v.getTag(app.kuro.anime.R.id.tag_pulse);
        if (t instanceof ObjectAnimator) ((ObjectAnimator) t).cancel();
        v.setTag(app.kuro.anime.R.id.tag_pulse, null);
        v.setAlpha(1f);
    }
}
