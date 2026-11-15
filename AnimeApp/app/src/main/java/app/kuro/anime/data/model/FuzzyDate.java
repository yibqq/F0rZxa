package app.kuro.anime.data.model;

import androidx.annotation.Nullable;

/** A possibly partial date (AniList often knows only the year or year + month). */
public final class FuzzyDate {
    public final int year, month, day;

    public FuzzyDate(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public boolean isEmpty() { return year <= 0; }

    @Nullable
    public static FuzzyDate parseIso(@Nullable String iso) {
        if (iso == null || iso.length() < 4) return null;
        try {
            int y = Integer.parseInt(iso.substring(0, 4));
            int m = iso.length() >= 7 ? Integer.parseInt(iso.substring(5, 7)) : 0;
            int d = iso.length() >= 10 ? Integer.parseInt(iso.substring(8, 10)) : 0;
            return new FuzzyDate(y, m, d);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
