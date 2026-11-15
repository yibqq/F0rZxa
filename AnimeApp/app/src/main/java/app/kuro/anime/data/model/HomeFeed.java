package app.kuro.anime.data.model;

import java.util.ArrayList;
import java.util.List;

public final class HomeFeed {
    public final List<Section> sections = new ArrayList<>();

    public static final class Section {
        public final SectionType type;
        public final List<Anime> items;

        public Section(SectionType type, List<Anime> items) {
            this.type = type;
            this.items = items;
        }
    }

    public Section get(SectionType type) {
        for (Section s : sections) if (s.type == type) return s;
        return null;
    }
}
