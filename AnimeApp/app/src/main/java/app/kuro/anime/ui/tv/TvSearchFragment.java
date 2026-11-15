package app.kuro.anime.ui.tv;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import app.kuro.anime.R;
import app.kuro.anime.core.Debouncer;
import app.kuro.anime.databinding.FragmentTvSearchBinding;
import app.kuro.anime.ui.common.Navigator;
import app.kuro.anime.ui.common.PagedGridBinder;
import app.kuro.anime.ui.search.SearchViewModelAccess;

/** TV search: on-screen keyboard input, debounced, with a focusable results grid. */
public class TvSearchFragment extends Fragment {
    private FragmentTvSearchBinding b;
    private final Debouncer debouncer = new Debouncer(450);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentTvSearchBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        SearchViewModelAccess vm = new ViewModelProvider(this).get(SearchViewModelAccess.class);
        new PagedGridBinder(getViewLifecycleOwner(), b.tvSearchGrid, b.tvSearchState, null, vm.results(),
                a -> {
                    vm.commit();
                    Navigator.openDetails(requireContext(), a);
                },
                sv -> sv.show(R.drawable.ic_search, getString(R.string.search_no_results, vm.text()),
                        getString(R.string.search_no_results_hint), null, null), true);
        b.tvSearchInput.setText(vm.text());
        b.tvSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}

            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}

            @Override
            public void afterTextChanged(Editable s) {
                String t = s.toString();
                debouncer.submit(() -> vm.search(t));
            }
        });
        b.tvSearchInput.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                debouncer.cancel();
                vm.search(v.getText().toString());
                vm.commit();
                b.tvSearchGrid.requestFocus();
                return true;
            }
            return false;
        });
        if (vm.text().isEmpty()) {
            b.tvSearchState.show(R.drawable.ic_search, getString(R.string.search_prompt_title),
                    getString(R.string.search_prompt_body), null, null);
        }
    }

    @Override
    public void onDestroyView() {
        debouncer.cancel();
        super.onDestroyView();
        b = null;
    }
}
