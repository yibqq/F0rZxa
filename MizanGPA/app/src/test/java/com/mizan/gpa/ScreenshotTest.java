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
            });
        }
        String courseId = cur.courses.get(3).id; // the course close to deprivation
        for (int tab = 0; tab < 3; tab++) {
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
