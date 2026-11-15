package app.kuro.anime.ui.tv;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import app.kuro.anime.R;
import app.kuro.anime.databinding.ActivityBrowseBinding;
import app.kuro.anime.ui.browse.BrowseViewModel;
import app.kuro.anime.ui.common.Navigator;
import app.kuro.anime.ui.common.PagedGridBinder;

/** Full list (e.g. a genre) on TV: focusable poster grid; the remote's Back button returns. */
public class TvBrowseActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityBrowseBinding b = ActivityBrowseBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        int g = getResources().getDimensionPixelSize(R.dimen.gutter);
        b.browseRoot.setPadding(g / 2, g / 2, g / 2, 0);
        b.browseBack.setVisibility(View.GONE);
        BrowseViewModel vm = new ViewModelProvider(this).get(BrowseViewModel.class);
        b.browseTitle.setText(vm.title);
        new PagedGridBinder(this, b.browseGrid, b.browseState, b.browseBanner, vm.list,
                a -> Navigator.openDetails(this, a),
                sv -> sv.show(R.drawable.ic_grid, getString(R.string.empty_generic), null, null, null), true);
        TvFocus.request(b.browseGrid);
    }
}
