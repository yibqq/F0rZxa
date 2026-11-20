package com.gpacalculator.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Rect;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gpacalculator.R;
import com.gpacalculator.data.DraftStore;
import com.gpacalculator.data.HistoryRepository;
import com.gpacalculator.logic.GpaCalculator;
import com.gpacalculator.logic.PreviousRecord;
import com.gpacalculator.model.Course;
import com.gpacalculator.model.GpaResult;
import com.gpacalculator.model.Grade;
import com.gpacalculator.model.GradeScale;
import com.gpacalculator.model.HistoryRecord;
import com.gpacalculator.ui.util.Dimens;
import com.gpacalculator.ui.util.Formatters;
import com.gpacalculator.ui.util.Haptics;
import com.gpacalculator.ui.util.Keyboard;
import com.gpacalculator.ui.util.Motion;
import com.gpacalculator.ui.util.NumberFilters;
import com.gpacalculator.ui.util.PressEffect;
import com.gpacalculator.ui.util.SystemInsets;
import com.gpacalculator.ui.widget.IosSwitch;
import com.gpacalculator.ui.widget.SegmentedControl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The Calculator tab: scale picker, live GPA, course list, cumulative inputs and Save. */
final class CalculatorScreen {
    static final int MAX_COURSES = 40;

    private final Activity activity;
    private final HistoryRepository repository;
    private final DraftStore draftStore;
    private final Banner banner;
    private final LayoutInflater inflater;

    private final ScrollView scroll;
    private final View content;
    private final TextView largeTitle;
    private final NavBar navBar;
    private final SegmentedControl scaleControl;
    private final TextView heroValue;
    private final TextView heroOutOf;
    private final TextView statCredits;
    private final TextView statCourses;
    private final TextView statCumulative;
    private final View statCumulativeGroup;
    private final LinearLayout coursesList;
    private final View addSeparator;
    private final View addRow;
    private final TextView coursesFooter;
    private final View cumulativeCard;
    private final IosSwitch cumulativeSwitch;
    private final View cumulativeFields;
    private final EditText previousGpa;
    private final EditText previousCredits;
    private final TextView cumulativeFooter;
    private final TextView saveButton;

    private final List<Course> courses = new ArrayList<>();
    private GradeScale scale = GradeScale.FOUR;
    private long nextId = 1;

    private boolean restoring;
    private boolean saving;
    private boolean saved;
    private float displayedGpa = Float.NaN;
    private ValueAnimator heroAnimator;
    private int keyboardBottom;

    CalculatorScreen(Activity activity, View root, HistoryRepository repository, Banner banner) {
        this.activity = activity;
        this.repository = repository;
        this.banner = banner;
        this.draftStore = new DraftStore(activity);
        this.inflater = LayoutInflater.from(activity);

        scroll = root.findViewById(R.id.calc_scroll);
        content = root.findViewById(R.id.calc_content);
        largeTitle = root.findViewById(R.id.calc_large_title);
        navBar = new NavBar(root.findViewById(R.id.calc_top_bar));
        scaleControl = root.findViewById(R.id.calc_scale);
        heroValue = root.findViewById(R.id.hero_value);
        heroOutOf = root.findViewById(R.id.hero_out_of);
        statCredits = root.findViewById(R.id.stat_credits);
        statCourses = root.findViewById(R.id.stat_courses);
        statCumulative = root.findViewById(R.id.stat_cumulative);
        statCumulativeGroup = root.findViewById(R.id.stat_cumulative_group);
        coursesList = root.findViewById(R.id.courses_list);
        addSeparator = root.findViewById(R.id.courses_add_separator);
        addRow = root.findViewById(R.id.course_add);
        coursesFooter = root.findViewById(R.id.courses_footer);
        cumulativeCard = root.findViewById(R.id.cumulative_card);
        cumulativeSwitch = root.findViewById(R.id.cumulative_switch);
        cumulativeFields = root.findViewById(R.id.cumulative_fields);
        previousGpa = root.findViewById(R.id.prev_gpa);
        previousCredits = root.findViewById(R.id.prev_credits);
        cumulativeFooter = root.findViewById(R.id.cumulative_footer);
        saveButton = root.findViewById(R.id.calc_save);

        setUpNavBar();
        setUpScale();
        setUpCourses();
        setUpCumulative();
        saveButton.setOnClickListener(v -> save());
        PressEffect.attach(saveButton);

        restoreDraft();
    }

