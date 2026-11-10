package com.mizan.gpa;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.export.SampleData;
import com.mizan.gpa.ui.CourseActivity;
import com.mizan.gpa.ui.GradeScaleActivity;
import com.mizan.gpa.ui.MainActivity;
import com.mizan.gpa.ui.SemesterActivity;
import com.mizan.gpa.ui.SettingsActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.Duration;

/** Every screen's background must follow the chosen palette, not the base theme. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
public class ThemeBackgroundTest {
    private Context ctx;
    private Semester sem;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        Settings st = new Settings();
        st.colorTheme = "violet";
        Repository.get(ctx).replace(SampleData.create(ctx, st));
        sem = Repository.get(ctx).data().currentSemester();
    }

    private void check(Class<? extends Activity> type, Intent intent, int expectedColorRes, Integer tab) {
        Intent i = intent != null ? intent : new Intent(ctx, type);
        try (ActivityScenario<Activity> sc = ActivityScenario.launch(i)) {
            sc.onActivity(a -> {
                if (tab != null) ((ViewPager2) a.findViewById(R.id.pager)).setCurrentItem(tab, false);
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800));
                View decor = a.getWindow().getDecorView();
                Bitmap b = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
                decor.draw(new Canvas(b));
                // A point near the bottom edge that no card or button covers.
                int px = b.getPixel(2, b.getHeight() - 2);
                assertEquals(type.getSimpleName() + (tab != null ? " tab " + tab : ""),
                        Integer.toHexString(ContextCompat.getColor(ctx, expectedColorRes)), Integer.toHexString(px));
            });
        }
    }

    @Test
    public void lightBackgroundsFollowPalette() {
        int bg = R.color.theme_violet_background;
        check(SettingsActivity.class, null, bg, null);
        check(GradeScaleActivity.class, null, bg, null);
        check(SemesterActivity.class, SemesterActivity.intent(ctx, sem.id), bg, null);
        for (int tab = 0; tab < 4; tab++) {
            check(CourseActivity.class, CourseActivity.intent(ctx, sem.courses.get(0).id, tab), bg, tab);
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    public void darkBackgroundsFollowPalette() {
        int bg = R.color.theme_violet_background;
        check(SettingsActivity.class, null, bg, null);
        for (int tab = 0; tab < 4; tab++) {
            check(CourseActivity.class, CourseActivity.intent(ctx, sem.courses.get(0).id, tab), bg, tab);
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    public void pureBlackReachesEveryScreen() {
        Repository.get(ctx).settings().pureBlack = true;
        Repository.get(ctx).save();
        try (ActivityScenario<SettingsActivity> sc = ActivityScenario.launch(SettingsActivity.class)) {
            sc.onActivity(a -> {
                shadowOf(Looper.getMainLooper()).idle();
                View decor = a.getWindow().getDecorView();
                Bitmap b = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
                decor.draw(new Canvas(b));
                assertEquals("ff000000", Integer.toHexString(b.getPixel(2, b.getHeight() - 2)));
            });
        }
    }
}
