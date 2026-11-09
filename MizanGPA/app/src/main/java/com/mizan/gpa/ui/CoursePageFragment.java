package com.mizan.gpa.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;

import com.mizan.gpa.R;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.util.Ui;

/** Base for the course tabs: a header card and a list inside a card. */
public abstract class CoursePageFragment extends BaseFragment {
    protected View root;
    protected View header;
    protected LinearLayout list;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        root = inflater.inflate(R.layout.fragment_course_page, container, false);
        Ui.padForNavBar(root.findViewById(R.id.scroll));
        FrameLayout h = root.findViewById(R.id.header);
        if (headerLayout() != 0) {
            header = inflater.inflate(headerLayout(), h, false);
            h.addView(header);
        }
        list = root.findViewById(R.id.list);
        onCreated();
        return root;
    }

    protected abstract @LayoutRes int headerLayout();

    protected void onCreated() {}

    protected Course course() {
        return data().findCourse(requireArguments().getString(CourseActivity.EXTRA_ID));
    }

    protected Semester semester() {
        return data().semesterOf(requireArguments().getString(CourseActivity.EXTRA_ID));
    }

    protected AppCompatActivity activity() {
        return (AppCompatActivity) requireActivity();
    }

    protected void showList(int title, boolean visible) {
        TextView t = root.findViewById(R.id.list_title);
        t.setText(title);
        t.setVisibility(visible ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.list_card).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Wires the overflow button of an item_simple row to edit/delete actions. */
    protected void rowMenu(View row, Runnable edit, Runnable delete) {
        View menu = row.findViewById(R.id.s_menu);
        menu.setVisibility(View.VISIBLE);
        menu.setOnClickListener(v -> {
            PopupMenu p = new PopupMenu(requireContext(), menu);
            p.getMenu().add(0, 1, 0, R.string.edit);
            p.getMenu().add(0, 2, 0, R.string.delete);
            p.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) edit.run();
                else delete.run();
                return true;
            });
            p.show();
        });
        row.setOnClickListener(v -> edit.run());
    }
}
