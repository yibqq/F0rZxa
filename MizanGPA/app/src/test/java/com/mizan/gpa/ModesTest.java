package com.mizan.gpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.Menu;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.navigation.NavigationBarView;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.export.SampleData;
import com.mizan.gpa.ui.MainActivity;
import com.mizan.gpa.ui.OnboardingActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.time.Duration;

/** Interface modes, onboarding persistence, and that switching modes never touches data. */
@RunWith(RobolectricTestRunner.class)
public class ModesTest {
    private final Context ctx = ApplicationProvider.getApplicationContext();

    private void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800));
    }

    /** Same effect as the developer option "Animator duration scale" (0 = animations off). */
    private static void animatorScale(float scale) {
        try {
            android.animation.ValueAnimator.class.getMethod("setDurationScale", float.class).invoke(null, scale);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private Menu navMenu(MainActivity a) {
        return ((NavigationBarView) a.findViewById(R.id.nav)).getMenu();
    }

    @Test
    public void liteHasExactlyThreeSectionsAndStandardFive() throws Exception {
        Settings st = new Settings();
        st.onboarded = true;
        st.appMode = Settings.MODE_LITE;
        Repository.get(ctx).replace(SampleData.create(ctx, st));
        String before = Repository.get(ctx).data().serialize(0);
        try (ActivityScenario<MainActivity> sc = ActivityScenario.launch(MainActivity.class)) {
            sc.onActivity(a -> {
                idle();
                Menu m = navMenu(a);
                assertEquals(3, m.size());
                assertEquals(R.id.tab_gpa, m.getItem(0).getItemId());
                assertEquals(R.id.tab_tasks, m.getItem(1).getItemId());
                assertEquals(R.id.tab_settings, m.getItem(2).getItemId());
            });
            // Switch back to Standard: the activity is rebuilt with the full navigation.
            sc.onActivity(a -> {
                Repository.get(ctx).settings().appMode = Settings.MODE_STANDARD;
                Repository.get(ctx).save();
                a.recreate();
            });
            sc.onActivity(a -> {
                idle();
                Menu m = navMenu(a);
                assertEquals(5, m.size());
                assertNotNull(m.findItem(R.id.tab_attendance));
                assertNotNull(m.findItem(R.id.tab_schedule));
            });
        }
        // No data changed apart from the mode itself.
        AppData after = Repository.get(ctx).data();
        AppData beforeData = AppData.parse(before);
        assertEquals(beforeData.semesters.size(), after.semesters.size());
        assertEquals(beforeData.tasks.size(), after.tasks.size());
        assertEquals(beforeData.allCourses().get(0).attendance.size(), after.allCourses().get(0).attendance.size());
    }

    @Test
    public void firstLaunchShowsOnboardingAndSavesTheChoice() {
        Repository.get(ctx).replace(new AppData());
        // The main screen hands over to the welcome flow and closes itself.
        MainActivity first = Robolectric.buildActivity(MainActivity.class).setup().get();
        Intent next = shadowOf(first).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(OnboardingActivity.class.getName(), next.getComponent().getClassName());
        assertTrue(first.isFinishing());
        try (ActivityScenario<OnboardingActivity> sc = ActivityScenario.launch(OnboardingActivity.class)) {
            sc.onActivity(a -> {
                idle();
                a.findViewById(R.id.ob_skip).performClick();     // jumps to the mode choice
                idle();
                a.findViewById(R.id.ob_mode_lite).performClick();
                a.findViewById(R.id.ob_next).performClick();     // "Start"
                idle();
                assertTrue(a.isFinishing());
            });
        }
        Settings st = Repository.get(ctx).settings();
        assertTrue(st.onboarded);
        assertEquals(Settings.MODE_LITE, st.appMode);
        // Ordinary launches go straight to the app.
        try (ActivityScenario<MainActivity> sc = ActivityScenario.launch(MainActivity.class)) {
            sc.onActivity(a -> {
                assertFalse(a.isFinishing());
                assertEquals(3, navMenu(a).size());
            });
        }
    }

    @Test
    public void onboardingKeepsItsPageAcrossRecreation() {
        Repository.get(ctx).replace(new AppData());
        // Robolectric does not draw frames, so MotionLayout animations never finish here; with
        // "remove animations" on, the activity switches pages instantly (its reduced-motion path).
        animatorScale(0);
        try (ActivityScenario<OnboardingActivity> sc = ActivityScenario.launch(OnboardingActivity.class)) {
            sc.onActivity(a -> {
                idle();
                a.findViewById(R.id.ob_next).performClick();
                idle();
                a.findViewById(R.id.ob_next).performClick();
                idle();
            });
            sc.recreate();
            sc.onActivity(a -> {
                idle();
                // Third page: back is available and the next button still says "Next".
                assertEquals(android.view.View.VISIBLE, a.findViewById(R.id.ob_back).getVisibility());
                assertEquals(a.getString(R.string.ob_next),
                        ((com.google.android.material.button.MaterialButton) a.findViewById(R.id.ob_next)).getText().toString());
                // Back goes to the previous page instead of leaving.
                a.getOnBackPressedDispatcher().onBackPressed();
                idle();
                assertFalse(a.isFinishing());
                assertEquals(android.view.View.VISIBLE, a.findViewById(R.id.ob_back).getVisibility());
            });
        } finally {
            animatorScale(1);
        }
    }
}
