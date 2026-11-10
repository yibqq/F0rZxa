package com.mizan.gpa;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.export.PdfReport;
import com.mizan.gpa.export.SampleData;
import com.mizan.gpa.ui.CourseActivity;
import com.mizan.gpa.ui.Dialogs;
import com.mizan.gpa.ui.GradeScaleActivity;
import com.mizan.gpa.ui.MainActivity;
import com.mizan.gpa.ui.SemesterActivity;
import com.mizan.gpa.ui.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;

/**
 * Renders every screen with real graphics (Robolectric native mode) and saves PNGs, in English,
 * Arabic (RTL) and dark mode. Doubles as a smoke test: every screen must inflate, bind and draw.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
public class ScreenshotTest {
    private static final int[] TABS = {R.id.tab_home, R.id.tab_semesters, R.id.tab_attendance, R.id.tab_forecast, R.id.tab_schedule};
    private static final String[] TAB_NAMES = {"1-home", "2-semesters", "3-attendance", "4-forecast", "5-schedule"};

    @Test
    public void english() throws Exception {
        shootAll("en");
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    public void arabic() throws Exception {
        shootAll("ar");
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-night-xxhdpi")
    public void arabicDark() throws Exception {
        shootAll("ar-dark");
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    public void tablet() throws Exception {
        load();
        try (ActivityScenario<MainActivity> sc = ActivityScenario.launch(MainActivity.class)) {
            sc.onActivity(a -> {
                settle();
                save(a.getWindow().getDecorView(), "tablet-home");
            });
        }
    }

    /** A different palette, font and pure black dark mode. */
    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-night-xxhdpi")
    public void violetCairoPureBlack() {
        load();
        Context ctx = ApplicationProvider.getApplicationContext();
        Settings st = Repository.get(ctx).settings();
        st.colorTheme = "violet";
        st.fontId = "cairo";
        st.pureBlack = true;
        Repository.get(ctx).save();
        try (ActivityScenario<MainActivity> sc = ActivityScenario.launch(MainActivity.class)) {
            sc.onActivity(a -> {
                settle();
                save(a.getWindow().getDecorView(), "theme-violet-cairo-home");
                a.selectTab(R.id.tab_schedule);
                settle();
                saveScrollContent(a, "theme-violet-cairo-schedule");
            });
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    public void oceanPlexLight() {
        load();
        Context ctx = ApplicationProvider.getApplicationContext();
        Settings st = Repository.get(ctx).settings();
        st.colorTheme = "ocean";
        st.fontId = "plex";
        Repository.get(ctx).save();
        try (ActivityScenario<MainActivity> sc = ActivityScenario.launch(MainActivity.class)) {
            sc.onActivity(a -> {
                settle();
                save(a.getWindow().getDecorView(), "theme-ocean-plex-home");
            });
        }
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    public void widgetsArabic() {
        widgets("ar");
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    public void widgetsEnglishDark() {
        widgets("en-dark");
    }

    /** Applies the widget RemoteViews and fills their lists from the real list factories. */
    private void widgets(String prefix) {
        load();
        Context ctx = ApplicationProvider.getApplicationContext();
        android.widget.FrameLayout host = new android.widget.FrameLayout(ctx);
        renderWidget(ctx, host, com.mizan.gpa.widget.AttendanceWidget.build(ctx, 1),
                new com.mizan.gpa.widget.AttendanceWidgetService.Factory(ctx), prefix + "-widget-attendance", 380);
        renderWidget(ctx, host, com.mizan.gpa.widget.ScheduleWidget.build(ctx, 2),
                new com.mizan.gpa.widget.ScheduleWidgetService.Factory(ctx), prefix + "-widget-schedule", 560);
        renderWidget(ctx, host, com.mizan.gpa.widget.ExamsWidget.build(ctx, 3),
                new com.mizan.gpa.widget.ExamsWidgetService.Factory(ctx), prefix + "-widget-exams", 300);
        measureAndSave(com.mizan.gpa.widget.GpaWidget.build(ctx, 4).apply(ctx, host), 170, 170, prefix + "-widget-gpa");
        measureAndSave(com.mizan.gpa.widget.NextLectureWidget.build(ctx, 5).apply(ctx, host), 360, 76, prefix + "-widget-next");
        // Previews shown in the launcher's widget picker.
        for (int layout : new int[]{R.layout.widget_attendance_preview, R.layout.widget_schedule_preview}) {
            View v = android.view.LayoutInflater.from(ctx).inflate(layout, host, false);
            measureAndSave(v, 360, layout == R.layout.widget_attendance_preview ? 300 : 340,
                    prefix + (layout == R.layout.widget_attendance_preview ? "-preview-attendance" : "-preview-schedule"));
        }
    }

    private void renderWidget(Context ctx, ViewGroup host, android.widget.RemoteViews rv,
                              android.widget.RemoteViewsService.RemoteViewsFactory factory, String name, int heightDp) {
        View root = rv.apply(ctx, host);
        android.widget.ListView listView = root.findViewById(R.id.w_list);
        ViewGroup frame = (ViewGroup) listView.getParent();
        frame.removeAllViews();
        android.widget.LinearLayout rows = new android.widget.LinearLayout(ctx);
        rows.setOrientation(android.widget.LinearLayout.VERTICAL);
        rows.setPadding(listView.getPaddingLeft(), 0, listView.getPaddingRight(), 0);
        factory.onCreate();
        factory.onDataSetChanged();
        for (int i = 0; i < factory.getCount(); i++) rows.addView(factory.getViewAt(i).apply(ctx, rows));
        frame.addView(rows);
        measureAndSave(root, 360, heightDp, name);
    }

    private static void measureAndSave(View v, int widthDp, int heightDp, String name) {
        float d = v.getResources().getDisplayMetrics().density;
        int w = (int) (widthDp * d), h = (int) (heightDp * d);
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        write(b, name);
    }

    /** Home screen in every palette, to compare them side by side. */
    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    public void paletteGalleryLight() {
        paletteGallery("light");
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-night-xxhdpi")
    public void paletteGalleryDark() {
        paletteGallery("dark");
    }

    private void paletteGallery(String mode) {
        load();
        Context ctx = ApplicationProvider.getApplicationContext();
        for (com.mizan.gpa.util.Themes.Palette p : com.mizan.gpa.util.Themes.PALETTES) {
            Repository.get(ctx).settings().colorTheme = p.id;
            Repository.get(ctx).save();
            try (ActivityScenario<MainActivity> sc = ActivityScenario.launch(MainActivity.class)) {
                sc.onActivity(a -> {
                    settle();
                    save(a.getWindow().getDecorView(), "palette-" + mode + "-" + p.id);
                });
            }
        }
    }

    /** The adaptive launcher icon as a circle and as a rounded square. */
    @Test
    public void launcherIcon() {
        Context ctx = ApplicationProvider.getApplicationContext();
        int size = 432;
        android.graphics.drawable.Drawable bg = ctx.getDrawable(R.drawable.ic_launcher_background);
        android.graphics.drawable.Drawable fg = ctx.getDrawable(R.drawable.ic_launcher_foreground);
        Bitmap out = Bitmap.createBitmap(size * 2 + 60, size + 40, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(0xFFE9EEF0);
        for (int i = 0; i < 2; i++) {
            Bitmap layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(layer);
            android.graphics.Path clip = new android.graphics.Path();
            if (i == 0) clip.addCircle(size / 2f, size / 2f, size / 2f, android.graphics.Path.Direction.CW);
            else clip.addRoundRect(0, 0, size, size, size * 0.24f, size * 0.24f, android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            // Adaptive icons show the middle 72 of 108 units.
            int full = size * 108 / 72, off = (full - size) / 2;
            bg.setBounds(-off, -off, full - off, full - off);
            bg.draw(c);
            fg.setBounds(-off, -off, full - off, full - off);
            fg.draw(c);
            canvas.drawBitmap(layer, 20 + i * (size + 20), 20, null);
        }
        write(out, "icon");
    }

    @Test
    public void emptyState() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        Repository.get(ctx).replace(new AppData());
        try (ActivityScenario<MainActivity> sc = ActivityScenario.launch(MainActivity.class)) {
            sc.onActivity(a -> {
                settle();
                save(a.getWindow().getDecorView(), "en-0-welcome");
            });
        }
    }

    /** PdfDocument has no native backend in Robolectric, so the report pages are drawn to bitmaps instead. */
    @Test
    public void pdfReportPages() {
        load();
        Context ctx = ApplicationProvider.getApplicationContext();
        renderPdf(ctx, "pdf-en");
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    public void pdfReportPagesArabic() {
        load();
        renderPdf(ApplicationProvider.getApplicationContext(), "pdf-ar");
    }

    private void renderPdf(Context ctx, String prefix) {
        int[] pages = {0};
        PdfReport.render(ctx, Repository.get(ctx).data(), new PdfReport.PageSink() {
            Bitmap bitmap;

            @Override
            public Canvas startPage(int number, int width, int height) {
                pages[0] = number;
                bitmap = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bitmap);
                c.drawColor(0xFFFFFFFF);
                c.scale(2, 2);
                return c;
            }

            @Override
            public void finishPage() {
                write(bitmap, prefix + "-page" + pages[0]);
            }
        });
        assertTrue(pages[0] >= 1);
    }

    private void load() {
        Context ctx = ApplicationProvider.getApplicationContext();
        Settings st = new Settings();
        st.studentName = "Sara";
        st.university = "King Saud University";
        Repository.get(ctx).replace(SampleData.create(ctx, st));
    }

    private void shootAll(String prefix) throws Exception {
        load();
        Context ctx = ApplicationProvider.getApplicationContext();
        AppData d = Repository.get(ctx).data();
        try (ActivityScenario<MainActivity> sc = ActivityScenario.launch(MainActivity.class)) {
            sc.onActivity(a -> {
                for (int i = 0; i < TABS.length; i++) {
                    a.selectTab(TABS[i]);
                    settle();
                    save(a.getWindow().getDecorView(), prefix + "-" + TAB_NAMES[i]);
                    saveScrollContent(a, prefix + "-" + TAB_NAMES[i] + "-full");
                }
                a.openExams();
                settle();
                save(a.getWindow().getDecorView(), prefix + "-5b-exams");
            });
        }
        Semester cur = d.currentSemester();
        assertNotNull(cur);
        try (ActivityScenario<SemesterActivity> sc = ActivityScenario.launch(SemesterActivity.intent(ctx, cur.id))) {
            sc.onActivity(a -> {
                settle();
                save(a.getWindow().getDecorView(), prefix + "-6-semester");
                Dialogs.editCourse(a, cur, cur.courses.get(0), null);
                settle();
                Dialog dlg = ShadowDialog.getLatestDialog();
                save(dlg.getWindow().getDecorView(), prefix + "-6b-course-dialog");
                dlg.dismiss();
                Dialogs.editExam(a, cur, null, cur.courses.get(3).exams.get(0));
                settle();
                Dialog examDlg = ShadowDialog.getLatestDialog();
                save(examDlg.getWindow().getDecorView(), prefix + "-6c-exam-dialog");
                examDlg.dismiss();
            });
        }
        String courseId = cur.courses.get(3).id; // the course close to deprivation
        for (int tab = 0; tab < 4; tab++) {
            int t = tab;
            try (ActivityScenario<CourseActivity> sc = ActivityScenario.launch(CourseActivity.intent(ctx, courseId, tab))) {
                sc.onActivity(a -> {
                    settle();
                    ViewPager2 pager = a.findViewById(R.id.pager);
                    pager.setCurrentItem(t, false);
                    settle();
                    save(a.getWindow().getDecorView(), prefix + "-7-course-tab" + t);
                });
            }
        }
        try (ActivityScenario<SettingsActivity> sc = ActivityScenario.launch(SettingsActivity.class)) {
            sc.onActivity(a -> {
                settle();
                save(a.getWindow().getDecorView(), prefix + "-8-settings");
                saveScrollContent(a, prefix + "-8-settings-full");
            });
        }
        try (ActivityScenario<GradeScaleActivity> sc = ActivityScenario.launch(GradeScaleActivity.class)) {
            sc.onActivity(a -> {
                settle();
                save(a.getWindow().getDecorView(), prefix + "-9-scale");
            });
        }
    }

    private static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static File dir() {
        File d = new File(System.getProperty("screenshots.dir", "build/screenshots"));
        d.mkdirs();
        return d;
    }

    private static void save(View v, String name) {
        int w = v.getWidth(), h = v.getHeight();
        if (w == 0 || h == 0) return;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        v.draw(c);
        write(b, name);
    }

    /** Draws the full height of the visible tab's scroll content. */
    private static void saveScrollContent(Activity a, String name) {
        NestedScrollView scroll = findVisibleScroll(a.getWindow().getDecorView());
        if (scroll == null || scroll.getChildCount() == 0) return;
        View child = scroll.getChildAt(0);
        int w = child.getWidth(), h = child.getHeight();
        if (w == 0 || h == 0) return;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(com.google.android.material.color.MaterialColors.getColor(scroll, android.R.attr.colorBackground));
        child.draw(c);
        write(b, name);
    }

    private static NestedScrollView findVisibleScroll(View v) {
        if (v.getVisibility() != View.VISIBLE) return null;
        if (v instanceof NestedScrollView && v.getId() == R.id.scroll) return (NestedScrollView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                NestedScrollView s = findVisibleScroll(g.getChildAt(i));
                if (s != null) return s;
            }
        }
        return null;
    }

    private static void write(Bitmap b, String name) {
        try (FileOutputStream f = new FileOutputStream(new File(dir(), name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
