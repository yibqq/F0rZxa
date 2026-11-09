package com.mizan.gpa.ui;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigationrail.NavigationRailView;
import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.export.DataTransfer;
import com.mizan.gpa.notify.Notifier;
import com.mizan.gpa.util.Ui;

public class MainActivity extends BaseActivity {
    private static final String STATE_TAB = "tab";
    private static final int[] TABS = {R.id.tab_home, R.id.tab_semesters, R.id.tab_attendance, R.id.tab_forecast, R.id.tab_schedule};

    private NavigationBarView nav;
    private MaterialToolbar toolbar;
    private int currentTab = R.id.tab_home;
    private DataTransfer transfer;
    private ActivityResultLauncher<String> notificationPermission;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        transfer = new DataTransfer(this);
        notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});
        toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.main);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new android.content.Intent(this, SettingsActivity.class));
                return true;
            } else if (item.getItemId() == R.id.action_export_pdf) {
                transfer.exportPdf();
                return true;
            }
            return false;
        });
        nav = findViewById(R.id.nav);
        View container = findViewById(R.id.container);
        Ui.padSides(findViewById(R.id.main_root));
        if (nav instanceof NavigationRailView) Ui.padForNavBar(container);

        if (savedInstanceState != null) currentTab = savedInstanceState.getInt(STATE_TAB, R.id.tab_home);
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
        if (currentTab == R.id.tab_semesters) title = R.string.tab_semesters;
        else if (currentTab == R.id.tab_attendance) title = R.string.title_attendance;
        else if (currentTab == R.id.tab_forecast) title = R.string.title_forecast;
        else if (currentTab == R.id.tab_schedule) title = R.string.title_schedule;
        else title = R.string.app_name;
        toolbar.setTitle(title);
        if (currentTab == R.id.tab_home) {
            String name = d.settings.studentName == null ? "" : d.settings.studentName.trim();
            toolbar.setSubtitle(name.isEmpty() ? null : getString(R.string.hello_name, name));
        } else if (currentTab != R.id.tab_semesters && cur != null) {
            toolbar.setSubtitle(cur.name);
        } else {
            toolbar.setSubtitle(null);
        }
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
