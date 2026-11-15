package app.kuro.anime.ui.browse;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import app.kuro.anime.R;
import app.kuro.anime.databinding.ActivityBrowseBinding;
import app.kuro.anime.ui.common.InsetsHelper;
import app.kuro.anime.ui.common.Navigator;
import app.kuro.anime.ui.common.PagedGridBinder;

public class BrowseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ActivityBrowseBinding b = ActivityBrowseBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        InsetsHelper.pad(b.browseRoot, InsetsHelper.TOP | InsetsHelper.START | InsetsHelper.END);
        InsetsHelper.pad(b.browseGrid, InsetsHelper.BOTTOM);
        BrowseViewModel vm = new ViewModelProvider(this).get(BrowseViewModel.class);
        b.browseTitle.setText(vm.title);
        b.browseBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        new PagedGridBinder(this, b.browseGrid, b.browseState, b.browseBanner, vm.list,
                a -> Navigator.openDetails(this, a),
                sv -> sv.show(R.drawable.ic_grid, getString(R.string.empty_generic), null, null, null), false);
    }
}
