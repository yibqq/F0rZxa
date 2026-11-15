package app.kuro.anime.ui.tv;

import androidx.annotation.Nullable;

/** Implemented by TV activities that show an ambient backdrop for the focused title. */
public interface TvBackdropHost {
    void showBackdrop(@Nullable String url, @Nullable String color);
}
