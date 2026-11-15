package com.mizan.gpa.ui;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Task;
import com.mizan.gpa.notify.Notifier;
import com.mizan.gpa.util.Ui;

import java.time.LocalDateTime;
import java.util.List;

/**
 * "Reminders & Tasks": exams, assignments, practical assessments and general reminders, with
 * category filters, open/closed views, sorting, and sections for overdue, upcoming and undated
 * entries. Shared by both interface modes; with a course id it shows that course's entries only.
 */
public class TasksFragment extends BaseFragment {
    public static final String ARG_COURSE = "course";
    private static final String STATE_CATEGORY = "category", STATE_CLOSED = "closed";

    private View root, header;
    private String category;      // null = all
    private boolean closed;
    private String courseId;
    private ActivityResultLauncher<String> permission;

    public static TasksFragment forCourse(String courseId) {
        TasksFragment f = new TasksFragment();
        Bundle b = new Bundle();
        b.putString(ARG_COURSE, courseId);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        permission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> render());
        courseId = getArguments() == null ? null : getArguments().getString(ARG_COURSE);
        if (state != null) {
            category = state.getString(STATE_CATEGORY);
            closed = state.getBoolean(STATE_CLOSED);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_CATEGORY, category);
        out.putBoolean(STATE_CLOSED, closed);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        root = inflater.inflate(R.layout.fragment_list, container, false);
        ExtendedFloatingActionButton fab = root.findViewById(R.id.fab);
        if (courseId != null) {
            // Inside a course the activity's button adds tasks.
            fab.setVisibility(View.GONE);
            Ui.padForNavBar(root.findViewById(R.id.scroll));
        } else {
            fab.setText(R.string.add_task);
            fab.setOnClickListener(v -> add());
        }
        FrameLayout h = root.findViewById(R.id.header);
        header = inflater.inflate(R.layout.header_tasks, h, false);
        h.addView(header);

        ChipGroup filters = header.findViewById(R.id.t_filters);
        addFilter(filters, null, getString(R.string.tasks_all));
        for (String c : Task.CATEGORIES) addFilter(filters, c, TaskUi.categoryLabel(requireContext(), c));

        MaterialButtonToggleGroup st = header.findViewById(R.id.t_state);
        st.check(closed ? R.id.t_state_closed : R.id.t_state_open);
        st.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            closed = id == R.id.t_state_closed;
            render();
        });
        header.findViewById(R.id.t_sort).setOnClickListener(v -> chooseSort());
        return root;
    }

    private void addFilter(ChipGroup group, String cat, String label) {
        Chip c = new Chip(requireContext());
        c.setId(View.generateViewId());
        c.setText(label);
        c.setCheckable(true);
        c.setCheckedIconVisible(false);
        if (cat != null) {
            c.setChipIconResource(TaskUi.categoryIcon(cat));
            c.setChipIconVisible(true);
        }
        c.setChecked(cat == null ? category == null : cat.equals(category));
        c.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                category = cat;
                render();
            }
        });
        group.addView(c);
    }

    /** Adds a task, preselecting the course and the filtered category. */
    public void add() {
        AppData d = data();
        Course c = courseId == null ? null : d.findCourse(courseId);
        String cat = category != null ? category : (courseId != null ? Task.EXAM : Task.GENERAL);
        Dialogs.editTask((AppCompatActivity) requireActivity(), null, c, cat);
    }

    private void chooseSort() {
        String[] values = {TaskUi.SORT_DATE, TaskUi.SORT_COURSE, TaskUi.SORT_CATEGORY};
        CharSequence[] labels = {getString(R.string.tasks_sort_date), getString(R.string.tasks_sort_course),
                getString(R.string.tasks_sort_category)};
        String cur = data().settings.taskSort;
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) checked = i;
        Dialogs.choose(requireContext(), getString(R.string.tasks_sort), labels, checked, which -> {
            data().settings.taskSort = values[which];
            repo().save();
        });
    }

    @Override
    protected void render() {
        AppData d = data();
        LocalDateTime now = LocalDateTime.now();
        renderBanner(d);
        LinearLayout list = root.findViewById(R.id.list);
        list.removeAllViews();
        List<Task> items = TaskUi.filter(d, category, courseId, closed, d.settings.taskSort, now);
        if (items.isEmpty()) {
            if (closed) Rows.empty(root, R.drawable.ic_check_circle, R.string.tasks_empty_closed_title, R.string.tasks_empty_closed_text, 0, null);
            else Rows.empty(root, R.drawable.ic_notifications, R.string.tasks_empty_title, R.string.tasks_empty_text,
                    R.string.add_task, v -> add());
            return;
        }
        Rows.hideEmpty(root);
        boolean byDate = TaskUi.SORT_DATE.equals(d.settings.taskSort);
        TaskUi.State lastSection = null;
        for (Task t : items) {
            TaskUi.State s = TaskUi.state(t, now);
            if (byDate && !closed && s != lastSection) {
                int title = s == TaskUi.State.OVERDUE ? R.string.tasks_section_overdue
                        : s == TaskUi.State.UNDATED ? R.string.tasks_section_undated : R.string.tasks_section_upcoming;
                list.addView(section(list, getString(title)));
                lastSection = s;
            }
            list.addView(Rows.taskCard(list, (AppCompatActivity) requireActivity(), t, courseId == null));
        }
    }

    private View section(ViewGroup parent, String text) {
        TextView t = (TextView) LayoutInflater.from(requireContext()).inflate(R.layout.view_day_header, parent, false);
        t.setText(text);
        return t;
    }

    /** Tells the user plainly when reminders will not arrive, with a way to fix it. */
    private void renderBanner(AppData d) {
        View banner = header.findViewById(R.id.t_banner);
        TextView text = header.findViewById(R.id.t_banner_text);
        TextView action = header.findViewById(R.id.t_banner_action);
        boolean anyDated = false;
        for (Task t : d.tasks) anyDated |= t.hasDate() && !t.done;
        if (!anyDated) {
            banner.setVisibility(View.GONE);
        } else if (!d.settings.examReminders) {
            banner.setVisibility(View.VISIBLE);
            text.setText(R.string.tasks_banner_reminders_off);
            action.setText(R.string.enable);
            action.setOnClickListener(v -> {
                d.settings.examReminders = true;
                repo().save();
            });
        } else if (!Notifier.canPost(requireContext())) {
            banner.setVisibility(View.VISIBLE);
            text.setText(R.string.tasks_banner_notifications_off);
            action.setText(R.string.enable);
            action.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS);
                else startActivity(new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName()));
            });
        } else {
            banner.setVisibility(View.GONE);
        }
    }
}
