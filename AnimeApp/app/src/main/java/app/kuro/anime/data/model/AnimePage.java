package app.kuro.anime.data.model;

import java.util.List;

public final class AnimePage {
    public final List<Anime> items;
    public final int page;
    public final boolean hasNext;

    public AnimePage(List<Anime> items, int page, boolean hasNext) {
        this.items = items;
        this.page = page;
        this.hasNext = hasNext;
    }
}
