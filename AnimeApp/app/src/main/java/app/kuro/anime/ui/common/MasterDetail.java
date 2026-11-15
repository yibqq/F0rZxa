package app.kuro.anime.ui.common;

import android.view.View;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import app.kuro.anime.data.model.Anime;
import app.kuro.anime.ui.details.DetailsFragment;

/**
 * Optional detail pane for list screens. Present only in expanded layouts (layout-w840dp);
 * on smaller windows {@link #show} returns false and the caller opens a full-screen page.
 */
public final class MasterDetail {
    private final Fragment host;
    @Nullable private final View container;
    @Nullable private final View placeholder;

    public MasterDetail(Fragment host, @Nullable View container, @Nullable View placeholder) {
        this.host = host;
        this.container = container;
        this.placeholder = placeholder;
    }

    public boolean isActive() { return container != null; }

    public boolean show(Anime a) {
        if (container == null) return false;
        host.getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(container.getId(), DetailsFragment.newInstance(a, true))
                .commit();
        if (placeholder != null) placeholder.setVisibility(View.GONE);
        return true;
    }

    public void showPlaceholder() {
        if (container == null) return;
        boolean hasChild = host.getChildFragmentManager().findFragmentById(container.getId()) != null;
        if (placeholder != null) placeholder.setVisibility(hasChild ? View.GONE : View.VISIBLE);
    }
}
