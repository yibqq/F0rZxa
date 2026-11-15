package app.kuro.anime.core;

/** A value plus where it came from. */
public final class Fetched<T> {
    public final T value;
    public final boolean stale;
    public final boolean fallback;

    public Fetched(T value, boolean stale, boolean fallback) {
        this.value = value;
        this.stale = stale;
        this.fallback = fallback;
    }

    public static <T> Fetched<T> fresh(T value) { return new Fetched<>(value, false, false); }

    public <R> Fetched<R> map(R other) { return new Fetched<>(other, stale, fallback); }

    public Resource<T> toResource() { return Resource.success(value, stale, fallback); }
}
