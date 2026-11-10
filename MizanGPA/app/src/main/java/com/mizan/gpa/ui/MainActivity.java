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
    private static final int[] TABS = {R.id.tab_home, R.id.tab_semesters, R.id.tab_attendance, R.id.tab_forecast, R.id.tab_schedule};

    private NavigationBarView nav;
    private TextView headerTitle;
    private TextView headerSubtitle;
    private int currentTab = R.id.tab_home;
    private DataTransfer transfer;
    private ActivityResultLauncher<String> notificationPermission;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        transfer = new DataTransfer(this);
        notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});
        headerTitle = findViewById(R.id.header_title);
        headerSubtitle = findViewById(R.id.header_subtitle);
        findViewById(R.id.header_settings).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, SettingsActivity.class)));
        findViewById(R.id.header_pdf).setOnClickListener(v -> transfer.exportPdf());
        nav = findViewById(R.id.nav);
        View container = findViewById(R.id.container);
        Ui.padSides(findViewById(R.id.main_root));
        if (nav instanceof NavigationRailView) Ui.padForNavBar(container);

        if (savedInstanceState != null) currentTab = savedInstanceState.getInt(STATE_TAB, R.id.tab_home);
        else currentTab = getIntent().getIntExtra(EXTRA_TAB, R.id.tab_home);
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
        int tab = intent.getIntExtra(EXTRA_TAB, 0);
        if (tab != 0) selectTab(tab);
    }

    public void selectTab(int tabId) {
        nav.setSelectedItemId(tabId);
    }

    private static String tag(int id) {
        return "tab-" + id;
    }

    private Fragment create(int id) {
        if (id == R.id.tab_semesters) return new SemestersFragment();
        if (id == R.id.tab_attendance) return new AttendanceFragment();
        if (id == R.id.tab_forecast) return new ForecastFragment();
        if (id == R.id.tab_schedule) return new ScheduleFragment();
        return new DashboardFragment();
    }

    private void showTab(int id, boolean animate) {
        currentTab = id;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();
        if (animate) tx.setCustomAnimations(R.anim.tab_enter, R.anim.tab_exit);
        for (int t : TABS) {
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
        if (currentTab == R.id.tab_semesters) { title = R.string.tab_semesters; icon = R.drawable.ic_school; }
        else if (currentTab == R.id.tab_attendance) { title = R.string.title_attendance; icon = R.drawable.ic_event_available; }
        else if (currentTab == R.id.tab_forecast) { title = R.string.title_forecast; icon = R.drawable.ic_trending_up; }
        else if (currentTab == R.id.tab_schedule) { title = R.string.title_schedule; icon = R.drawable.ic_calendar; }
        else { title = R.string.app_name; icon = 0; }
        headerTitle.setText(title);

        // Logo on the home tab; the section icon on a tonal tile elsewhere.
        View logo = findViewById(R.id.header_logo);
        ImageView logoIcon = findViewById(R.id.header_logo_icon);
        logo.setClipToOutline(true);
        if (icon == 0) {
            int primary = Ui.attr(this, androidx.appcompat.R.attr.colorPrimary);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    new int[]{primary, Ui.blend(primary, Ui.attr(this, com.google.android.material.R.attr.colorTertiary), 0.6f)});
            g.setCornerRadius(Ui.dp(this, 15));
            logo.setBackground(g);
            logoIcon.setImageResource(R.drawable.ic_launcher_foreground);
            logoIcon.setImageTintList(null);
            logoIcon.setPadding(0, 0, 0, 0);
            logoIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            Ui.roundedFill(logo, Ui.attr(this, com.google.android.material.R.attr.colorSecondaryContainer), 15);
            logoIcon.setImageResource(icon);
            logoIcon.setImageTintList(Ui.csl(Ui.attr(this, com.google.android.material.R.attr.colorOnSecondaryContainer)));
            int pad = Ui.dp(this, 11);
            logoIcon.setPadding(pad, pad, pad, pad);
            logoIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }

        String subtitle;
        if (currentTab == R.id.tab_home) {
            String name = d.settings.studentName == null ? "" : d.settings.studentName.trim();
            String today = Fmt.dayName(this, Fmt.toCalendarDay(java.time.LocalDate.now().getDayOfWeek()), false)
                    + " " + Fmt.dateShort(this, java.time.LocalDate.now());
            subtitle = name.isEmpty() ? today : getString(R.string.hello_name, name) + " · " + today;
        } else if (currentTab == R.id.tab_semesters) {
            subtitle = getResources().getQuantityString(R.plurals.semesters_count, d.semesters.size(), d.semesters.size());
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