    // region Setup

    private void setUpNavBar() {
        navBar.title.setText(R.string.tab_calculator);
        navBar.action.setText(R.string.action_clear);
        navBar.action.setVisibility(View.VISIBLE);
        navBar.action.setOnClickListener(v -> confirmClear());
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> updateNavBar());
    }

    private void setUpScale() {
        String four = activity.getString(R.string.scale_4);
        String five = activity.getString(R.string.scale_5);
        scaleControl.setOptions(new String[]{four, five}, new String[]{
                activity.getString(R.string.cd_scale, four), activity.getString(R.string.cd_scale, five)});
        scaleControl.setOnSelectionChangedListener(index -> {
            scale = index == 0 ? GradeScale.FOUR : GradeScale.FIVE;
            onInputChanged();
        });
    }

    private void setUpCourses() {
        coursesList.setLayoutTransition(Motion.listTransition());
        addRow.setOnClickListener(v -> {
            if (courses.size() >= MAX_COURSES) {
                Haptics.warning(v);
                banner.show(activity.getString(R.string.courses_limit, Formatters.number(MAX_COURSES)));
                return;
            }
            openSheet(null);
        });
    }

    private void setUpCumulative() {
        LayoutTransition transition = Motion.listTransition();
        ((LinearLayout) cumulativeCard).setLayoutTransition(transition);

        cumulativeCard.findViewById(R.id.cumulative_toggle_row).setOnClickListener(v -> cumulativeSwitch.toggleFromUser());
        cumulativeSwitch.setOnCheckedChangeListener((view, checked, fromUser) -> {
            cumulativeFields.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (!checked) {
                clearFieldFocus();
            }
            onInputChanged();
        });

        previousGpa.setFilters(new InputFilter[]{NumberFilters.gpa()});
        previousCredits.setFilters(new InputFilter[]{NumberFilters.wholeNumber(3)});
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onInputChanged();
            }
        };
        previousGpa.addTextChangedListener(watcher);
        previousCredits.addTextChangedListener(watcher);
        previousCredits.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                clearFieldFocus();
                return true;
            }
            return false;
        });
        View.OnFocusChangeListener focus = (v, hasFocus) -> {
            if (hasFocus) ensureFocusedFieldVisible();
        };
        previousGpa.setOnFocusChangeListener(focus);
        previousCredits.setOnFocusChangeListener(focus);
    }

    private void restoreDraft() {
        restoring = true;
        DraftStore.Draft draft = draftStore.load();

        scale = draft.scale;
        scaleControl.setSelectedIndex(scale == GradeScale.FOUR ? 0 : 1, false);

        LayoutTransition listTransition = coursesList.getLayoutTransition();
        LayoutTransition cardTransition = ((LinearLayout) cumulativeCard).getLayoutTransition();
        coursesList.setLayoutTransition(null);
        ((LinearLayout) cumulativeCard).setLayoutTransition(null);

        for (Course c : draft.courses) {
            if (courses.size() >= MAX_COURSES) break;
            courses.add(c);
            nextId = Math.max(nextId, c.id + 1);
            coursesList.addView(createRow(c));
        }
        rebindRows();

        previousGpa.setText(draft.previousGpa);
        previousCredits.setText(draft.previousCredits);
        cumulativeSwitch.setChecked(draft.cumulative, false);
        cumulativeFields.setVisibility(draft.cumulative ? View.VISIBLE : View.GONE);

        coursesList.setLayoutTransition(listTransition);
        ((LinearLayout) cumulativeCard).setLayoutTransition(cardTransition);
        restoring = false;
        render(false);
    }

    // endregion

    // region Courses

    private View createRow(Course course) {
        View row = inflater.inflate(R.layout.item_course_row, coursesList, false);
        row.setTag(course.id);
        row.findViewById(R.id.course_row).setOnClickListener(v -> {
            int index = indexOf((long) row.getTag());
            if (index >= 0) openSheet(courses.get(index));
        });
        return row;
    }

    private void bindRow(View row, Course course, int index) {
        TextView name = row.findViewById(R.id.course_name);
        TextView detail = row.findViewById(R.id.course_detail);
        TextView grade = row.findViewById(R.id.course_grade);
        String title = course.name.isEmpty()
                ? activity.getString(R.string.course_fallback, Formatters.number(index + 1))
                : course.name;
        String credits = Formatters.creditHours(activity, course.credits);
        name.setText(title);
        detail.setText(credits);
        grade.setText(course.grade.code());
        grade.setTextColor(activity.getColor(course.grade == Grade.F ? R.color.destructive : R.color.tint));
        row.findViewById(R.id.row_separator).setVisibility(index == 0 ? View.GONE : View.VISIBLE);
        row.findViewById(R.id.course_row).setContentDescription(
                activity.getString(R.string.cd_course_row, title, credits, course.grade.code()));
    }

    private void rebindRows() {
        for (int i = 0; i < coursesList.getChildCount() && i < courses.size(); i++) {
            bindRow(coursesList.getChildAt(i), courses.get(i), i);
        }
    }

    private int indexOf(long id) {
        for (int i = 0; i < courses.size(); i++) {
            if (courses.get(i).id == id) return i;
        }
        return -1;
    }

    private void openSheet(Course existing) {
        clearFieldFocus();
        int defaultCredits = courses.isEmpty() ? 3 : courses.get(courses.size() - 1).credits;
        CourseSheet.show(activity, scale, existing, defaultCredits, new CourseSheet.Listener() {
            @Override
            public void onSave(String name, int credits, Grade grade) {
                if (existing == null) {
                    if (courses.size() >= MAX_COURSES) return;
                    Course course = new Course(nextId++, name, credits, grade);
                    courses.add(course);
                    View row = createRow(course);
                    coursesList.addView(row);
                    bindRow(row, course, courses.size() - 1);
                } else {
                    int index = indexOf(existing.id);
                    if (index < 0) return;
                    Course updated = new Course(existing.id, name, credits, grade);
                    courses.set(index, updated);
                    bindRow(coursesList.getChildAt(index), updated, index);
                }
                onInputChanged();
            }

            @Override
            public void onDelete() {
                if (existing == null) return;
                int index = indexOf(existing.id);
                if (index < 0) return;
                courses.remove(index);
                coursesList.removeViewAt(index);
                rebindRows();
                onInputChanged();
            }
        });
    }

    // endregion

    // region State

    private void onInputChanged() {
        if (restoring) return;
        saved = false;
        render(true);
        persistDraft();
    }

    private void persistDraft() {
        DraftStore.Draft d = new DraftStore.Draft();
        d.scale = scale;
        d.courses = new ArrayList<>(courses);
        d.cumulative = cumulativeSwitch.isChecked();
        d.previousGpa = previousGpa.getText().toString();
        d.previousCredits = previousCredits.getText().toString();
        draftStore.save(d);
    }

    /** Snapshot of everything derived from the current inputs. */
    private static final class Computed {
        GpaResult semester;
        PreviousRecord previous;
        GpaResult cumulative;
    }

    private Computed compute() {
        Computed c = new Computed();
        c.semester = GpaCalculator.semester(courses, scale);
        if (cumulativeSwitch.isChecked()) {
            c.previous = PreviousRecord.parse(previousGpa.getText(), previousCredits.getText(), scale);
            if (c.previous.isValid() && c.semester.hasGpa()) {
                c.cumulative = GpaCalculator.cumulative(c.semester, c.previous.gpa, c.previous.credits);
            }
        }
        return c;
    }

    private void render(boolean animate) {
        Computed c = compute();

        setHeroValue(c.semester.gpa, animate);
        heroOutOf.setText(activity.getString(R.string.hero_out_of, GpaCalculator.format(scale.max())));
        statCredits.setText(Formatters.number(c.semester.credits.intValue()));
        statCourses.setText(Formatters.number(courses.size()));
        if (c.cumulative != null) {
            statCumulative.setText(GpaCalculator.format(c.cumulative.gpa));
            statCumulativeGroup.setVisibility(View.VISIBLE);
        } else {
            statCumulativeGroup.setVisibility(View.GONE);
        }

        boolean hasCourses = !courses.isEmpty();
        addSeparator.setVisibility(hasCourses ? View.VISIBLE : View.GONE);
        coursesFooter.setText(hasCourses ? R.string.courses_footer : R.string.courses_footer_empty);

        renderCumulativeFooter(c);
        renderSaveButton(c);

        boolean anything = hasCourses || cumulativeSwitch.isChecked()
                || previousGpa.length() > 0 || previousCredits.length() > 0;
        navBar.action.setEnabled(anything);
    }

    private void renderCumulativeFooter(Computed c) {
        boolean gpaError = false;
        boolean creditsError = false;
        String text;
        if (c.previous == null) {
            text = activity.getString(R.string.cumulative_footer_off);
        } else {
            switch (c.previous.status) {
                case GPA_OUT_OF_RANGE:
                    gpaError = true;
                    text = activity.getString(R.string.cumulative_footer_gpa_invalid, GpaCalculator.format(scale.max()));
                    break;
                case CREDITS_OUT_OF_RANGE:
                    creditsError = true;
                    text = activity.getString(R.string.cumulative_footer_credits_invalid,
                            Formatters.number(PreviousRecord.MAX_CREDITS));
                    break;
                case INCOMPLETE:
                    text = activity.getString(R.string.cumulative_footer_incomplete);
                    break;
                case VALID:
                default:
                    text = c.cumulative != null
                            ? activity.getString(R.string.cumulative_footer_valid,
                            GpaCalculator.format(c.cumulative.gpa),
                            Formatters.number(c.cumulative.credits.intValue()))
                            : activity.getString(R.string.cumulative_footer_no_courses);
                    break;
            }
        }
        boolean error = gpaError || creditsError;
        cumulativeFooter.setText(text);
        cumulativeFooter.setTextColor(activity.getColor(error ? R.color.destructive : R.color.label_secondary));
        previousGpa.setActivated(gpaError);
        previousCredits.setActivated(creditsError);
    }

    private boolean canSave(Computed c) {
        if (courses.isEmpty() || saving || saved) return false;
        return c.previous == null || c.previous.isValid();
    }

    private void renderSaveButton(Computed c) {
        saveButton.setEnabled(canSave(c));
        saveButton.setActivated(saved);
        saveButton.setText(saved ? R.string.saved : R.string.save_to_history);
    }

    private void setHeroValue(BigDecimal gpa, boolean animate) {
        if (heroAnimator != null) heroAnimator.cancel();
        if (gpa == null) {
            heroValue.setText(R.string.placeholder);
            heroValue.setTextColor(activity.getColor(R.color.label_tertiary));
            displayedGpa = Float.NaN;
            return;
        }
        heroValue.setTextColor(activity.getColor(R.color.label));
        BigDecimal rounded = GpaCalculator.round(gpa);
        float target = rounded.floatValue();
        String finalText = rounded.toPlainString();

        if (!animate || Float.isNaN(displayedGpa)) {
            boolean appearing = animate && Float.isNaN(displayedGpa);
            displayedGpa = target;
            heroValue.setText(finalText);
            if (appearing) {
                heroValue.setAlpha(0f);
                heroValue.setScaleX(0.9f);
                heroValue.setScaleY(0.9f);
                heroValue.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(Motion.SPRING_DURATION).setInterpolator(Motion.SPRING).start();
            }
            return;
        }
        if (displayedGpa == target) {
            heroValue.setText(finalText);
            return;
        }

        heroAnimator = ValueAnimator.ofFloat(displayedGpa, target);
        heroAnimator.setDuration(420);
        heroAnimator.setInterpolator(Motion.EASE_OUT);
        heroAnimator.addUpdateListener(a -> {
            displayedGpa = (float) a.getAnimatedValue();
            heroValue.setText(String.format(Locale.US, "%.2f", displayedGpa));
        });
        heroAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    displayedGpa = target;
                    heroValue.setText(finalText);
                }
            }
        });
        heroAnimator.start();
    }

    // endregion

    // region Actions

    private void save() {
        Computed c = compute();
        if (!canSave(c)) return;
        saving = true;
        renderSaveButton(c);

        boolean withCumulative = c.cumulative != null;
        HistoryRecord record = new HistoryRecord(0, System.currentTimeMillis(), scale, new ArrayList<>(courses),
                c.semester.gpa, c.semester.credits.intValue(),
                withCumulative ? c.previous.gpa : null,
                withCumulative ? c.previous.credits.intValue() : null,
                withCumulative ? c.cumulative.gpa : null);

        repository.save(record, result -> {
            saving = false;
            if (result == null) {
                Haptics.warning(saveButton);
                banner.show(activity.getString(R.string.save_failed));
            } else {
                saved = true;
                Haptics.success(saveButton);
                PressEffect.pop(saveButton, 0.96f);
                banner.show(activity.getString(R.string.saved_banner));
            }
            renderSaveButton(compute());
        });
    }

    private void confirmClear() {
        clearFieldFocus();
        IosAlert.showDestructive(activity,
                activity.getString(R.string.clear_title),
                activity.getString(R.string.clear_message),
                activity.getString(R.string.clear),
                this::clearAll);
    }

    private void clearAll() {
        restoring = true;
        courses.clear();
        coursesList.removeAllViews();
        previousGpa.setText("");
        previousCredits.setText("");
        cumulativeSwitch.setChecked(false, true);
        cumulativeFields.setVisibility(View.GONE);
        restoring = false;
        onInputChanged();
        scroll.smoothScrollTo(0, 0);
    }

    // endregion

    // region Window, scrolling, keyboard

    void applyInsets(SystemInsets insets, int tabBarHeight) {
        navBar.applyInsets(insets);
        int margin = activity.getResources().getDimensionPixelSize(R.dimen.page_margin);
        content.setPadding(margin + insets.left, navBar.height(), margin + insets.right, 0);
        int bottom = Math.max(tabBarHeight, insets.ime) + Dimens.dp(activity, 32);
        scroll.setPadding(0, 0, 0, bottom);
        keyboardBottom = insets.keyboardVisible() ? insets.ime : 0;
        if (insets.keyboardVisible()) ensureFocusedFieldVisible();
        scroll.post(this::updateNavBar);
    }

    private void updateNavBar() {
        int threshold = largeTitle.getBottom() - navBar.height() - Dimens.dp(activity, 4);
        navBar.setCollapsed(scroll.getScrollY() > threshold);
    }

    void scrollToTop() {
        scroll.smoothScrollTo(0, 0);
    }

    void onHidden() {
        clearFieldFocus();
    }

    private void clearFieldFocus() {
        if (previousGpa.hasFocus() || previousCredits.hasFocus()) {
            Keyboard.hide(previousGpa);
            previousGpa.clearFocus();
            previousCredits.clearFocus();
        }
    }

    private void ensureFocusedFieldVisible() {
        View focused = previousGpa.hasFocus() ? previousGpa : previousCredits.hasFocus() ? previousCredits : null;
        if (focused == null || keyboardBottom == 0) return;
        scroll.post(() -> {
            Rect r = new Rect();
            focused.getDrawingRect(r);
            scroll.offsetDescendantRectToMyCoords(focused, r);
            int visibleBottom = scroll.getScrollY() + scroll.getHeight() - keyboardBottom - Dimens.dp(activity, 56);
            if (r.bottom > visibleBottom) {
                scroll.smoothScrollBy(0, r.bottom - visibleBottom);
            }
        });
    }

    // endregion
}
