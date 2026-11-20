package com.gpacalculator.ui;

import android.app.Activity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gpacalculator.R;
import com.gpacalculator.data.HistoryRepository;
import com.gpacalculator.model.HistoryRecord;
import com.gpacalculator.ui.util.Dimens;
import com.gpacalculator.ui.util.Haptics;
import com.gpacalculator.ui.util.Motion;
import com.gpacalculator.ui.util.SystemInsets;

/** The History tab: saved calculations, newest first, with swipe-to-delete and undo. */
final class HistoryScreen implements HistoryRepository.Listener {
    private final Activity activity;
    private final HistoryRepository repository;
    private final Banner banner;
    private final RecyclerView list;
    private final LinearLayoutManager layoutManager;
    private final HistoryAdapter adapter;
    private final View empty;
    private final NavBar navBar;
    private boolean loaded;

    HistoryScreen(Activity activity, View root, HistoryRepository repository, Banner banner) {
        this.activity = activity;
        this.repository = repository;
        this.banner = banner;

        list = root.findViewById(R.id.history_list);
        empty = root.findViewById(R.id.history_empty);
        navBar = new NavBar(root.findViewById(R.id.history_top_bar));
        navBar.title.setText(R.string.tab_history);

        layoutManager = new LinearLayoutManager(activity);
        adapter = new HistoryAdapter(activity, record -> DetailActivity.open(activity, record.id));
        list.setLayoutManager(layoutManager);
        list.setAdapter(adapter);
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        animator.setRemoveDuration(Motion.NORMAL);
        animator.setMoveDuration(Motion.NORMAL);
        list.setItemAnimator(animator);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateNavBar();
            }
        });
        new ItemTouchHelper(new SwipeToDeleteCallback(activity, this::deleteAt)).attachToRecyclerView(list);

        repository.addListener(this);
        reload();
    }

    @Override
    public void onHistoryChanged() {
        reload();
    }

    private void reload() {
        repository.loadAll(records -> {
            adapter.submit(records);
            loaded = true;
            updateEmptyState();
            list.post(this::updateNavBar);
        });
    }

    private void deleteAt(int adapterPosition) {
        HistoryRecord record = adapter.recordAt(adapterPosition);
        if (record == null) return;
        adapter.removeAt(adapterPosition);
        updateEmptyState();
        Haptics.success(list);
        repository.delete(record.id, null);
        banner.show(activity.getString(R.string.record_deleted), activity.getString(R.string.undo),
                () -> repository.save(record, null));
    }

    private void updateEmptyState() {
        boolean isEmpty = loaded && adapter.recordCount() == 0;
        if (isEmpty && empty.getVisibility() != View.VISIBLE) {
            empty.setVisibility(View.VISIBLE);
            empty.setAlpha(0f);
            empty.setTranslationY(Dimens.dpF(activity, 8));
            empty.animate().alpha(1f).translationY(0f).setDuration(Motion.NORMAL)
                    .setInterpolator(Motion.EASE_OUT).start();
        } else if (!isEmpty) {
            empty.animate().cancel();
            empty.setVisibility(View.GONE);
        }
    }

    private void updateNavBar() {
        View header = layoutManager.findViewByPosition(0);
        boolean collapsed = header == null
                || header.getBottom() - Dimens.dp(activity, 14) < navBar.height();
        navBar.setCollapsed(collapsed);
    }

    void applyInsets(SystemInsets insets, int tabBarHeight) {
        navBar.applyInsets(insets);
        list.setPadding(insets.left, navBar.height(), insets.right, tabBarHeight + Dimens.dp(activity, 24));
        empty.setPadding(empty.getPaddingLeft(), navBar.height(), empty.getPaddingRight(), tabBarHeight);
        list.post(this::updateNavBar);
    }

    void scrollToTop() {
        list.smoothScrollToPosition(0);
    }

    void destroy() {
        repository.removeListener(this);
    }
}
