package app.kuro.anime.ui.details;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import app.kuro.anime.R;
import app.kuro.anime.databinding.ActivityDetailsBinding;

public class DetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ActivityDetailsBinding b = ActivityDetailsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        if (savedInstanceState == null) {
            DetailsFragment f = new DetailsFragment();
            Bundle args = getIntent().getExtras() != null ? new Bundle(getIntent().getExtras()) : new Bundle();
            args.putBoolean(DetailsFragment.ARG_EMBEDDED, false);
            f.setArguments(args);
            getSupportFragmentManager().beginTransaction().replace(R.id.details_container, f).commit();
        }
    }
}
