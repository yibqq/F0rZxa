package app.kuro.anime.data.repo;

import androidx.annotation.Nullable;

import app.kuro.anime.core.AppError;
import app.kuro.anime.core.Fetched;
import app.kuro.anime.data.local.ResponseCache;
import app.kuro.anime.data.source.SourceCall;

/**
 * Cache-first execution of {@link SourceCall}s:
 * fresh cache → network (then cache) → stale cache when allowed. Bodies are only cached after
 * they parse successfully, so a malformed response can never poison the cache.
 */
public final class CachedExecutor {

    private final ResponseCache cache;

    public CachedExecutor(ResponseCache cache) { this.cache = cache; }

    public <T> Fetched<T> execute(SourceCall<T> call, boolean forceRefresh, boolean allowStale) throws AppError {
        ResponseCache.Entry entry = cache.get(call.cacheKey);
        if (!forceRefresh && entry != null && entry.isFresh(call.ttlMs)) {
            T cached = tryParse(call, entry);
            if (cached != null) return Fetched.fresh(cached);
        }
        try {
            String body = call.network.fetch();
            T value;
            try {
                value = call.parser.parse(body);
            } catch (AppError e) {
                throw e;
            } catch (Exception e) {
                throw new AppError(AppError.Kind.PARSE, e.getMessage(), e, 0, 0);
            }
            cache.put(call.cacheKey, body);
            return Fetched.fresh(value);
        } catch (AppError e) {
            if (allowStale && entry != null && e.kind != AppError.Kind.NOT_FOUND) {
                T stale = tryParse(call, entry);
                if (stale != null) return new Fetched<>(stale, true, false);
            }
            throw e;
        }
    }

    /** Returns any cached value regardless of age, or null. */
    @Nullable
    public <T> Fetched<T> cachedOnly(SourceCall<T> call) {
        ResponseCache.Entry entry = cache.get(call.cacheKey);
        if (entry == null) return null;
        T v = tryParse(call, entry);
        return v == null ? null : new Fetched<>(v, true, false);
    }

    @Nullable
    private <T> T tryParse(SourceCall<T> call, ResponseCache.Entry entry) {
        try {
            return call.parser.parse(entry.body);
        } catch (Exception e) {
            cache.remove(call.cacheKey);
            return null;
        }
    }
}
