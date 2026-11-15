package app.kuro.anime.core;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, dependency-free text helpers (unit-testable on the JVM). */
public final class Text {
    private static final Pattern BR = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern MULTI_NL = Pattern.compile("\\n{3,}");
    private static final Pattern NUM_ENTITY = Pattern.compile("&#(x?[0-9a-fA-F]+);");

    private Text() {}

    public static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    public static String firstNonBlank(String... values) {
        for (String v : values) if (!isBlank(v)) return v.trim();
        return null;
    }

    /** Converts AniList/MAL synopsis markup into readable plain text. */
    public static String stripHtml(String html) {
        if (html == null) return null;
        String s = html.replace("\r", "");
        s = BR.matcher(s).replaceAll("\n");
        s = TAG.matcher(s).replaceAll("");
        s = s.replace("&quot;", "\"").replace("&#039;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
                .replace("&mdash;", "—").replace("&ndash;", "–").replace("&hellip;", "…")
                .replace("&rsquo;", "’").replace("&lsquo;", "‘").replace("&ldquo;", "“").replace("&rdquo;", "”");
        Matcher m = NUM_ENTITY.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String g = m.group(1);
            int cp;
            try {
                cp = g.startsWith("x") ? Integer.parseInt(g.substring(1), 16) : Integer.parseInt(g);
            } catch (NumberFormatException e) {
                cp = '?';
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(cp))));
        }
        m.appendTail(sb);
        s = sb.toString().replace("&amp;", "&");
        s = MULTI_NL.matcher(s).replaceAll("\n\n");
        return s.trim();
    }

    public static String join(Collection<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String i : items) {
            if (isBlank(i)) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(i);
        }
        return sb.toString();
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb);
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0);
    }
}
