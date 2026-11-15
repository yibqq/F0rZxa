package com.mizan.gpa.ui;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigationrail.NavigationRailView;
import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.export.DataTransfer;
import com.mizan.gpa.notify.Notifier;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

public class MainActivity extends BaseActivity {
    private static final String STATE_TAB = "tab";
    /** Optional tab to open, e.g. from a widget. */
    public static final String EXTRA_TAB = "openTab";
    /** Tab by name ("attendance", "schedule", "tasks", "gpa"), used by launcher shortcuts and notifications. */
    public static final String EXTRA_SHORTCUT = "shortcut";
    /** Standard mode: the full app. */
    private static final int[] TABS_STANDARD = {R.id.tab_home, R.id.tab_gpa, R.id.tab_attendance, R.id.tab_schedule, R.id.tab_tasks};
    /** Lite mode: exactly the GPA calculator, reminders & tasks, and settings. */
    private static final int[] TABS_LITE = {R.id.tab_gpa, R.id.tab_tasks, R.id.tab_settings};
    private static final int[] ALL_TABS = {R.id.tab_home, R.id.tab_gpa, R.id.tab_attendance, R.id.tab_schedule, R.id.tab_tasks, R.id.tab_settings};
    private int[] tabs = TABS_STANDARD;
    private boolean lite;

