package app.kuro.anime.data.model;

public final class Relation {
    public final String type; // SEQUEL, PREQUEL, SIDE_STORY, PARENT, ALTERNATIVE, SPIN_OFF, SUMMARY, …
    public final Anime anime;

    public Relation(String type, Anime anime) {
        this.type = type;
        this.anime = anime;
    }
}
