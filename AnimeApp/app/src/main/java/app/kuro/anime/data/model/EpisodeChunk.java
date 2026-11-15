package app.kuro.anime.data.model;

import java.util.ArrayList;
import java.util.List;

/** One page of an anime's episode list (100 episodes per chunk). */
public final class EpisodeChunk {
    public static final int SIZE = 100;

    public final int index;
    public final int total;
    public final List<Episode> episodes;
    public final List<String> sources;
    public final boolean stale;

    public EpisodeChunk(int index, int total, List<Episode> episodes, List<String> sources, boolean stale) {
        this.index = index;
        this.total = total;
        this.episodes = episodes;
        this.sources = sources == null ? new ArrayList<>() : sources;
        this.stale = stale;
    }

    public static int chunkCount(int total) { return total <= 0 ? 0 : (total + SIZE - 1) / SIZE; }

    public static int firstOf(int index) { return index * SIZE + 1; }

    public static int lastOf(int index, int total) { return Math.min(total, (index + 1) * SIZE); }
}
