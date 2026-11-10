package com.mizan.gpa.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;
import com.mizan.gpa.R;
import com.mizan.gpa.data.Repository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * App-wide fonts. Bundled fonts live in assets/fonts (all under the SIL Open Font License); fonts the
 * user imports are copied to the app's private storage and grouped into families by file name
 * ("Family-Bold.otf"). Text views get the regular, medium or bold face that matches their own weight.
 */
public final class Fonts {
    public static final String SYSTEM = "system";
    public static final String DEFAULT = "tajawal";
    public static final String FILE_PREFIX = "file:";
    private static final String DIR = "fonts";

    /** A selectable font family. */
    public static final class Family {
        public final String id;
        public final String name;
        public final boolean imported;
        final String regular, medium, bold;
        final boolean variable;
        /** Imported files of this family by weight (100..900). */
        final Map<Integer, File> files = new LinkedHashMap<>();

        Family(String id, String name, String regular, String medium, String bold, boolean variable) {
            this.id = id;
            this.name = name;
            this.regular = regular;
            this.medium = medium;
            this.bold = bold;
            this.variable = variable;
            this.imported = false;
        }

        Family(String id, String name) {
            this.id = id;
            this.name = name;
            this.regular = this.medium = this.bold = null;
            this.variable = false;
            this.imported = true;
        }

        public int weightCount() {
            return imported ? files.size() : (variable ? 3 : (medium != null ? 3 : 2));
        }
    }

    public static final Family[] BUNDLED = {
            new Family("tajawal", "Tajawal · تجوّل", "tajawal_regular.ttf", "tajawal_medium.ttf", "tajawal_bold.ttf", false),
            new Family("cairo", "Cairo · القاهرة", "cairo_variable.ttf", null, null, true),
            new Family("plex", "IBM Plex Sans Arabic", "plex_regular.ttf", "plex_medium.ttf", "plex_bold.ttf", false),
            new Family("almarai", "Almarai · المراعي", "almarai_regular.ttf", null, "almarai_bold.ttf", false),
            new Family("readex", "Readex Pro", "readex_variable.ttf", null, null, true),
            new Family("kufi", "Noto Kufi Arabic · كوفي", "kufi_variable.ttf", null, null, true),
    };

    private static String loadedId;
    private static Typeface regular, medium, bold;
    /** Changes whenever the font changes, so views know they must be updated. */
    private static int generation;

    private Fonts() {}

    // ------------------------------------------------------------------ catalogue

    public static List<Family> families(Context ctx) {
        List<Family> out = new ArrayList<>();
        for (Family f : BUNDLED) out.add(f);
        out.addAll(importedFamilies(ctx));
        return out;
    }

    public static List<Family> importedFamilies(Context ctx) {
        Map<String, Family> byName = new LinkedHashMap<>();
        File[] files = dir(ctx).listFiles();
        if (files == null) return new ArrayList<>();
        java.util.Arrays.sort(files);
        for (File f : files) {
            String base = stripExtension(f.getName());
            String family = familyName(base);
            Family fam = byName.get(family);
            if (fam == null) {
                fam = new Family(FILE_PREFIX + family, family);
                byName.put(family, fam);
            }
            fam.files.put(weightOf(base), f);
        }
        return new ArrayList<>(byName.values());
    }

    public static Family find(Context ctx, String id) {
        for (Family f : families(ctx)) if (f.id.equals(id)) return f;
        return null;
    }

    public static String displayName(Context ctx, String id) {
        if (id == null || SYSTEM.equals(id)) return ctx.getString(R.string.font_system);
        Family f = find(ctx, id);
        return f != null ? f.name : ctx.getString(R.string.font_system);
    }

