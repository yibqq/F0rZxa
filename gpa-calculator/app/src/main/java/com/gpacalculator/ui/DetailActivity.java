package com.gpacalculator.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gpacalculator.R;
import com.gpacalculator.data.HistoryRepository;
import com.gpacalculator.logic.GpaCalculator;
import com.gpacalculator.model.Course;
import com.gpacalculator.model.Grade;
import com.gpacalculator.model.GradeScale;
import com.gpacalculator.model.HistoryRecord;
import com.gpacalculator.ui.util.Dimens;
import com.gpacalculator.ui.util.EdgeToEdge;
import com.gpacalculator.ui.util.Formatters;
import com.gpacalculator.ui.util.Haptics;
import com.gpacalculator.ui.util.SystemInsets;

/** Read-only view of a saved calculation. */
public final class DetailActivity extends Activity {
    private static final String EXTRA_ID = "record_id";

    private HistoryRepository repository;
    private NavBar navBar;
    private ScrollView scroll;
    private TextView largeTitle;
    private long recordId;

    @SuppressWarnings("deprecation")
    static void open(Activity from, long id) {
        from.startActivity(new Intent(from, DetailActivity.class).putExtra(EXTRA_ID, id));
        if (Build.VERSION.SDK_INT < 34) {
            from.overridePendingTransition(R.anim.push_enter, R.anim.push_exit);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.push_enter, R.anim.push_exit);
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.pop_enter, R.anim.pop_exit);
        }
        EdgeToEdge.apply(getWindow());
        setContentView(R.layout.activity_detail);

        repository = HistoryRepository.get(this);
        recordId = getIntent().getLongExtra(EXTRA_ID, -1);

        scroll = findViewById(R.id.detail_scroll);
        largeTitle = findViewById(R.id.detail_large_title);
        navBar = new NavBar(findViewById(R.id.detail_top_bar));
        navBar.back.setVisibility(View.VISIBLE);
        navBar.back.setContentDescription(getString(R.string.back));
        navBar.back.setOnClickListener(v -> finish());
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> updateNavBar());

        View root = findViewById(R.id.detail_root);
        View content = findViewById(R.id.detail_content);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            SystemInsets in = SystemInsets.from(insets);
            navBar.applyInsets(in);
            int margin = getResources().getDimensionPixelSize(R.dimen.page_margin);
            content.setPadding(margin + in.left, navBar.height(), margin + in.right, 0);
            scroll.setPadding(0, 0, 0, in.bottom + Dimens.dp(this, 32));
            scroll.post(this::updateNavBar);
            return insets;
        });

        findViewById(R.id.detail_delete).setOnClickListener(v -> confirmDelete());

        repository.load(recordId, record -> {
            if (record == null) {
                finish();
            } else {
                bind(record);
            }
        });
    }

    private void updateNavBar() {
        int threshold = largeTitle.getBottom() - navBar.height() - Dimens.dp(this, 4);
        navBar.setCollapsed(scroll.getScrollY() > threshold);
    }

    private void bind(HistoryRecord r) {
        String date = Formatters.longDate(this, r.createdAt);
        largeTitle.setText(date);
        navBar.title.setText(date);

        String scaleLabel = getString(r.scale == GradeScale.FIVE ? R.string.scale_5 : R.string.scale_4);
        ((TextView) findViewById(R.id.detail_gpa)).setText(GpaCalculator.format(r.semesterGpa));
        ((TextView) findViewById(R.id.detail_out_of))
                .setText(getString(R.string.hero_out_of, GpaCalculator.format(r.scale.max())));
        ((TextView) findViewById(R.id.detail_credits)).setText(Formatters.number(r.semesterCredits));
        ((TextView) findViewById(R.id.detail_courses)).setText(Formatters.number(r.courses.size()));
        ((TextView) findViewById(R.id.detail_points)).setText(
                GpaCalculator.format(GpaCalculator.semester(r.courses, r.scale).qualityPoints));

        LayoutInflater inflater = LayoutInflater.from(this);
        if (r.hasCumulative()) {
            findViewById(R.id.detail_cumulative_section).setVisibility(View.VISIBLE);
            ViewGroup card = findViewById(R.id.detail_cumulative_card);
            card.removeAllViews();
            addKeyValue(inflater, card, getString(R.string.previous_gpa), GpaCalculator.format(r.previousGpa), false);
            addKeyValue(inflater, card, getString(R.string.completed_credits),
                    Formatters.number(r.previousCredits), false);
            addKeyValue(inflater, card, getString(R.string.total_credits), Formatters.number(r.totalCredits()), false);
            addKeyValue(inflater, card, getString(R.string.cumulative_gpa), GpaCalculator.format(r.cumulativeGpa), true);
        }

        ViewGroup courses = findViewById(R.id.detail_courses_card);
        courses.removeAllViews();
        for (int i = 0; i < r.courses.size(); i++) {
            Course c = r.courses.get(i);
            View row = inflater.inflate(R.layout.item_course_row, courses, false);
            View inner = row.findViewById(R.id.course_row);
            inner.setClickable(false);
            inner.setFocusable(true);
            inner.setBackground(null);
            row.findViewById(R.id.course_chevron).setVisibility(View.GONE);
            row.findViewById(R.id.row_separator).setVisibility(i == 0 ? View.GONE : View.VISIBLE);

            String title = c.name.isEmpty() ? getString(R.string.course_fallback, Formatters.number(i + 1)) : c.name;
            String detail = getString(R.string.course_detail_points, Formatters.creditHours(this, c.credits),
                    GpaCalculator.format(c.grade.points(r.scale)));
            ((TextView) row.findViewById(R.id.course_name)).setText(title);
            ((TextView) row.findViewById(R.id.course_detail)).setText(detail);
            TextView grade = row.findViewById(R.id.course_grade);
            grade.setText(c.grade.code());
            grade.setTextColor(getColor(c.grade == Grade.F ? R.color.destructive : R.color.tint));
            inner.setContentDescription(getString(R.string.cd_course_row, title, detail, c.grade.code()));
            courses.addView(row);
        }

        ((TextView) findViewById(R.id.detail_footer))
                .setText(getString(R.string.detail_footer, Formatters.dateTime(this, r.createdAt), scaleLabel));
        scroll.post(this::updateNavBar);
    }

    private void addKeyValue(LayoutInflater inflater, ViewGroup card, String key, String value, boolean emphasize) {
        View row = inflater.inflate(R.layout.item_kv_row, card, false);
        ((TextView) row.findViewById(R.id.kv_key)).setText(key);
        TextView valueView = row.findViewById(R.id.kv_value);
        valueView.setText(value);
        if (emphasize) {
            valueView.setTextColor(getColor(R.color.tint));
            valueView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        }
        row.findViewById(R.id.row_separator).setVisibility(card.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        card.addView(row);
    }

    private void confirmDelete() {
        IosAlert.showDestructive(this,
                getString(R.string.delete_record_title),
                getString(R.string.delete_record_message),
                getString(R.string.delete),
                () -> {
                    Haptics.success(scroll);
                    repository.delete(recordId, ok -> finish());
                });
    }

    @SuppressWarnings("deprecation")
    @Override
    public void finish() {
        super.finish();
        if (Build.VERSION.SDK_INT < 34) {
            overridePendingTransition(R.anim.pop_enter, R.anim.pop_exit);
        }
    }
}
