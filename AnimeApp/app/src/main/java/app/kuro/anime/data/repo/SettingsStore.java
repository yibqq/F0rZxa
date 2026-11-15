package app.kuro.anime.data.repo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import app.kuro.anime.data.model.TitleLanguage;

/** User preferences. Language itself is stored by AppCompat's per-app locale support. */
public final class SettingsStore {
    private static final String TITLE_LANG = "title_language";
    private static final String DATA_SAVER = "data_saver";
    private static final String COMPACT_GRID = "compact_grid";
    private static final String HIDE_SPOILERS = "hide_spoilers";

    private final SharedPreferences prefs;
    private final MutableLiveData<Long> changes = new MutableLiveData<>(0L);

    public SettingsStore(Context context) {
        prefs = context.getSharedPreferences("kuro_settings", Context.MODE_PRIVATE);
    }

    public LiveData<Long> changes() { return changes; }

    public TitleLanguage titleLanguage() {
        try {
            return TitleLanguage.valueOf(prefs.getString(TITLE_LANG, TitleLanguage.ROMAJI.name()));
        } catch (IllegalArgumentException e) {
            return TitleLanguage.ROMAJI;
        }
    }

    public void setTitleLanguage(TitleLanguage l) { put(TITLE_LANG, l.name()); }

    public boolean dataSaver() { return prefs.getBoolean(DATA_SAVER, false); }

    public void setDataSaver(boolean v) { put(DATA_SAVER, v); }

    public boolean compactGrid() { return prefs.getBoolean(COMPACT_GRID, false); }

    public void setCompactGrid(boolean v) { put(COMPACT_GRID, v); }

    public boolean hideSpoilers() { return prefs.getBoolean(HIDE_SPOILERS, false); }

    public void setHideSpoilers(boolean v) { put(HIDE_SPOILERS, v); }

    private void put(String k, String v) {
        prefs.edit().putString(k, v).apply();
        changes.setValue(System.nanoTime());
    }

    private void put(String k, boolean v) {
        prefs.edit().putBoolean(k, v).apply();
        changes.setValue(System.nanoTime());
    }
}
