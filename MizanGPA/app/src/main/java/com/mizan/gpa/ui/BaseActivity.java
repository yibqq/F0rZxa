package com.mizan.gpa.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.util.Fonts;
import com.mizan.gpa.util.Themes;
import com.mizan.gpa.util.Ui;

/** Edge-to-edge activity that re-renders whenever the stored data changes. */
public abstract class BaseActivity extends AppCompatActivity implements Repository.Listener {
    private String styleKey;

    @Override
    protected void attachBaseContext(Context base) {
        // Text size preference, on top of the system font size.
        float scale = Repository.get(base).settings().fontScale;
        if (Math.abs(scale - 1f) > 0.001f) {
            Configuration c = new Configuration(base.getResources().getConfiguration());
            c.fontScale = c.fontScale * scale;
            base = base.createConfigurationContext(c);
        }
        super.attachBaseContext(base);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        Settings st = Repository.get(this).settings();
        Themes.apply(this, st);
        // EdgeToEdge created the window before the palette overlay was applied, so its background still
        // comes from the base theme. Repaint it from the themed background color.
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                Ui.attr(this, android.R.attr.colorBackground)));
        styleKey = Themes.styleKey(st);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        View decor = getWindow().getDecorView();
        // New views (lists, snackbars, toolbar titles) get the app font as soon as they are laid out.
        decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> Fonts.applyTree(decor));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Font, palette or text size changed in Settings while this screen was in the back stack.
        if (!Themes.styleKey(repo().settings()).equals(styleKey)) recreate();
    }

    protected Repository repo() {
        return Repository.get(this);
    }

    protected AppData data() {
        return repo().data();
    }

    /** Sets up a toolbar with an up arrow, and pads scroll content / FAB for the navigation bar. */
    protected MaterialToolbar setupChrome(boolean up) {
        MaterialToolbar tb = findViewById(R.id.toolbar);
        if (tb != null && up) tb.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        android.view.View scroll = findViewById(R.id.scroll);
        if (scroll != null) Ui.padForNavBar(scroll);
        android.view.View fab = findViewById(R.id.fab);
        if (fab != null) Ui.liftForNavBar(fab);
        android.view.View root = findViewById(R.id.snack_anchor_root);
        if (root != null) Ui.padSides(root);
        return tb;
    }

    @Override
    protected void onStart() {
        super.onStart();
        repo().addListener(this);
        render();
    }

    @Override
    protected void onStop() {
        super.onStop();
        repo().removeListener(this);
    }

    @Override
    public void onDataChanged() {
        if (!isFinishing()) render();
    }

    protected abstract void render();
}
