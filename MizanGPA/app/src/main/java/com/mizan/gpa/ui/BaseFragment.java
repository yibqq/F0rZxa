package com.mizan.gpa.ui;

import androidx.fragment.app.Fragment;

import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Repository;

/** A fragment that renders on start and again whenever the stored data changes. */
public abstract class BaseFragment extends Fragment implements Repository.Listener {
    protected Repository repo() {
        return Repository.get(requireContext());
    }

    protected AppData data() {
        return repo().data();
    }

    @Override
    public void onStart() {
        super.onStart();
        repo().addListener(this);
        render();
    }

    @Override
    public void onStop() {
        super.onStop();
        repo().removeListener(this);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && isAdded()) render();
    }

    @Override
    public void onDataChanged() {
        if (isAdded() && getView() != null && !isHidden()) render();
    }

    protected abstract void render();
}
