package com.mizan.gpa;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.notify.Notifier;
import com.mizan.gpa.notify.ReminderScheduler;

public class MizanApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Repository repo = Repository.get(this);
        applyTheme(repo.settings().themeMode);
        DynamicColors.applyToActivitiesIfAvailable(this, new DynamicColorsOptions.Builder()
                .setPrecondition((activity, theme) -> Repository.get(activity).settings().dynamicColor)
                .build());
        Notifier.createChannels(this);
        ReminderScheduler.scheduleNext(this);
    }

    public static void applyTheme(int mode) {
        switch (mode) {
            case Settings.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case Settings.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
