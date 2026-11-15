package app.kuro.anime.data.model;

import androidx.annotation.Nullable;

public final class Episode {
    public final int number;
    @Nullable public String title;
    @Nullable public String titleAlt;      // romanized / Japanese title when available
    @Nullable public FuzzyDate airDate;
    @Nullable public String thumbnailUrl;  // only real episode stills, never the series cover
    public int lengthMinutes;
    public boolean filler;
    public boolean recap;
    public boolean watched;

    public Episode(int number) { this.number = number; }

    public boolean hasDetails() { return title != null || airDate != null || thumbnailUrl != null; }
}
