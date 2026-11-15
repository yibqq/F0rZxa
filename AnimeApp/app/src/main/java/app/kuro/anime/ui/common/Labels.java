package app.kuro.anime.ui.common;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.kuro.anime.R;
import app.kuro.anime.core.AppError;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.FuzzyDate;
import app.kuro.anime.data.model.WatchStatus;

/** Turns API enum values and dates into localized, human text. */
public final class Labels {
    private Labels() {}

    public static String format(Context c, @Nullable String f) {
        if (f == null) return null;
        switch (f) {
            case "TV": return c.getString(R.string.format_tv);
            case "TV_SHORT": return c.getString(R.string.format_tv_short);
            case "MOVIE": return c.getString(R.string.format_movie);
            case "SPECIAL": return c.getString(R.string.format_special);
            case "OVA": return c.getString(R.string.format_ova);
            case "ONA": return c.getString(R.string.format_ona);
            case "MUSIC": return c.getString(R.string.format_music);
            default: return f;
        }
    }

    public static String status(Context c, @Nullable String s) {
        if (s == null) return null;
        switch (s) {
            case "FINISHED": return c.getString(R.string.status_finished);
            case "RELEASING": return c.getString(R.string.status_releasing);
            case "NOT_YET_RELEASED": return c.getString(R.string.status_not_yet_released);
            case "CANCELLED": return c.getString(R.string.status_cancelled);
            case "HIATUS": return c.getString(R.string.status_hiatus);
            default: return s;
        }
    }

    public static String season(Context c, @Nullable String s) {
        if (s == null) return null;
        switch (s) {
            case "WINTER": return c.getString(R.string.season_winter);
            case "SPRING": return c.getString(R.string.season_spring);
            case "SUMMER": return c.getString(R.string.season_summer);
            case "FALL": return c.getString(R.string.season_fall);
            default: return s;
        }
    }

    public static String watchStatus(Context c, WatchStatus s) {
        switch (s) {
            case WATCHING: return c.getString(R.string.status_watching);
            case PLANNING: return c.getString(R.string.status_planning);
            case COMPLETED: return c.getString(R.string.status_completed);
            case PAUSED: default: return c.getString(R.string.status_paused);
        }
    }

    public static String relation(Context c, @Nullable String type) {
        if (type == null) return c.getString(R.string.relation_other);
        switch (type) {
            case "SEQUEL": return c.getString(R.string.relation_sequel);
            case "PREQUEL": return c.getString(R.string.relation_prequel);
            case "SIDE_STORY": return c.getString(R.string.relation_side_story);
            case "PARENT": return c.getString(R.string.relation_parent);
            case "ALTERNATIVE": return c.getString(R.string.relation_alternative);
            case "SPIN_OFF": return c.getString(R.string.relation_spin_off);
            case "SUMMARY": return c.getString(R.string.relation_summary);
            default: return c.getString(R.string.relation_other);
        }
    }

    public static String role(Context c, @Nullable String role) {
        if ("MAIN".equals(role)) return c.getString(R.string.role_main);
        if ("SUPPORTING".equals(role)) return c.getString(R.string.role_supporting);
        if ("BACKGROUND".equals(role)) return c.getString(R.string.role_background);
        return role;
    }

    public static String source(@Nullable String s) {
        if (s == null) return null;
        String t = s.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** Localized label for an AniList genre key; unknown genres are shown as-is. */
    public static String genre(Context c, String key) {
        Resources r = c.getResources();
        String[] keys = r.getStringArray(R.array.genre_keys);
        String[] labels = r.getStringArray(R.array.genre_labels);
        for (int i = 0; i < keys.length && i < labels.length; i++) if (keys[i].equals(key)) return labels[i];
        return key;
    }

    /** "TV · 2023 · 28 episodes" */
    public static String metaLine(Context c, Anime a, boolean withEpisodes) {
        List<String> parts = new ArrayList<>();
        String f = format(c, a.format);
        if (f != null) parts.add(f);
        int year = a.seasonYear > 0 ? a.seasonYear : a.startDate != null ? a.startDate.year : 0;
        if (year > 0) parts.add(String.valueOf(year));
        if (withEpisodes && a.episodes > 1) parts.add(c.getResources().getQuantityString(R.plurals.episodes_count, a.episodes, a.episodes));
        return android.text.TextUtils.join(" · ", parts);
    }

    /** Formats an integer with the UI locale's digits (e.g. Arabic-Indic numerals in Arabic). */
    public static String number(Context c, int n) {
        java.text.NumberFormat f = java.text.NumberFormat.getIntegerInstance(c.getResources().getConfiguration().getLocales().get(0));
        f.setGroupingUsed(false);
        return f.format(n);
    }

    @Nullable
    public static String date(Context c, @Nullable FuzzyDate d) {
        if (d == null || d.isEmpty()) return null;
        Locale locale = c.getResources().getConfiguration().getLocales().get(0);
        try {
            if (d.month > 0 && d.day > 0) {
                return LocalDate.of(d.year, d.month, d.day)
                        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale));
            }
            if (d.month > 0) {
                return LocalDate.of(d.year, d.month, 1).format(DateTimeFormatter.ofPattern("MMM yyyy", locale));
            }
        } catch (RuntimeException ignored) {
            // invalid partial date from the source
        }
        return String.valueOf(d.year);
    }

    public static String dateRange(Context c, @Nullable FuzzyDate from, @Nullable FuzzyDate to) {
        String a = date(c, from);
        String b = date(c, to);
        if (a == null) return null;
        if (b == null || b.equals(a)) return a;
        return a + " – " + b;
    }

    public static String countdown(Context c, long airingAtSec) {
        long secs = Math.max(0, airingAtSec - System.currentTimeMillis() / 1000);
        long days = secs / 86_400;
        long hours = (secs % 86_400) / 3_600;
        long minutes = (secs % 3_600) / 60;
        if (days > 0) return c.getString(R.string.time_days_hours, (int) days, (int) hours);
        if (hours > 0) return c.getString(R.string.time_hours_minutes, (int) hours, (int) minutes);
        return c.getString(R.string.time_minutes, (int) Math.max(1, minutes));
    }

    public static String error(Context c, @Nullable AppError e) {
        if (e == null) return c.getString(R.string.state_unknown);
        switch (e.kind) {
            case OFFLINE: return c.getString(R.string.state_offline_body);
            case TIMEOUT: return c.getString(R.string.state_timeout);
            case RATE_LIMITED: return c.getString(R.string.state_rate_limited);
            case SERVER: return c.getString(R.string.state_server);
            case NOT_FOUND: return c.getString(R.string.state_not_found);
            case PARSE: return c.getString(R.string.state_parse);
            default: return c.getString(R.string.state_unknown);
        }
    }
}
