package app.kuro.anime.data.model;

import androidx.annotation.Nullable;

public enum WatchStatus {
    WATCHING, PLANNING, COMPLETED, PAUSED;

    @Nullable
    public static WatchStatus parse(@Nullable String s) {
        if (s == null) return null;
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
