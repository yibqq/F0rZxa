package com.mizan.gpa.ui;

import com.mizan.gpa.R;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.Semester;

import java.time.LocalDateTime;
import java.util.List;

/** Exams of one course: upcoming first, then finished ones (which can be turned into marks). */
public class CourseExamsFragment extends CoursePageFragment {
    @Override
    protected int headerLayout() {
        return 0;
    }

    @Override
    protected void render() {
        Course c = course();
        Semester s = semester();
        if (c == null || s == null) return;
        list.removeAllViews();
        root.findViewById(R.id.list_title).setVisibility(android.view.View.GONE);
        // Exam cards carry their own background, so the list sits outside the shared card.
        android.view.ViewGroup holder = root.findViewById(R.id.exam_holder);
        holder.removeAllViews();
        if (c.exams.isEmpty()) {
            Rows.empty(root, R.drawable.ic_exam, R.string.empty_exams_title, R.string.empty_exams_text,
                    R.string.add_exam, v -> Dialogs.editExam(activity(), s, c, null));
            return;
        }
        Rows.hideEmpty(root);
        int index = s.courses.indexOf(c);
        List<Exams.Entry> all = Exams.of(s, LocalDateTime.now());
        for (Exams.Entry e : all) {
            if (e.course != c || e.courseIndex != index) continue;
            holder.addView(Rows.examCard(holder, activity(), s, e, false));
        }
    }
}