    static File dir(Context ctx) {
        File d = new File(ctx.getFilesDir(), DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    // ------------------------------------------------------------------ loading

    private static synchronized void ensure(Context ctx) {
        String id = Repository.get(ctx).settings().fontId;
        if (id == null) id = DEFAULT;
        if (id.equals(loadedId)) return;
        loadedId = id;
        generation++;
        regular = medium = bold = null;
        if (SYSTEM.equals(id)) return;
        Family f = find(ctx, id);
        if (f == null) {
            f = BUNDLED[0];
        }
        try {
            if (f.imported) loadImported(f);
            else loadBundled(ctx.getAssets(), f);
        } catch (RuntimeException e) {
            regular = medium = bold = null;
        }
    }

    private static void loadBundled(AssetManager am, Family f) {
        String path = DIR + "/";
        if (f.variable) {
            regular = variable(am, path + f.regular, 400);
            medium = variable(am, path + f.regular, 500);
            bold = variable(am, path + f.regular, 700);
        } else {
            regular = new Typeface.Builder(am, path + f.regular).setWeight(400).build();
            medium = f.medium != null ? new Typeface.Builder(am, path + f.medium).setWeight(500).build() : null;
            bold = new Typeface.Builder(am, path + f.bold).setWeight(700).build();
        }
    }

    private static Typeface variable(AssetManager am, String path, int weight) {
        return new Typeface.Builder(am, path)
                .setFontVariationSettings("'wght' " + weight)
                .setWeight(weight)
                .build();
    }

    private static void loadImported(Family f) {
        File reg = closest(f.files, 400, false);
        File med = f.files.get(500);
        File b = closest(f.files, 700, true);
        regular = reg == null ? null : new Typeface.Builder(reg).setWeight(400).build();
        medium = med == null ? null : new Typeface.Builder(med).setWeight(500).build();
        if (b != null && b != reg) bold = new Typeface.Builder(b).setWeight(700).build();
        else if (regular != null) bold = Typeface.create(regular, Typeface.BOLD);
    }

    /** The file whose weight is closest to {@code target}, preferring heavier (bold) or lighter faces. */
    private static File closest(Map<Integer, File> files, int target, boolean heavier) {
        File best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Map.Entry<Integer, File> e : files.entrySet()) {
            int w = e.getKey();
            int score = Math.abs(w - target) * 2 + ((heavier ? w < target : w > target) ? 1 : 0);
            if (heavier && w < 600 && files.size() > 1) score += 1000;
            if (score < bestScore) {
                bestScore = score;
                best = e.getValue();
            }
        }
        return best;
    }

    /** Regular and bold faces of any family, for previews; null for the system font. */
    public static synchronized Typeface[] preview(Context ctx, Family f) {
        if (f == null) return null;
        Typeface keepR = regular, keepM = medium, keepB = bold;
        try {
            if (f.imported) loadImported(f);
            else loadBundled(ctx.getAssets(), f);
            return new Typeface[]{regular, bold};
        } catch (RuntimeException e) {
            return null;
        } finally {
            regular = keepR;
            medium = keepM;
            bold = keepB;
        }
    }

    // ------------------------------------------------------------------ applying

    /** Typeface for a weight (400 regular, 500 medium, 700 bold), or null to keep the system font. */
    public static Typeface get(Context ctx, int weight) {
        ensure(ctx);
        if (regular == null) return null;
        if (weight >= 600) return bold != null ? bold : regular;
        if (weight >= 500) return medium != null ? medium : regular;
        return regular;
    }

    public static void apply(Paint p, Context ctx, int weight) {
        Typeface t = get(ctx, weight);
        p.setTypeface(t != null ? t : Typeface.create(Typeface.DEFAULT, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL));
    }

    private static int weightOf(Typeface t) {
        if (t == null) return 400;
        if (Build.VERSION.SDK_INT >= 28) return Math.max(t.getWeight(), t.isBold() ? 700 : 0);
        return t.isBold() ? 700 : 400;
    }

    /** Applies the app font to every text view under {@code root} that has not been updated yet. */
    public static void applyTree(View root) {
        if (root == null) return;
        Context ctx = root.getContext();
        ensure(ctx);
        walk(root, ctx);
    }

    private static void walk(View v, Context ctx) {
        if (v instanceof TextView) applyView((TextView) v, ctx);
        if (v instanceof TextInputLayout) {
            Typeface t = get(ctx, 400);
            if (t != null && !Integer.valueOf(generation).equals(v.getTag(R.id.font_generation))) {
                ((TextInputLayout) v).setTypeface(t);
                v.setTag(R.id.font_generation, generation);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), ctx);
        }
    }