    private NavigationBarView nav;
    private TextView headerTitle;
    private TextView headerSubtitle;
    private int currentTab = R.id.tab_home;
    private DataTransfer transfer;
    private ActivityResultLauncher<String> notificationPermission;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!data().settings.onboarded) {
            // First launch: the welcome flow ends by choosing the interface mode.
            startActivity(new android.content.Intent(this, OnboardingActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        lite = com.mizan.gpa.data.Settings.MODE_LITE.equals(data().settings.appMode);
        tabs = lite ? TABS_LITE : TABS_STANDARD;
        transfer = new DataTransfer(this);
        notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});
        headerTitle = findViewById(R.id.header_title);
        headerSubtitle = findViewById(R.id.header_subtitle);
        findViewById(R.id.header_settings).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, SettingsActivity.class)));
        findViewById(R.id.header_pdf).setOnClickListener(v -> transfer.exportPdf());
        nav = findViewById(R.id.nav);
        nav.getMenu().clear();
        nav.inflateMenu(lite ? R.menu.nav_lite : R.menu.nav_main);
        // Lite mode keeps the header minimal: settings is a tab, and the PDF report lives in it.
        findViewById(R.id.header_settings).setVisibility(lite ? View.GONE : View.VISIBLE);
        findViewById(R.id.header_pdf).setVisibility(lite ? View.GONE : View.VISIBLE);
        View container = findViewById(R.id.container);
        Ui.padSides(findViewById(R.id.main_root));
        if (nav instanceof NavigationRailView) Ui.padForNavBar(container);

        if (savedInstanceState != null) currentTab = savedInstanceState.getInt(STATE_TAB, tabs[0]);
        else currentTab = requestedTab(getIntent());
        if (!hasTab(currentTab)) currentTab = tabs[0];
        nav.setSelectedItemId(currentTab);
        nav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId(), true);
            return true;
        });
        nav.setOnItemReselectedListener(item -> {
            Fragment f = getSupportFragmentManager().findFragmentByTag(tag(item.getItemId()));
            View v = f == null ? null : f.getView();
            View scroll = v == null ? null : v.findViewById(R.id.scroll);
            if (scroll instanceof androidx.core.widget.NestedScrollView) {
                ((androidx.core.widget.NestedScrollView) scroll).smoothScrollTo(0, 0);
            }
        });
        showTab(currentTab, false);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_TAB, currentTab);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!intent.hasExtra(EXTRA_TAB) && !intent.hasExtra(EXTRA_SHORTCUT)) return;
        int tab = requestedTab(intent);
        if (hasTab(tab)) selectTab(tab);
    }

    private boolean hasTab(int id) {
        for (int t : tabs) if (t == id) return true;
        return false;
    }

    /** The tab an intent (widget, shortcut, notification) asks for, if this mode has it. */
    private int requestedTab(android.content.Intent intent) {
        int tab = intent.getIntExtra(EXTRA_TAB, 0);
        String shortcut = intent.getStringExtra(EXTRA_SHORTCUT);
        if (shortcut != null) {
            switch (shortcut) {
                case "attendance": tab = R.id.tab_attendance; break;
                case "gpa":
                case "forecast": tab = R.id.tab_gpa; break;
                case "tasks":
                case "exams": tab = R.id.tab_tasks; break;
                case "schedule": tab = R.id.tab_schedule; break;
                default: break;
            }
        }
        return tab != 0 && hasTab(tab) ? tab : tabs[0];
    }

    /** Opens reminders & tasks. */
    public void openTasks() {
        selectTab(R.id.tab_tasks);
    }

    public void selectTab(int tabId) {
        nav.setSelectedItemId(tabId);
    }

    private static String tag(int id) {
        return "tab-" + id;
    }

    private Fragment create(int id) {
        if (id == R.id.tab_gpa) return new GpaFragment();
        if (id == R.id.tab_attendance) return new AttendanceFragment();
        if (id == R.id.tab_tasks) return new TasksFragment();
        if (id == R.id.tab_settings) return new SettingsFragment();
        if (id == R.id.tab_schedule) return new ScheduleFragment();
        return new DashboardFragment();
    }

    private void showTab(int id, boolean animate) {
        currentTab = id;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();
        if (animate) tx.setCustomAnimations(R.anim.tab_enter, R.anim.tab_exit);
        for (int t : ALL_TABS) {
            Fragment f = fm.findFragmentByTag(tag(t));
            if (t == id) {
                if (f == null) tx.add(R.id.container, create(t), tag(t));
                else tx.show(f);
            } else if (f != null && !f.isHidden()) {
                tx.hide(f);
            }
        }
        tx.setReorderingAllowed(true).commitNow();
        updateTitle();
        View appbar = findViewById(R.id.appbar);
        if (appbar instanceof com.google.android.material.appbar.AppBarLayout) {
            Fragment f = fm.findFragmentByTag(tag(id));
            View scroll = f != null && f.getView() != null ? f.getView().findViewById(R.id.scroll) : null;
            if (scroll != null) ((com.google.android.material.appbar.AppBarLayout) appbar).setLiftOnScrollTargetView(scroll);
        }
    }

    private void updateTitle() {
        AppData d = data();
        Semester cur = d.currentSemester();
        int title;
        int icon;
        if (currentTab == R.id.tab_gpa) { title = R.string.title_gpa; icon = R.drawable.ic_school; }
        else if (currentTab == R.id.tab_attendance) { title = R.string.title_attendance; icon = R.drawable.ic_event_available; }
        else if (currentTab == R.id.tab_tasks) { title = R.string.title_tasks; icon = R.drawable.ic_notifications; }
        else if (currentTab == R.id.tab_settings) { title = R.string.settings; icon = R.drawable.ic_settings; }
        else if (currentTab == R.id.tab_schedule) { title = R.string.title_schedule; icon = R.drawable.ic_calendar; }
        else { title = R.string.app_name; icon = 0; }
        // In Lite mode the first tab carries the app's name and logo.
        if (lite && currentTab == tabs[0]) icon = 0;
        headerTitle.setText(title);

        // Logo on the home tab; the section icon on a tonal tile elsewhere.
        View logo = findViewById(R.id.header_logo);
        ImageView logoIcon = findViewById(R.id.header_logo_icon);
        logo.setClipToOutline(true);
        if (icon == 0) {
            com.mizan.gpa.util.Themes.brandTile(logo, logoIcon);
        } else {
            int tone = Ui.attr(this, com.google.android.material.R.attr.colorSecondaryContainer);
            logo.setBackground(new com.mizan.gpa.util.SquircleDrawable(tone, tone));
            logoIcon.setImageResource(icon);
            logoIcon.setImageTintList(Ui.csl(Ui.attr(this, com.google.android.material.R.attr.colorOnSecondaryContainer)));
            int pad = Ui.dp(this, 11);
            logoIcon.setPadding(pad, pad, pad, pad);
            logoIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logoIcon.setScaleX(1f);
            logoIcon.setScaleY(1f);
        }

        String subtitle;
        if (lite && currentTab == tabs[0]) {
            headerTitle.setText(R.string.app_name);
        }
        if (currentTab == R.id.tab_home) {
            String name = d.settings.studentName == null ? "" : d.settings.studentName.trim();
            String today = Fmt.dayName(this, Fmt.toCalendarDay(java.time.LocalDate.now().getDayOfWeek()), false)
                    + " " + Fmt.dateShort(this, java.time.LocalDate.now());
            subtitle = name.isEmpty() ? today : getString(R.string.hello_name, name) + " · " + today;
        } else if (currentTab == R.id.tab_gpa) {
            subtitle = lite ? getString(R.string.title_gpa) : getResources().getQuantityString(R.plurals.semesters_count, d.semesters.size(), d.semesters.size());
        } else if (currentTab == R.id.tab_tasks || currentTab == R.id.tab_settings) {
            subtitle = "";
        } else {
            subtitle = cur != null ? cur.name : "";
        }
        headerSubtitle.setText(subtitle);
        headerSubtitle.setVisibility(subtitle.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void render() {
        updateTitle();
        maybeAskNotifications();
    }

    private void maybeAskNotifications() {
        AppData d = data();
        if (Build.VERSION.SDK_INT < 33 || d.settings.notificationsAsked || d.semesters.isEmpty()) return;
        if (Notifier.canPost(this)) return;
        d.settings.notificationsAsked = true;
        repo().save();
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }
}
