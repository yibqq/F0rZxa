package app.kuro.anime.ui.search;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.R;
import app.kuro.anime.core.Debouncer;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.databinding.FragmentSearchBinding;
import app.kuro.anime.ui.common.InsetsHelper;
import app.kuro.anime.ui.common.Labels;
import app.kuro.anime.ui.common.Layouts;
import app.kuro.anime.ui.common.MasterDetail;
import app.kuro.anime.ui.common.Navigator;
import app.kuro.anime.ui.common.PagedAnimeList;
import app.kuro.anime.ui.common.PagedGridBinder;
import app.kuro.anime.ui.common.ScrollableTab;

/** Debounced search with progressive results, local history and a format filter. */
public class SearchFragment extends Fragment implements ScrollableTab {

    private static final long DEBOUNCE_MS = 380;
    private FragmentSearchBinding b;
    private SearchViewModel vm;
    private PagedGridBinder grid;
    private HistoryAdapter history;
    private MasterDetail masterDetail;
    private final Debouncer debouncer = new Debouncer(DEBOUNCE_MS);
    private boolean restoringText;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentSearchBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        vm = new ViewModelProvider(this).get(SearchViewModel.class);
        InsetsHelper.pad(b.searchRoot, InsetsHelper.TOP);
        if (Layouts.usesRail(c)) {
            InsetsHelper.pad(b.main.searchResults, InsetsHelper.BOTTOM);
            InsetsHelper.pad(b.main.historyList, InsetsHelper.BOTTOM);
        }
        masterDetail = new MasterDetail(this, b.pane == null ? null : b.pane.detailContainer,
                b.pane == null ? null : b.pane.detailPlaceholder);

        grid = new PagedGridBinder(getViewLifecycleOwner(), b.main.searchResults, b.main.searchState, b.main.searchBanner,
                vm.results, this::open, sv -> sv.show(R.drawable.ic_search,
                getString(R.string.search_no_results, vm.text), getString(R.string.search_no_results_hint), null, null), false);

        history = new HistoryAdapter(new HistoryAdapter.Listener() {
            @Override
            public void onPick(String q) {
                setText(q);
                vm.search(q);
                vm.commitToHistory();
                hideKeyboard();
                updateMode();
            }

            @Override public void onRemove(String q) { vm.removeHistory(q); }
        });
        b.main.historyList.setLayoutManager(new LinearLayoutManager(c));
        b.main.historyList.setAdapter(history);
        b.main.historyClear.setOnClickListener(v -> vm.clearHistory());

        List<String> formats = new ArrayList<>();
        for (String f : SearchViewModel.FORMATS) formats.add(f == null ? getString(R.string.filter_any) : Labels.format(c, f));
        b.main.searchFormat.setItems(formats);
        b.main.searchFormat.setSelectedIndex(vm.formatIndex, false);
        b.main.searchFormat.setOnSegmentSelected(i -> {
            vm.formatIndex = i;
            vm.search(vm.text);
        });

        setText(vm.text);
        b.main.searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (restoringText) return;
                String text = s.toString();
                b.main.searchClear.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
                if (text.trim().length() < 2) {
                    debouncer.cancel();
                    vm.search(text);
                    updateMode();
                } else {
                    debouncer.submit(() -> {
                        vm.search(text);
                        updateMode();
                    });
                }
            }
        });
        b.main.searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                debouncer.cancel();
                vm.search(v.getText().toString());
                vm.commitToHistory();
                hideKeyboard();
                updateMode();
                return true;
            }
            return false;
        });
        b.main.searchClear.setOnClickListener(v -> {
            setText("");
            vm.search("");
            updateMode();
            b.main.searchInput.requestFocus();
        });

        vm.history().observe(getViewLifecycleOwner(), list -> {
            history.submit(list);
            updateMode();
        });
        vm.results.state().observe(getViewLifecycleOwner(), s -> updateMode());
        if (vm.selectedKey == null) masterDetail.showPlaceholder();
    }

    private void open(Anime a) {
        vm.commitToHistory();
        vm.selectedKey = a.key();
        if (!masterDetail.show(a)) Navigator.openDetails(requireContext(), a);
    }

    /** Shows history/prompt when the query is empty, results otherwise. */
    private void updateMode() {
        if (b == null) return;
        boolean idle = vm.text.length() < 2;
        List<String> h = vm.history().getValue();
        boolean hasHistory = h != null && !h.isEmpty();
        b.main.historyGroup.setVisibility(idle && hasHistory ? View.VISIBLE : View.GONE);
        b.main.searchResults.setVisibility(idle ? View.GONE : View.VISIBLE);
        if (idle) {
            if (hasHistory) b.main.searchState.hide();
            else b.main.searchState.show(R.drawable.ic_search, getString(R.string.search_prompt_title),
                    getString(R.string.search_prompt_body), null, null);
            b.main.searchBanner.setVisibility(View.GONE);
        } else {
            PagedAnimeList.State s = vm.results.state().getValue();
            if (s == null) b.main.searchState.hide();
        }
    }

    private void setText(String t) {
        restoringText = true;
        b.main.searchInput.setText(t);
        b.main.searchInput.setSelection(t.length());
        b.main.searchClear.setVisibility(t.isEmpty() ? View.GONE : View.VISIBLE);
        restoringText = false;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(b.main.searchInput.getWindowToken(), 0);
        b.main.searchInput.clearFocus();
    }

    @Override
    public void scrollToTop() {
        if (b == null) return;
        if (vm.text.length() >= 2 && grid != null) grid.scrollToTop();
        else {
            b.main.searchInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(b.main.searchInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public void onDestroyView() {
        debouncer.cancel();
        super.onDestroyView();
        b = null;
        grid = null;
    }
}
