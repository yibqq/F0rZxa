package com.mizan.gpa.ui;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.constraintlayout.motion.widget.MotionLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.mizan.gpa.R;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.util.Themes;
import com.mizan.gpa.util.Ui;

/**
 * First-launch welcome: four pages introducing the app (GPA, organization and attendance, reminders),
 * then the choice between the Standard and Lite interfaces. Completion and the mode are saved, so it is
 * shown once; the mode can be changed later in Settings. Honors "remove animations", supports back
 * navigation (to the previous page) and keeps its page across recreation.
 */
public class OnboardingActivity extends BaseActivity {
    private static final String STATE_PAGE = "page", STATE_MODE = "mode";
    private static final int[] STATES = {R.id.ob_state1, R.id.ob_state2, R.id.ob_state3, R.id.ob_state4, R.id.ob_state5};
    private static final int[] PAGES = {R.id.ob_page1, R.id.ob_page2, R.id.ob_page3, R.id.ob_page4, R.id.ob_page5};
    private static final int[] TITLES = {R.string.ob_welcome_title, R.string.ob_gpa_title, R.string.ob_organize_title,
            R.string.ob_reminders_title, R.string.ob_mode_title};

    private MotionLayout motion;
    private int page;
    private String mode = Settings.MODE_STANDARD;
    private boolean reduceMotion;
    private MaterialCardView standardCard, liteCard;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_onboarding);
        motion = findViewById(R.id.ob_motion);
        // "Remove animations" in the system settings turns all transitions into instant page changes.
        reduceMotion = !ValueAnimator.areAnimatorsEnabled();
        if (state != null) {
            page = Math.max(0, Math.min(STATES.length - 1, state.getInt(STATE_PAGE)));
            mode = Settings.MODE_LITE.equals(state.getString(STATE_MODE)) ? Settings.MODE_LITE : Settings.MODE_STANDARD;
        }

        Themes.brandTile(findViewById(R.id.ob_page1_tile), findViewById(R.id.ob_page1_icon));
        int tone = Ui.attr(this, com.google.android.material.R.attr.colorPrimaryContainer);
        int ink = Ui.attr(this, com.google.android.material.R.attr.colorOnPrimaryContainer);
        for (int id : new int[]{R.id.ob_page2, R.id.ob_page3, R.id.ob_page4}) {
            int tileId = id == R.id.ob_page2 ? R.id.ob_page2_tile : id == R.id.ob_page3 ? R.id.ob_page3_tile : R.id.ob_page4_tile;
            int iconId = id == R.id.ob_page2 ? R.id.ob_page2_icon : id == R.id.ob_page3 ? R.id.ob_page3_icon : R.id.ob_page4_icon;
            findViewById(tileId).setBackground(new com.mizan.gpa.util.SquircleDrawable(tone, Ui.blend(tone,
                    Ui.attr(this, com.google.android.material.R.attr.colorTertiaryContainer), 0.6f)));
            ((ImageView) findViewById(iconId)).setImageTintList(Ui.csl(ink));
        }

        standardCard = findViewById(R.id.ob_mode_standard);
        liteCard = findViewById(R.id.ob_mode_lite);
        modeCard(standardCard, R.drawable.ic_dashboard, R.string.mode_standard, R.string.mode_standard_desc, Settings.MODE_STANDARD);
        modeCard(liteCard, R.drawable.ic_tune, R.string.mode_lite, R.string.mode_lite_desc, Settings.MODE_LITE);
        refreshModeCards();

        buildDots();
        findViewById(R.id.ob_next).setOnClickListener(v -> {
            if (page == STATES.length - 1) finishOnboarding();
            else go(page + 1);
        });
        findViewById(R.id.ob_back).setOnClickListener(v -> go(page - 1));
        findViewById(R.id.ob_skip).setOnClickListener(v -> go(STATES.length - 1));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (page > 0) {
                    go(page - 1);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
        motion.setTransitionListener(new MotionLayout.TransitionListener() {
            @Override
            public void onTransitionStarted(MotionLayout m, int start, int end) {}

            @Override
            public void onTransitionChange(MotionLayout m, int start, int end, float progress) {}

            @Override
            public void onTransitionCompleted(MotionLayout m, int current) {
                for (int i = 0; i < STATES.length; i++) {
                    if (STATES[i] == current && i != page) {
                        page = i;
                        onPageShown(true);
                    }
                }
            }

            @Override
            public void onTransitionTrigger(MotionLayout m, int trigger, boolean positive, float progress) {}
        });
        if (reduceMotion) {
            // No swiping animations either: pages change only with the buttons.
            for (int i = 0; i + 1 < STATES.length; i++) {
                androidx.constraintlayout.motion.widget.MotionScene.Transition t = motion.getTransition(transitionId(i));
                if (t != null) t.setEnabled(false);
            }
        }
        motion.post(() -> {
            jumpTo(page);
            onPageShown(false);
        });
    }

    private static int transitionId(int fromPage) {
        switch (fromPage) {
            case 0: return R.id.ob_t12;
            case 1: return R.id.ob_t23;
            case 2: return R.id.ob_t34;
            default: return R.id.ob_t45;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_PAGE, page);
        out.putString(STATE_MODE, mode);
    }

    private void go(int target) {
        if (target < 0 || target >= STATES.length || target == page) return;
        if (reduceMotion || Math.abs(target - page) > 1) {
            jumpTo(target);
            onPageShown(true);
        } else if (target > page) {
            // Name the transition explicitly: MotionLayout's own search could start from another state.
            motion.setTransition(transitionId(page));
            motion.setProgress(0f);
            motion.transitionToEnd();
        } else {
            motion.setTransition(transitionId(target));
            motion.setProgress(1f);
            motion.transitionToStart();
        }
    }

    /** Shows a page instantly by resting at the start or end of one of the scene's transitions. */
    private void jumpTo(int target) {
        if (target == 0) {
            motion.setTransition(transitionId(0));
            motion.setProgress(0f);
        } else {
            motion.setTransition(transitionId(target - 1));
            motion.setProgress(1f);
        }
        page = target;
    }

    /** Updates buttons, dots and accessibility after the shown page changed. */
    private void onPageShown(boolean announce) {
        MaterialButton next = findViewById(R.id.ob_next);
        boolean last = page == STATES.length - 1;
        next.setText(last ? R.string.ob_start : R.string.ob_next);
        next.setIcon(last ? null : getDrawable(R.drawable.ic_chevron));
        // Only the shown page is visible to screen readers.
        for (int i = 0; i < PAGES.length; i++) {
            findViewById(PAGES[i]).setImportantForAccessibility(i == page
                    ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        LinearLayout dots = findViewById(R.id.ob_dots);
        for (int i = 0; i < dots.getChildCount(); i++) paintDot(dots.getChildAt(i), i == page);
        if (announce) motion.announceForAccessibility(getString(R.string.ob_page_announce, page + 1, STATES.length, getString(TITLES[page])));
    }

    private void buildDots() {
        LinearLayout dots = findViewById(R.id.ob_dots);
        dots.removeAllViews();
        for (int i = 0; i < STATES.length; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            dots.addView(d, lp);
        }
    }

    private void paintDot(View d, boolean active) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) d.getLayoutParams();
        lp.width = Ui.dp(this, active ? 22 : 8);
        d.setLayoutParams(lp);
        Ui.roundedFill(d, active ? Ui.attr(this, androidx.appcompat.R.attr.colorPrimary)
                : Ui.attr(this, com.google.android.material.R.attr.colorOutlineVariant), 8);
    }

    private void modeCard(MaterialCardView card, int icon, int title, int text, String value) {
        ImageView iv = card.findViewById(R.id.m_icon);
        iv.setImageResource(icon);
        iv.setImageTintList(Ui.csl(Ui.attr(this, com.google.android.material.R.attr.colorOnSecondaryContainer)));
        iv.setBackground(new com.mizan.gpa.util.SquircleDrawable(Ui.attr(this, com.google.android.material.R.attr.colorSecondaryContainer),
                Ui.attr(this, com.google.android.material.R.attr.colorSecondaryContainer)));
        ((TextView) card.findViewById(R.id.m_title)).setText(title);
        ((TextView) card.findViewById(R.id.m_text)).setText(text);
        card.setContentDescription(getString(title) + ". " + getString(text));
        card.setOnClickListener(v -> {
            mode = value;
            refreshModeCards();
        });
    }

    private void refreshModeCards() {
        boolean lite = Settings.MODE_LITE.equals(mode);
        styleCard(standardCard, !lite);
        styleCard(liteCard, lite);
    }

    private void styleCard(MaterialCardView c, boolean selected) {
        c.setChecked(selected);
        c.setStrokeColor(selected ? Ui.attr(this, androidx.appcompat.R.attr.colorPrimary)
                : Ui.attr(this, com.google.android.material.R.attr.colorOutlineVariant));
        c.setCardBackgroundColor(selected ? Ui.withAlpha(Ui.attr(this, androidx.appcompat.R.attr.colorPrimary), 0.08f)
                : Ui.attr(this, com.google.android.material.R.attr.colorSurface));
    }

    private void finishOnboarding() {
        Settings st = repo().settings();
        st.onboarded = true;
        st.appMode = mode;
        repo().save();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void render() {}
}
