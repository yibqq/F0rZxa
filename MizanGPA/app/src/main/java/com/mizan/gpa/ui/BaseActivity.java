package com.mizan.gpa.ui;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Repository;
import com.mizan.gpa.util.Ui;

/** Edge-to-edge activity that re-renders whenever the stored data changes. */
public abstract class BaseActivity extends AppCompatActivity implements Repository.Listener {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
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
