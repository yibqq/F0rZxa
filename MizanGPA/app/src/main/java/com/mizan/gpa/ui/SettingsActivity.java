package com.mizan.gpa.ui;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.mizan.gpa.BuildConfig;
import com.mizan.gpa.MizanApp;
import com.mizan.gpa.R;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.export.DataTransfer;
import com.mizan.gpa.export.SampleData;
import com.mizan.gpa.notify.Notifier;
import com.mizan.gpa.notify.ReminderScheduler;
import com.mizan.gpa.util.Fonts;
import com.mizan.gpa.util.Fmt;

public class SettingsActivity extends BaseActivity {
    private DataTransfer transfer;
    private ActivityResultLauncher<String[]> fontPicker;
    private ActivityResultLauncher<String> notificationPermission;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupChrome(true);
        transfer = new DataTransfer(this);
        fontPicker = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            if (uris == null || uris.isEmpty()) return;
            try {
                String id = Fonts.importFonts(this, uris);
                if (id == null) {
                    com.mizan.gpa.util.Ui.snack(this, getString(R.string.font_import_failed));
                    return;
                }
                st().fontId = id;
                save();
                Fonts.invalidate();
                recreate();
            } catch (java.io.IOException e) {
                com.mizan.gpa.util.Ui.snack(this, getString(R.string.font_import_failed));
            }
        });
        notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> render());

        section(R.id.set_section_profile, R.string.section_profile);
        section(R.id.set_section_appearance, R.string.section_appearance);
        section(R.id.set_section_grading, R.string.section_grading);
        section(R.id.set_section_attendance, R.string.section_attendance);
        section(R.id.set_section_reminders, R.string.section_reminders);
        section(R.id.set_section_exams, R.string.section_exams);
        section(R.id.set_section_data, R.string.section_data);
        section(R.id.set_section_about, R.string.section_about);

        row(R.id.set_profile, R.drawable.ic_person, R.string.set_profile, v ->
                Dialogs.texts(this, getString(R.string.set_profile), R.string.student_name, st().studentName,
                        R.string.university, st().university, (a, b) -> {
                            st().studentName = a;
                            st().university = b;
                            save();
                        }));
        row(R.id.set_theme, R.drawable.ic_dark_mode, R.string.set_theme, v -> {
            CharSequence[] items = {getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)};
            Dialogs.choose(this, getString(R.string.set_theme), items, st().themeMode, which -> {
                st().themeMode = which;
                save();
                MizanApp.applyTheme(which);
            });
        });
        row(R.id.set_language, R.drawable.ic_translate, R.string.set_language, v -> {
            CharSequence[] items = {getString(R.string.language_system), "العربية", "English"};
            String cur = AppCompatDelegate.getApplicationLocales().toLanguageTags();
            int checked = cur.startsWith("ar") ? 1 : cur.startsWith("en") ? 2 : 0;
            Dialogs.choose(this, getString(R.string.set_language), items, checked, which -> {
                String tag = which == 1 ? "ar" : which == 2 ? "en" : "";
                AppCompatDelegate.setApplicationLocales(tag.isEmpty() ? LocaleListCompat.getEmptyLocaleList()
                        : LocaleListCompat.forLanguageTags(tag));
            });
        });
        row(R.id.set_dynamic, R.drawable.ic_palette, R.string.set_dynamic, v -> {
            st().dynamicColor = !st().dynamicColor;
            save();
            recreate();
        });
        findViewById(R.id.set_dynamic).setVisibility(DynamicColors.isDynamicColorAvailable() ? View.VISIBLE : View.GONE);
        row(R.id.set_palette, R.drawable.ic_palette, R.string.set_palette, v -> choosePalette());
        row(R.id.set_pure_black, R.drawable.ic_dark_mode, R.string.set_pure_black, v -> {
            st().pureBlack = !st().pureBlack;
            save();
            if (com.mizan.gpa.util.Themes.isNight(this)) recreate();
        });
        row(R.id.set_font, R.drawable.ic_translate, R.string.set_font, v -> chooseFont());
        row(R.id.set_font_size, R.drawable.ic_tune, R.string.set_font_size, v -> {
            float[] values = {0.9f, 1f, 1.15f, 1.3f};
            CharSequence[] items = {getString(R.string.size_small), getString(R.string.size_default),
                    getString(R.string.size_large), getString(R.string.size_larger)};
            int checked = 1;
            for (int i = 0; i < values.length; i++) if (Math.abs(values[i] - st().fontScale) < 0.01f) checked = i;
            Dialogs.choose(this, getString(R.string.set_font_size), items, checked, which -> {
                st().fontScale = values[which];
                save();
                recreate();
            });
        });

        row(R.id.set_scale, R.drawable.ic_star, R.string.grade_scale, v -> startActivity(new Intent(this, GradeScaleActivity.class)));
        row(R.id.set_round, R.drawable.ic_tune, R.string.set_round, v -> {
            st().roundPercent = !st().roundPercent;
            save();
        });
        row(R.id.set_prior, R.drawable.ic_school, R.string.set_prior, v ->
                Dialogs.numbers(this, getString(R.string.set_prior), getString(R.string.set_prior_message),
                        R.string.prior_gpa, st().priorCredits > 0 ? st().priorGpa : Double.NaN,
                        R.string.prior_credits, st().priorCredits > 0 ? st().priorCredits : Double.NaN, true, (gpa, credits) -> {
                            if (Double.isNaN(credits) || credits == 0) {
                                st().priorCredits = 0;
                                st().priorGpa = 0;
                                save();
                                return true;
                            }
                            if (Double.isNaN(gpa) || gpa < 0 || gpa > st().scale.maxPoints() || credits < 0 || credits > 1000) return false;
                            st().priorGpa = gpa;
                            st().priorCredits = credits;
                            save();
                            return true;
                        }));
        row(R.id.set_weeks, R.drawable.ic_calendar, R.string.set_weeks, v ->
                Dialogs.numbers(this, getString(R.string.set_weeks), getString(R.string.set_weeks_message),
                        R.string.teaching_weeks, st().defaultWeeks, 0, Double.NaN, false, (w, x) -> {
                            if (Double.isNaN(w) || w < 1 || w > 52) return false;
                            st().defaultWeeks = (int) Math.round(w);
                            save();
                            return true;
                        }));

        row(R.id.set_abs_limit, R.drawable.ic_event_busy, R.string.set_abs_limit, v ->
                Dialogs.numbers(this, getString(R.string.set_abs_limit), getString(R.string.set_abs_limit_message),
                        R.string.percent, st().absenceLimit, 0, Double.NaN, true, (p, x) -> {
                            if (Double.isNaN(p) || p <= 0 || p > 100) return false;
                            st().absenceLimit = p;
                            save();
                            return true;
                        }));
        row(R.id.set_abs_rule, R.drawable.ic_info, R.string.set_abs_rule, v -> {
            CharSequence[] items = {getString(R.string.rule_reach), getString(R.string.rule_exceed)};
            Dialogs.choose(this, getString(R.string.set_abs_rule), items, st().deprivedOnReach ? 0 : 1, which -> {
                st().deprivedOnReach = which == 0;
                save();
            });
        });
        row(R.id.set_abs_warn, R.drawable.ic_warning, R.string.set_abs_warn, v ->
                Dialogs.numbers(this, getString(R.string.set_abs_warn), getString(R.string.set_abs_warn_message),
                        R.string.absences_label, st().warnRemaining, 0, Double.NaN, false, (n, x) -> {
                            if (Double.isNaN(n) || n < 0 || n > 50) return false;
                            st().warnRemaining = (int) Math.round(n);
                            save();
                            return true;
                        }));
        row(R.id.set_abs_excused, R.drawable.ic_help, R.string.set_abs_excused, v -> {
            st().countExcused = !st().countExcused;
            save();
        });
        row(R.id.set_abs_late, R.drawable.ic_clock, R.string.set_abs_late, v ->
                Dialogs.numbers(this, getString(R.string.set_abs_late), getString(R.string.set_abs_late_message),
                        R.string.lates_count, st().latesPerAbsence, 0, Double.NaN, false, (n, x) -> {
                            if (Double.isNaN(n) || n < 0 || n > 20) return false;
                            st().latesPerAbsence = (int) Math.round(n);
                            save();
                            return true;
                        }));
        row(R.id.set_abs_alerts, R.drawable.ic_notifications, R.string.set_abs_alerts, v -> {
            st().attendanceAlerts = !st().attendanceAlerts;
            save();
            if (st().attendanceAlerts) askNotifications();
        });

        row(R.id.set_rem_enabled, R.drawable.ic_schedule_alarm, R.string.set_rem_enabled, v -> {
            st().remindersEnabled = !st().remindersEnabled;
            save();
            if (st().remindersEnabled) askNotifications();
        });
        row(R.id.set_rem_minutes, R.drawable.ic_clock, R.string.set_rem_minutes, v -> {
            int[] values = {0, 5, 10, 15, 30, 45, 60};
            CharSequence[] items = new CharSequence[values.length];
            int checked = 3;
            for (int i = 0; i < values.length; i++) {
                items[i] = values[i] == 0 ? getString(R.string.at_start_time)
                        : getResources().getQuantityString(R.plurals.minutes_before, values[i], values[i]);
                if (values[i] == st().reminderMinutes) checked = i;
            }
            Dialogs.choose(this, getString(R.string.set_rem_minutes), items, checked, which -> {
                st().reminderMinutes = values[which];
                save();
            });
        });
        row(R.id.set_rem_exact, R.drawable.ic_schedule_alarm, R.string.set_rem_exact, v -> {
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            }
        });

        row(R.id.set_exam_enabled, R.drawable.ic_exam, R.string.set_exam_enabled, v -> {
            st().examReminders = !st().examReminders;
            save();
            if (st().examReminders) askNotifications();
        });
        row(R.id.set_exam_days, R.drawable.ic_calendar, R.string.set_exam_days, v -> chooseOffsets(true));
        row(R.id.set_exam_day_time, R.drawable.ic_clock, R.string.set_exam_day_time, v -> {
            com.google.android.material.timepicker.MaterialTimePicker p = new com.google.android.material.timepicker.MaterialTimePicker.Builder()
                    .setTimeFormat(android.text.format.DateFormat.is24HourFormat(this)
                            ? com.google.android.material.timepicker.TimeFormat.CLOCK_24H
                            : com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
                    .setHour(st().examDayTime / 60).setMinute(st().examDayTime % 60)
                    .setTitleText(R.string.set_exam_day_time)
                    .build();
            p.addOnPositiveButtonClickListener(b -> {
                st().examDayTime = p.getHour() * 60 + p.getMinute();
                save();
            });
            p.show(getSupportFragmentManager(), "exam-day-time");
        });
        row(R.id.set_exam_minutes, R.drawable.ic_schedule_alarm, R.string.set_exam_minutes, v -> chooseOffsets(false));

        row(R.id.set_export_pdf, R.drawable.ic_description, R.string.export_pdf, v -> transfer.exportPdf());
        row(R.id.set_backup, R.drawable.ic_save, R.string.backup, v -> transfer.backup());
        row(R.id.set_restore, R.drawable.ic_restore, R.string.restore, v -> transfer.restore());
        row(R.id.set_sample, R.drawable.ic_lightbulb, R.string.load_sample, v ->
                Dialogs.confirm(this, getString(R.string.load_sample), getString(R.string.load_sample_confirm), R.string.load, () -> {
                    Settings keep = st();
                    repo().replace(SampleData.create(this, keep));
                    com.mizan.gpa.util.Ui.snack(this, getString(R.string.sample_loaded));
                }));
        row(R.id.set_clear, R.drawable.ic_delete, R.string.clear_data, v ->
                Dialogs.confirm(this, getString(R.string.clear_data), getString(R.string.clear_data_confirm), R.string.delete_all, () -> {
                    AppData fresh = new AppData();
                    fresh.settings = st();
                    fresh.settings.currentSemesterId = null;
                    fresh.settings.priorCredits = 0;
                    fresh.settings.priorGpa = 0;
                    fresh.settings.targetGpa = 0;
                    repo().replace(fresh);
                    com.mizan.gpa.util.Ui.snack(this, getString(R.string.data_cleared));
                }));
        row(R.id.set_about, R.drawable.ic_info, R.string.about_title, null);
    }

    /** Multi-choice list of exam reminder offsets, with a button to add a custom value. */
    private void chooseOffsets(boolean days) {
        java.util.List<Integer> selected = new java.util.ArrayList<>(days ? st().examDaysBefore : st().examMinutesBefore);
        java.util.List<Integer> all = new java.util.ArrayList<>();
        for (int o : days ? Exams.DAY_OPTIONS : Exams.MINUTE_OPTIONS) all.add(o);
        for (int x : selected) if (!all.contains(x)) all.add(x);
        all.sort((a, b) -> a - b);
        CharSequence[] labels = new CharSequence[all.size()];
        boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            labels[i] = days ? Exams.dayLabel(this, all.get(i)) : Exams.minuteLabel(this, all.get(i));
            checked[i] = selected.contains(all.get(i));
        }
        Runnable commit = () -> {
            java.util.List<Integer> target = days ? st().examDaysBefore : st().examMinutesBefore;
            target.clear();
            target.addAll(selected);
            save();
        };
        Dialogs.show(new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(days ? R.string.set_exam_days : R.string.set_exam_minutes)
                .setMultiChoiceItems(labels, checked, (d, which, on) -> {
                    selected.remove(all.get(which));
                    if (on) selected.add(all.get(which));
                })
                .setPositiveButton(R.string.save, (d, w) -> commit.run())
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.custom_value, (d, w) -> Dialogs.numbers(this,
                        getString(R.string.custom_value), getString(days ? R.string.custom_days_message : R.string.custom_minutes_message),
                        days ? R.string.days_unit : R.string.minutes_unit, Double.NaN, 0, Double.NaN, false, (n, x) -> {
                            if (Double.isNaN(n) || n < (days ? 1 : 0) || n > (days ? 60 : 24 * 60)) return false;
                            int value = (int) Math.round(n);
                            if (!selected.contains(value)) selected.add(value);
                            commit.run();
                            return true;
                        })));
    }

    private void choosePalette() {
        android.view.View content = getLayoutInflater().inflate(R.layout.dialog_palette, null);
        com.google.android.material.chip.ChipGroup group = content.findViewById(R.id.palettes);
        androidx.appcompat.app.AlertDialog[] dialog = new androidx.appcompat.app.AlertDialog[1];
        for (com.mizan.gpa.util.Themes.Palette p : com.mizan.gpa.util.Themes.PALETTES) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
            chip.setText(p.name);
            chip.setCheckable(true);
            chip.setChecked(p.id.equals(st().colorTheme) && !st().dynamicColor);
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(p.swatch);
            dot.setSize(com.mizan.gpa.util.Ui.dp(this, 18), com.mizan.gpa.util.Ui.dp(this, 18));
            chip.setChipIcon(dot);
            chip.setChipIconVisible(true);
            chip.setCheckedIconVisible(false);
            chip.setChipMinHeight(com.mizan.gpa.util.Ui.dp(this, 44));
            chip.setOnClickListener(x -> {
                st().colorTheme = p.id;
                st().dynamicColor = false;
                save();
                if (dialog[0] != null) dialog[0].dismiss();
                recreate();
            });
            group.addView(chip);
        }
        dialog[0] = Dialogs.show(new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.set_palette)
                .setView(content)
                .setNegativeButton(R.string.cancel, null));
    }

    private void chooseFont() {
        android.widget.LinearLayout list = new android.widget.LinearLayout(this);
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        list.setPadding(0, com.mizan.gpa.util.Ui.dp(this, 8), 0, com.mizan.gpa.util.Ui.dp(this, 8));
        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(this);
        scroll.addView(list);
        androidx.appcompat.app.AlertDialog[] dialog = new androidx.appcompat.app.AlertDialog[1];
        String current = st().fontId;
        String sample = getString(R.string.font_sample);

        java.util.List<Object> entries = new java.util.ArrayList<>();
        entries.add(Fonts.SYSTEM);
        entries.addAll(Fonts.families(this));
        for (Object e : entries) {
            android.view.View row = getLayoutInflater().inflate(R.layout.item_font, list, false);
            TextView name = row.findViewById(R.id.f_name);
            TextView preview = row.findViewById(R.id.f_preview);
            name.setTag(R.id.font_fixed, true);
            preview.setTag(R.id.font_fixed, true);
            String id;
            if (e instanceof Fonts.Family) {
                Fonts.Family f = (Fonts.Family) e;
                id = f.id;
                name.setText(f.imported ? getString(R.string.font_imported_name, f.name) : f.name);
                android.graphics.Typeface[] faces = Fonts.preview(this, f);
                if (faces != null && faces[0] != null) {
                    name.setTypeface(faces[1] != null ? faces[1] : faces[0]);
                    preview.setTypeface(faces[0]);
                }
                if (f.imported) {
                    android.view.View del = row.findViewById(R.id.f_delete);
                    del.setVisibility(View.VISIBLE);
                    del.setOnClickListener(x -> Dialogs.confirm(this, getString(R.string.font_delete_title, f.name),
                            getString(R.string.font_delete_text), R.string.delete, () -> {
                                Fonts.deleteFamily(this, f.id);
                                if (f.id.equals(st().fontId)) {
                                    st().fontId = Fonts.DEFAULT;
                                    save();
                                }
                                if (dialog[0] != null) dialog[0].dismiss();
                                Fonts.invalidate();
                                recreate();
                            }));
                }
            } else {
                id = Fonts.SYSTEM;
                name.setText(R.string.font_system);
                name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                preview.setTypeface(android.graphics.Typeface.DEFAULT);
            }
            preview.setText(sample);
            row.findViewById(R.id.f_check).setVisibility(id.equals(current) ? View.VISIBLE : View.INVISIBLE);
            row.setOnClickListener(x -> {
                if (dialog[0] != null) dialog[0].dismiss();
                if (id.equals(st().fontId)) return;
                st().fontId = id;
                save();
                Fonts.invalidate();
                recreate();
            });
            list.addView(row);
        }
        // Import a font file (any OTF/TTF), with a note about Thmanyah's font.
        android.view.View add = getLayoutInflater().inflate(R.layout.item_font, list, false);
        ((android.widget.ImageView) add.findViewById(R.id.f_check)).setImageResource(R.drawable.ic_add);
        ((TextView) add.findViewById(R.id.f_name)).setText(R.string.font_add);
        ((TextView) add.findViewById(R.id.f_preview)).setText(R.string.font_add_hint);
        add.setOnClickListener(x -> {
            if (dialog[0] != null) dialog[0].dismiss();
            launchFontPicker();
        });
        list.addView(add);
        dialog[0] = Dialogs.show(new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.set_font)
                .setView(scroll)
                .setNegativeButton(R.string.cancel, null));
    }

    private void launchFontPicker() {
        try {
            fontPicker.launch(new String[]{"font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf",
                    "application/x-font-otf", "application/vnd.ms-opentype", "application/font-sfnt", "application/octet-stream"});
        } catch (android.content.ActivityNotFoundException e) {
            com.mizan.gpa.util.Ui.snack(this, getString(R.string.no_file_picker));
        }
    }

    private Settings st() {
        return repo().settings();
    }

    private void save() {
        repo().save();
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void section(int id, @StringRes int title) {
        ((TextView) findViewById(id)).setText(title);
    }

    private void row(int id, @DrawableRes int icon, @StringRes int title, View.OnClickListener click) {
        View r = findViewById(id);
        ((ImageView) r.findViewById(R.id.row_icon)).setImageResource(icon);
        ((TextView) r.findViewById(R.id.row_title)).setText(title);
        r.setOnClickListener(click);
        r.setClickable(click != null);
    }

    private void summary(int id, CharSequence text) {
        TextView t = findViewById(id).findViewById(R.id.row_summary);
        t.setText(text);
        t.setVisibility(text == null || text.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void toggle(int id, boolean on) {
        MaterialSwitch s = findViewById(id).findViewById(R.id.row_switch);
        s.setVisibility(View.VISIBLE);
        s.setChecked(on);
    }

    @Override
    protected void render() {
        Settings st = st();
        String who = (st.studentName + (st.university.isEmpty() ? "" : " · " + st.university)).trim();
        summary(R.id.set_profile, who.isEmpty() ? getString(R.string.set_profile_summary) : who);
        summary(R.id.set_theme, getString(st.themeMode == Settings.THEME_LIGHT ? R.string.theme_light
                : st.themeMode == Settings.THEME_DARK ? R.string.theme_dark : R.string.theme_system));
        String lang = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        summary(R.id.set_language, lang.startsWith("ar") ? "العربية" : lang.startsWith("en") ? "English" : getString(R.string.language_system));
        summary(R.id.set_dynamic, getString(R.string.set_dynamic_summary));
        toggle(R.id.set_dynamic, st.dynamicColor);
        summary(R.id.set_palette, st.dynamicColor ? getString(R.string.set_dynamic)
                : getString(com.mizan.gpa.util.Themes.find(st.colorTheme).name));
        summary(R.id.set_pure_black, getString(R.string.set_pure_black_summary));
        toggle(R.id.set_pure_black, st.pureBlack);
        summary(R.id.set_font, Fonts.displayName(this, st.fontId));
        summary(R.id.set_font_size, getString(st.fontScale < 0.95f ? R.string.size_small : st.fontScale < 1.05f ? R.string.size_default
                : st.fontScale < 1.2f ? R.string.size_large : R.string.size_larger));

        summary(R.id.set_scale, getString(R.string.scale_summary, Labels.presetName(this, st.scale.preset), Fmt.gpa(st.scale.maxPoints())));
        summary(R.id.set_round, getString(R.string.set_round_summary));
        toggle(R.id.set_round, st.roundPercent);
        summary(R.id.set_prior, st.priorCredits > 0
                ? getString(R.string.prior_summary, Fmt.gpa(st.priorGpa), Fmt.num(st.priorCredits))
                : getString(R.string.set_prior_summary));
        summary(R.id.set_weeks, getResources().getQuantityString(R.plurals.weeks, st.defaultWeeks, st.defaultWeeks));

        summary(R.id.set_abs_limit, Fmt.pct(st.absenceLimit));
        summary(R.id.set_abs_rule, getString(st.deprivedOnReach ? R.string.rule_reach : R.string.rule_exceed));
        summary(R.id.set_abs_warn, getResources().getQuantityString(R.plurals.warn_summary, st.warnRemaining, st.warnRemaining));
        summary(R.id.set_abs_excused, getString(R.string.set_abs_excused_summary));
        toggle(R.id.set_abs_excused, st.countExcused);
        summary(R.id.set_abs_late, st.latesPerAbsence > 0
                ? getResources().getQuantityString(R.plurals.late_summary, st.latesPerAbsence, st.latesPerAbsence)
                : getString(R.string.late_not_counted));
        boolean canNotify = Notifier.canPost(this);
        summary(R.id.set_abs_alerts, canNotify ? getString(R.string.set_abs_alerts_summary) : getString(R.string.notifications_blocked));
        toggle(R.id.set_abs_alerts, st.attendanceAlerts);

        summary(R.id.set_rem_enabled, canNotify ? getString(R.string.set_rem_enabled_summary) : getString(R.string.notifications_blocked));
        toggle(R.id.set_rem_enabled, st.remindersEnabled);
        summary(R.id.set_rem_minutes, st.reminderMinutes == 0 ? getString(R.string.at_start_time)
                : getResources().getQuantityString(R.plurals.minutes_before, st.reminderMinutes, st.reminderMinutes));
        findViewById(R.id.set_rem_minutes).setEnabled(st.remindersEnabled);
        findViewById(R.id.set_rem_minutes).setAlpha(st.remindersEnabled ? 1f : 0.5f);
        boolean exact = ReminderScheduler.exactAlarmsAllowed(this);
        findViewById(R.id.set_rem_exact).setVisibility(Build.VERSION.SDK_INT >= 31 && !exact && st.remindersEnabled ? View.VISIBLE : View.GONE);
        summary(R.id.set_rem_exact, getString(R.string.set_rem_exact_summary));

        summary(R.id.set_exam_enabled, canNotify ? getString(R.string.set_exam_enabled_summary) : getString(R.string.notifications_blocked));
        toggle(R.id.set_exam_enabled, st.examReminders);
        java.util.List<Integer> none = new java.util.ArrayList<>();
        summary(R.id.set_exam_days, Exams.summary(this, st.examDaysBefore, none));
        summary(R.id.set_exam_day_time, getString(R.string.set_exam_day_time_summary, Fmt.time(this, st.examDayTime)));
        summary(R.id.set_exam_minutes, Exams.summary(this, none, st.examMinutesBefore));
        for (int id : new int[]{R.id.set_exam_days, R.id.set_exam_day_time, R.id.set_exam_minutes}) {
            findViewById(id).setEnabled(st.examReminders);
            findViewById(id).setAlpha(st.examReminders ? 1f : 0.5f);
        }
        summary(R.id.set_export_pdf, getString(R.string.export_pdf_summary));
        summary(R.id.set_backup, getString(R.string.backup_summary));
        summary(R.id.set_restore, getString(R.string.restore_summary));
        summary(R.id.set_sample, getString(R.string.load_sample_summary));
        summary(R.id.set_clear, getString(R.string.clear_data_summary));
        summary(R.id.set_about, getString(R.string.about_summary, BuildConfig.VERSION_NAME));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Permission state may have changed in the system settings.
        render();
    }
}
