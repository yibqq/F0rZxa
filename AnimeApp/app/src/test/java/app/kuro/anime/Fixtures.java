package app.kuro.anime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads recorded API responses (real AniList/Kitsu captures; Jikan samples follow the documented v4 schema). */
public final class Fixtures {
    private Fixtures() {}

    public static String load(String name) {
        try (InputStream in = Fixtures.class.getClassLoader().getResourceAsStream("fixtures/" + name)) {
            if (in == null) throw new IllegalStateException("Missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
