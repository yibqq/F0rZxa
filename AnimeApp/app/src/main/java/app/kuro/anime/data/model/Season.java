package app.kuro.anime.data.model;

import java.util.Calendar;
import java.util.TimeZone;

public final class Season {
    public static final String[] ALL = {"WINTER", "SPRING", "SUMMER", "FALL"};

    private Season() {}

    public static final class Current {
        public final String season;
        public final int year;

        public Current(String season, int year) {
            this.season = season;
            this.year = year;
        }
    }

    /** Anime seasons follow the Japanese broadcast calendar: Jan–Mar winter, Apr–Jun spring… */
    public static Current now() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
        return of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    public static Current of(int year, int month) {
        return new Current(ALL[(month - 1) / 3], year);
    }
}
