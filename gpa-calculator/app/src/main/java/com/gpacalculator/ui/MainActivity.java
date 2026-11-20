package com.gpacalculator.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.gpacalculator.R;
import com.gpacalculator.data.HistoryRepository;
import com.gpacalculator.ui.util.Dimens;
import com.gpacalculator.ui.util.EdgeToEdge;
import com.gpacalculator.ui.util.Haptics;
import com.gpacalculator.ui.util.Motion;
import com.gpacalculator.ui.util.PressEffect;
import com.gpacalculator.ui.util.SystemInsets;

/** Hosts the two tabs and the shared tab bar. */
public final class MainActivity extends Activity {
    private static final String KEY_TAB = "selected_tab";
    private static final int TAB_CALCULATOR = 0;
    private static final int TAB_HISTORY = 1;

    private CalculatorScreen calculator;
    private HistoryScreen history;
    private Banner banner;
    private View tabBar;
    private View[] screens;
    private View[] tabs;
    private ImageView[] tabIcons;
    private TextView[] tabLabels;
    private int currentTab = -1;

    /** {@code OnBackInvokedCallback} on API 33+, held as Object so older runtimes never resolve the type. */
    private Object backCallback;
    private boolean backCallbackRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.apply(getWindow());
        setContentView(R.layout.activity_main);

        HistoryRepository repository = HistoryRepository.get(this);
        banner = new Banner(findViewById(R.id.banner));
        View calculatorView = findViewById(R.id.screen_calculator);
        View historyView = findViewById(R.id.screen_history);
        calculator = new CalculatorScreen(this, calculatorView, repository, banner);
        history = new HistoryScreen(this, historyView, repository, banner);

        screens = new View[]{calculatorView, historyView};
        tabs = new View[]{findViewById(R.id.tab_calculator), findViewById(R.id.tab_history)};
        tabIcons = new ImageView[]{findViewById(R.id.tab_calculator_icon), findViewById(R.id.tab_history_icon)};
        tabLabels = new TextView[]{findViewById(R.id.tab_calculator_label), findViewById(R.id.tab_history_label)};
        for (int i = 0; i < tabs.length; i++) {
            int index = i;
            tabs[i].setOnClickListener(v -> selectTab(index, true));
            tabs[i].setContentDescription(tabLabels[i].getText());
            PressEffect.attach(tabs[i], 0.92f);
        }

        tabBar = findViewById(R.id.tab_bar);
        int tabBarContent = getResources().getDimensionPixelSize(R.dimen.tab_bar_height);
        findViewById(R.id.root).setOnApplyWindowInsetsListener((v, insets) -> {
            SystemInsets in = SystemInsets.from(insets);
            tabBar.setPadding(in.left, 0, in.right, in.bottom);
            int tabBarHeight = tabBarContent + in.bottom;
            calculator.applyInsets(in, tabBarHeight);
            history.applyInsets(in, tabBarHeight);
            banner.setBottomOffset(tabBarHeight);
            return insets;
        });

        int initial = savedInstanceState != null ? savedInstanceState.getInt(KEY_TAB, TAB_CALCULATOR) : TAB_CALCULATOR;
        selectTab(initial, false);
    }

    private void selectTab(int index, boolean animate) {
        if (index == currentTab) {
            if (index == TAB_HISTORY) history.scrollToTop();
            else calculator.scrollToTop();
            return;
        }
        if (currentTab == TAB_CALCULATOR) calculator.onHidden();
        int previous = currentTab;
        currentTab = index;

        int tint = getColor(R.color.tint);
        int inactive = getColor(R.color.tab_inactive);
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == index;
            tabIcons[i].setImageTintList(ColorStateList.valueOf(selected ? tint : inactive));
            tabLabels[i].setTextColor(selected ? tint : inactive);
            tabs[i].setSelected(selected);
        }

        View in = screens[index];
        for (int i = 0; i < screens.length; i++) {
            if (i != index) {
                screens[i].animate().cancel();
                screens[i].setVisibility(View.GONE);
            }
        }
        in.animate().cancel();
        in.setVisibility(View.VISIBLE);
        if (animate && previous >= 0) {
            in.setAlpha(0f);
            in.setTranslationY(Dimens.dpF(this, 6));
            in.animate().alpha(1f).translationY(0f).setDuration(Motion.NORMAL)
                    .setInterpolator(Motion.EASE_OUT).start();
            PressEffect.pop(tabIcons[index], 0.8f);
            Haptics.selection(tabs[index]);
        } else {
            in.setAlpha(1f);
            in.setTranslationY(0f);
        }
        updateBackHandling();
    }

    /** From History, Back returns to Calculator before leaving the app. */
    private void updateBackHandling() {
        if (Build.VERSION.SDK_INT < 33) return;
        OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
        if (backCallback == null) {
            backCallback = (OnBackInvokedCallback) () -> selectTab(TAB_CALCULATOR, true);
        }
        boolean want = currentTab == TAB_HISTORY;
        if (want && !backCallbackRegistered) {
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    (OnBackInvokedCallback) backCallback);
            backCallbackRegistered = true;
        } else if (!want && backCallbackRegistered) {
            dispatcher.unregisterOnBackInvokedCallback((OnBackInvokedCallback) backCallback);
            backCallbackRegistered = false;
        }
    }

    /** Pre-Android 13 path; on 13+ {@link #updateBackHandling()} registers an OnBackInvokedCallback. */
    @SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (currentTab == TAB_HISTORY) {
            selectTab(TAB_CALCULATOR, true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TAB, currentTab);
    }

    @Override
    protected void onDestroy() {
        history.destroy();
        super.onDestroy();
    }
}
