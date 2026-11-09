package com.mizan.gpa.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.AttendanceRecord;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeDef;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.util.Fmt;

import java.time.LocalDate;

public class CourseActivity extends BaseActivity {
    public static final int TAB_MARKS = 0;
    public static final int TAB_ATTENDANCE = 1;
    public static final int TAB_LECTURES = 2;
    static final String EXTRA_ID = "course";
    private static final String EXTRA_TAB = "tab";

    private String courseId;
    private MaterialToolbar toolbar;
    private ExtendedFloatingActionButton fab;
    private ViewPager2 pager;

    public static Intent intent(Context c, String courseId, int tab) {
        return new Intent(c, CourseActivity.class).putExtra(EXTRA_ID, courseId).putExtra(EXTRA_TAB, Math.max(0, tab));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course);
        courseId = getIntent().getStringExtra(EXTRA_ID);
        toolbar = setupChrome(true);
        toolbar.inflateMenu(R.menu.course);
        toolbar.setOnMenuItemClickListener(item -> {
            Course c = course();
            Semester s = semester();
            if (c == null || s == null) return false;
            if (item.getItemId() == R.id.action_edit) {
                Dialogs.editCourse(this, s, c, null);
            } else if (item.getItemId() == R.id.action_delete) {
                Dialogs.confirm(this, getString(R.string.delete_course_title, c.displayName()),
                        getString(R.string.delete_course_text), R.string.delete, () -> {
                            s.courses.remove(c);
                            repo().save();
                            finish();
                        });
            }
            return true;
        });
        fab = findViewById(R.id.fab);
        pager = findViewById(R.id.pager);
        pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                Fragment f = position == TAB_ATTENDANCE ? new CourseAttendanceFragment()
                        : position == TAB_LECTURES ? new CourseLecturesFragment() : new CourseMarksFragment();
                Bundle b = new Bundle();
                b.putString(EXTRA_ID, courseId);
                f.setArguments(b);
                return f;
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });
        TabLayout tabs = findViewById(R.id.tabs);
        int[] titles = {R.string.tab_marks, R.string.tab_attendance, R.string.tab_lectures};
        int[] icons = {R.drawable.ic_assignment, R.drawable.ic_event_available, R.drawable.ic_calendar};
        new TabLayoutMediator(tabs, pager, (tab, pos) -> {
            tab.setText(titles[pos]);
            tab.setIcon(icons[pos]);
        }).attach();
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateFab(position);
            }
        });
        if (savedInstanceState == null) pager.setCurrentItem(getIntent().getIntExtra(EXTRA_TAB, 0), false);
        fab.setOnClickListener(v -> {
            Course c = course();
            Semester s = semester();
            if (c == null || s == null) return;
            switch (pager.getCurrentItem()) {
                case TAB_ATTENDANCE:
                    Dialogs.editRecord(this, c, null, AttendanceRecord.ABSENT);
                    break;
                case TAB_LECTURES:
                    Dialogs.editLecture(this, s, c, null, Fmt.toCalendarDay(LocalDate.now().getDayOfWeek()));
                    break;
                default:
                    Dialogs.editAssessment(this, c, null);
                    break;
            }
        });
    }

    private void updateFab(int position) {
        int text = position == TAB_ATTENDANCE ? R.string.record_attendance
                : position == TAB_LECTURES ? R.string.add_lecture : R.string.add_mark;
        fab.setText(text);
        fab.extend();
    }

    Course course() {
        return data().findCourse(courseId);
    }

    Semester semester() {
        return data().semesterOf(courseId);
    }

    @Override
    protected void render() {
        Course c = course();
        if (c == null) {
            finish();
            return;
        }
        AppData d = data();
        toolbar.setTitle(c.displayName());
        StringBuilder sub = new StringBuilder(Labels.courseSubtitle(this, c));
        GradeDef g = d.settings.scale.find(c.finalGrade);
        if (g != null) sub.append(" · ").append(getString(R.string.final_grade_short, g.letter));
        toolbar.setSubtitle(sub);
        updateFab(pager.getCurrentItem());
    }
}
