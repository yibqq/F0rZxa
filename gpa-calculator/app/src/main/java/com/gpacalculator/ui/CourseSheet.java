package com.gpacalculator.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;

import com.gpacalculator.R;
import com.gpacalculator.logic.GpaCalculator;
import com.gpacalculator.model.Course;
import com.gpacalculator.model.Grade;
import com.gpacalculator.model.GradeScale;
import com.gpacalculator.ui.util.Dimens;
import com.gpacalculator.ui.util.Formatters;
import com.gpacalculator.ui.util.Haptics;
import com.gpacalculator.ui.util.Keyboard;
import com.gpacalculator.ui.util.Motion;
import com.gpacalculator.ui.util.PressEffect;
import com.gpacalculator.ui.util.SystemInsets;

import java.util.EnumMap;
import java.util.Map;

/** Bottom sheet for adding or editing a course: optional name, credit stepper and a grade keypad. */
final class CourseSheet extends OverlayDialog {

    interface Listener {
        void onSave(String name, int credits, Grade grade);

        void onDelete();
    }

    private final GradeScale scale;
    private final Course existing;
    private final Listener listener;
    private final Map<Grade, View> keys = new EnumMap<>(Grade.class);

    private int credits;
    private Grade grade;
    private boolean committed;

    private View sheet;
    private EditText nameField;
    private TextView creditsLabel;
    private TextView minus;
    private TextView plus;
    private TextView done;
    private GridLayout grid;

    static void show(Activity activity, GradeScale scale, Course existing, int defaultCredits, Listener listener) {
        new CourseSheet(activity, scale, existing, defaultCredits, listener).show();
    }

    private CourseSheet(Activity activity, GradeScale scale, Course existing, int defaultCredits, Listener listener) {
        super(activity);
        this.scale = scale;
        this.existing = existing;
        this.listener = listener;
        this.credits = existing != null ? existing.credits : clampCredits(defaultCredits);
        this.grade = existing != null ? existing.grade : null;
    }

    private static int clampCredits(int value) {
        return Math.max(Course.MIN_CREDITS, Math.min(Course.MAX_CREDITS, value));
    }

    @Override
    protected int layoutRes() {
        return R.layout.sheet_course;
    }

