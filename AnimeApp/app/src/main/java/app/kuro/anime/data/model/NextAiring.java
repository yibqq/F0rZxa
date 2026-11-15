package app.kuro.anime.data.model;

public final class NextAiring {
    public final int episode;
    public final long airingAtSec;

    public NextAiring(int episode, long airingAtSec) {
        this.episode = episode;
        this.airingAtSec = airingAtSec;
    }
}
