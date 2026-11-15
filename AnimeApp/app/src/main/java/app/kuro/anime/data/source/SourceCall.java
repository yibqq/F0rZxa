package app.kuro.anime.data.source;

import app.kuro.anime.core.AppError;

/**
 * A cacheable request produced by a data-source adapter: a stable cache key, a freshness
 * window, how to fetch the raw body, and how to parse it into the unified model.
 */
public final class SourceCall<T> {

    public interface Network { String fetch() throws AppError; }

    public interface Parser<T> { T parse(String body) throws Exception; }

    public final String cacheKey;
    public final long ttlMs;
    public final Network network;
    public final Parser<T> parser;

    public SourceCall(String cacheKey, long ttlMs, Network network, Parser<T> parser) {
        this.cacheKey = cacheKey;
        this.ttlMs = ttlMs;
        this.network = network;
        this.parser = parser;
    }
}