    @Override
    protected void onViewsCreated(View root) {
        sheet = root.findViewById(R.id.sheet);
        nameField = root.findViewById(R.id.sheet_name);
        creditsLabel = root.findViewById(R.id.sheet_credits);
        minus = root.findViewById(R.id.stepper_minus);
        plus = root.findViewById(R.id.stepper_plus);
        done = root.findViewById(R.id.sheet_done);
        grid = root.findViewById(R.id.sheet_grades);

        boolean editing = existing != null;
        ((TextView) root.findViewById(R.id.sheet_title))
                .setText(editing ? R.string.sheet_edit_title : R.string.sheet_add_title);
        done.setText(editing ? R.string.sheet_done : R.string.sheet_add);
        ((TextView) root.findViewById(R.id.sheet_grade_header))
                .setText(activity.getString(R.string.section_grade,
                        activity.getString(scale == GradeScale.FIVE ? R.string.scale_5 : R.string.scale_4)));

        if (editing) {
            nameField.setText(existing.name);
            View deleteCard = root.findViewById(R.id.sheet_delete_card);
            deleteCard.setVisibility(View.VISIBLE);
            root.findViewById(R.id.sheet_delete).setOnClickListener(v -> {
                if (committed) return;
                committed = true;
                Haptics.warning(v);
                listener.onDelete();
                dismissAnimated();
            });
        }
        nameField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                Keyboard.hide(v);
                v.clearFocus();
                return true;
            }
            return false;
        });

        root.findViewById(R.id.sheet_cancel).setOnClickListener(v -> dismissAnimated());
        done.setOnClickListener(v -> commit());

        minus.setOnClickListener(v -> stepCredits(-1));
        plus.setOnClickListener(v -> stepCredits(1));
        PressEffect.attach(minus, 0.9f);
        PressEffect.attach(plus, 0.9f);

        buildKeypad();
        setupDragToDismiss(root.findViewById(R.id.sheet_header));
        render();
    }

    private void buildKeypad() {
        LayoutInflater inflater = LayoutInflater.from(activity);
        int gap = Dimens.dp(activity, 4);
        for (Grade g : Grade.values()) {
            View key = inflater.inflate(R.layout.item_grade_key, grid, false);
            String points = GpaCalculator.format(g.points(scale));
            ((TextView) key.findViewById(R.id.key_letter)).setText(g.code());
            ((TextView) key.findViewById(R.id.key_points)).setText(points);
            key.setContentDescription(activity.getString(R.string.cd_grade_key, g.code(), points));

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = Dimens.dp(activity, 64);
            lp.setMargins(gap, gap, gap, gap);

            key.setOnClickListener(v -> selectGrade(g, v));
            PressEffect.attach(key, 0.94f);
            grid.addView(key, lp);
            keys.put(g, key);
        }
    }

    private void selectGrade(Grade g, View key) {
        if (grade != g) {
            Haptics.selection(key);
        }
        grade = g;
        render();
    }

    private void stepCredits(int delta) {
        int next = clampCredits(credits + delta);
        if (next == credits) {
            PressEffect.shake(creditsLabel);
            return;
        }
        credits = next;
        Haptics.selection(delta < 0 ? minus : plus);
        render();
    }

    private void render() {
        creditsLabel.setText(Formatters.creditHours(activity, credits));
        minus.setEnabled(credits > Course.MIN_CREDITS);
        plus.setEnabled(credits < Course.MAX_CREDITS);
        for (Map.Entry<Grade, View> e : keys.entrySet()) {
            e.getValue().setSelected(e.getKey() == grade);
        }
        done.setAlpha(grade != null ? 1f : 0.4f);
    }

    private void commit() {
        if (committed) return;
        if (grade == null) {
            Haptics.warning(done);
            PressEffect.shake(grid);
            grid.announceForAccessibility(activity.getString(R.string.choose_grade));
            return;
        }
        committed = true;
        Haptics.success(done);
        listener.onSave(nameField.getText().toString(), credits, grade);
        dismissAnimated();
    }

    @Override
    protected void onInsets(SystemInsets insets) {
        int bottom = Math.max(insets.ime, insets.bottom) + Dimens.dp(activity, 8);
        sheet.setPadding(insets.left, 0, insets.right, bottom);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) sheet.getLayoutParams();
        lp.topMargin = insets.top + Dimens.dp(activity, 12);
        sheet.setLayoutParams(lp);
    }

    @Override
    protected void animateIn() {
        sheet.setTranslationY(sheet.getHeight());
        sheet.setVisibility(View.VISIBLE);
        sheet.animate().translationY(0f).setDuration(Motion.SPRING_DURATION)
                .setInterpolator(Motion.SPRING).start();
        scrim.animate().alpha(1f).setDuration(Motion.NORMAL).start();
    }

    @Override
    protected void animateOut(Runnable onEnd) {
        float distance = sheet.getHeight() + Dimens.dp(activity, 16);
        float remaining = Math.max(0f, 1f - sheet.getTranslationY() / Math.max(1f, distance));
        long duration = Math.max(140, (long) (Motion.NORMAL * remaining));
        sheet.animate().translationY(distance).setDuration(duration)
                .setInterpolator(Motion.EASE_IN).withEndAction(onEnd).start();
        scrim.animate().alpha(0f).setDuration(duration).start();
    }

    /** Lets the sheet be pulled down by its handle/header, rubber-banding upwards like UIKit. */
    @SuppressLint("ClickableViewAccessibility")
    private void setupDragToDismiss(View dragArea) {
        final float flingVelocity = Dimens.dpF(activity, 900);
        dragArea.setOnTouchListener(new View.OnTouchListener() {
            private float startY;
            private VelocityTracker tracker;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (isClosing()) return false;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = e.getRawY() - sheet.getTranslationY();
                        sheet.animate().cancel();
                        tracker = VelocityTracker.obtain();
                        track(e);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        track(e);
                        float dy = e.getRawY() - startY;
                        float y = dy >= 0 ? dy : -(float) Math.sqrt(-dy) * 2f;
                        sheet.setTranslationY(y);
                        float h = Math.max(1f, sheet.getHeight());
                        scrim.setAlpha(Math.max(0f, Math.min(1f, 1f - y / h)));
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        track(e);
                        float vy = 0f;
                        if (tracker != null) {
                            tracker.computeCurrentVelocity(1000);
                            vy = tracker.getYVelocity();
                            tracker.recycle();
                            tracker = null;
                        }
                        float y = sheet.getTranslationY();
                        if (y > sheet.getHeight() * 0.3f || vy > flingVelocity) {
                            dismissAnimated();
                        } else {
                            sheet.animate().translationY(0f).setDuration(Motion.SPRING_DURATION)
                                    .setInterpolator(Motion.SPRING).start();
                            scrim.animate().alpha(1f).setDuration(Motion.FAST).start();
                        }
                        return true;
                    }
                    default:
                        return false;
                }
            }

            private void track(MotionEvent e) {
                if (tracker == null) return;
                // The sheet moves under the finger, so track screen coordinates.
                MotionEvent copy = MotionEvent.obtain(e);
                copy.setLocation(e.getRawX(), e.getRawY());
                tracker.addMovement(copy);
                copy.recycle();
            }
        });
    }
}
