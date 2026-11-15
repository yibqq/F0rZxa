package com.mizan.gpa.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.mizan.gpa.R;

/** Settings as a screen of its own (Standard mode); the content is {@link SettingsFragment}. */
public class SettingsActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupChrome(true);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commitNow();
        }
    }

    @Override
    protected void render() {}
}