    private static void applyView(TextView tv, Context ctx) {
        if (Boolean.TRUE.equals(tv.getTag(R.id.font_fixed))) return;
        Object gen = tv.getTag(R.id.font_generation);
        Typeface current = tv.getTypeface();
        // Skip views already set to our face, unless the font changed or code replaced the face.
        if (Integer.valueOf(generation).equals(gen) && current == tv.getTag(R.id.font_face)) return;
        Object stored = tv.getTag(R.id.font_weight);
        int weight;
        if (stored instanceof Integer && current == tv.getTag(R.id.font_face)) {
            weight = (Integer) stored;
        } else {
            weight = weightOf(current);
            tv.setTag(R.id.font_weight, weight);
        }
        Typeface t = get(ctx, weight);
        if (t != null && t != current) tv.setTypeface(t);
        tv.setTag(R.id.font_generation, generation);
        tv.setTag(R.id.font_face, tv.getTypeface());
    }

    // ------------------------------------------------------------------ importing

    /**
     * Copies font files chosen by the user into private storage. Returns the id of the imported family,
     * or null if none of the files is a usable font.
     */
    public static String importFonts(Context ctx, List<Uri> uris) throws IOException {
        String family = null;
        for (Uri uri : uris) {
            String name = queryName(ctx, uri);
            String ext = name.toLowerCase(Locale.ROOT).endsWith(".otf") ? ".otf" : ".ttf";
            String base = sanitize(stripExtension(name));
            File tmp = new File(ctx.getCacheDir(), "import-" + System.nanoTime() + ext);
            try (InputStream in = ctx.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(tmp)) {
                if (in == null) continue;
                byte[] buf = new byte[16384];
                int n;
                long total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > 30L * 1024 * 1024) throw new IOException("Font file too large");
                    out.write(buf, 0, n);
                }
            }
            Typeface t = new Typeface.Builder(tmp).build();
            if (t == null) {
                tmp.delete();
                continue;
            }
            File dest = new File(dir(ctx), base + ext);
            if (dest.exists()) dest.delete();
            if (!tmp.renameTo(dest)) {
                tmp.delete();
                continue;
            }
            family = FILE_PREFIX + familyName(base);
        }
        loadedId = null;
        return family;
    }

    public static void deleteFamily(Context ctx, String id) {
        for (Family f : importedFamilies(ctx)) {
            if (!f.id.equals(id)) continue;
            for (File file : f.files.values()) file.delete();
        }
        loadedId = null;
    }

    /** Forces the font to be reloaded (after the setting changed). */
    public static synchronized void invalidate() {
        loadedId = null;
    }

    private static String queryName(Context ctx, Uri uri) {
        String name = null;
        try (Cursor c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (RuntimeException ignored) {
            // Fall back to the last path segment.
        }
        if (name == null) name = uri.getLastPathSegment();
        return name == null ? "Font" : name;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String sanitize(String s) {
        String out = s.replaceAll("[^\\p{L}\\p{N} _\\-]", "").trim();
        return out.isEmpty() ? "Font" : out;
    }

    private static final String[][] WEIGHTS = {
            {"thin", "100"}, {"hairline", "100"}, {"extralight", "200"}, {"ultralight", "200"}, {"light", "300"},
            {"regular", "400"}, {"normal", "400"}, {"book", "400"}, {"roman", "400"}, {"medium", "500"},
            {"semibold", "600"}, {"demibold", "600"}, {"extrabold", "800"}, {"ultrabold", "800"}, {"bold", "700"},
            {"black", "900"}, {"heavy", "900"},
    };

    /** "Thmanyah Sans-Bold" -> "Thmanyah Sans"; files without a weight suffix are their own family. */
    static String familyName(String base) {
        int dash = Math.max(base.lastIndexOf('-'), base.lastIndexOf('_'));
        if (dash > 0 && weightSuffix(base.substring(dash + 1)) > 0) return base.substring(0, dash).trim();
        return base.trim();
    }

    static int weightOf(String base) {
        int dash = Math.max(base.lastIndexOf('-'), base.lastIndexOf('_'));
        int w = dash > 0 ? weightSuffix(base.substring(dash + 1)) : 0;
        return w > 0 ? w : 400;
    }

    private static int weightSuffix(String s) {
        String k = s.toLowerCase(Locale.ROOT).replace(" ", "").replace("italic", "");
        for (String[] w : WEIGHTS) if (k.equals(w[0])) return Integer.parseInt(w[1]);
        return 0;
    }
}
