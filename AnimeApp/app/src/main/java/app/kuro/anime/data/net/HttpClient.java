package app.kuro.anime.data.net;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import app.kuro.anime.BuildConfig;
import app.kuro.anime.core.AppError;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Thin HTTP layer shared by all data sources: per-host rate limiting, bounded retries with
 * back-off for 429/5xx, and conversion of failures into {@link AppError}s.
 * No API secrets live here — every source used by the app is keyless and public.
 */
public final class HttpClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_ATTEMPTS = 3;
    /** Never block a user-facing request longer than this waiting for a server back-off. */
    private static final long MAX_BACKOFF_WAIT_MS = 15_000;

    private final OkHttpClient client;
    private final Map<String, RateLimiter> limiters = new HashMap<>();
    private final RateLimiter defaultLimiter = new RateLimiter(60, 60_000, 250);
    private final NetworkMonitor network;

    public HttpClient(@Nullable NetworkMonitor network) {
        this.network = network;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public void registerLimiter(String host, RateLimiter limiter) {
        synchronized (limiters) {
            limiters.put(host, limiter);
        }
    }

    public String get(String url, Map<String, String> headers) throws AppError {
        Request.Builder b = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> h : headers.entrySet()) b.header(h.getKey(), h.getValue());
        return execute(b);
    }

    public String postJson(String url, String json, Map<String, String> headers) throws AppError {
        Request.Builder b = new Request.Builder().url(url).post(RequestBody.create(json, JSON));
        for (Map.Entry<String, String> h : headers.entrySet()) b.header(h.getKey(), h.getValue());
        return execute(b);
    }

    private String execute(Request.Builder builder) throws AppError {
        builder.header("User-Agent", "Kuro/" + BuildConfig.VERSION_NAME + " (Android)");
        Request request = builder.build();
        RateLimiter limiter = limiterFor(request.url().host());

        AppError last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (network != null && !network.isOnline()) {
                throw new AppError(AppError.Kind.OFFLINE, "No connection");
            }
            long pause = limiter.pendingPauseMs();
            if (pause > MAX_BACKOFF_WAIT_MS) {
                throw new AppError(AppError.Kind.RATE_LIMITED, "Backing off", null, 429, pause);
            }
            try {
                limiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AppError(AppError.Kind.UNKNOWN, "Interrupted", e, 0, 0);
            }
            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body == null) throw new AppError(AppError.Kind.PARSE, "Empty body");
                    return body.string();
                }
                if (code == 429) {
                    long retryAfter = parseRetryAfter(response.header("Retry-After"));
                    limiter.pauseFor(retryAfter);
                    last = new AppError(AppError.Kind.RATE_LIMITED, "HTTP 429", null, code, retryAfter);
                    if (retryAfter > MAX_BACKOFF_WAIT_MS) throw last;
                    continue;
                }
                if (code == 404) throw new AppError(AppError.Kind.NOT_FOUND, "HTTP 404", null, code, 0);
                if (code >= 500) {
                    last = new AppError(AppError.Kind.SERVER, "HTTP " + code, null, code, 0);
                    sleepQuietly(600L * attempt);
                    continue;
                }
                // Other 4xx: retrying will not help (bad request, forbidden…)
                throw new AppError(AppError.Kind.SERVER, "HTTP " + code, null, code, 0);
            } catch (IOException e) {
                last = AppError.from(e);
                if (network != null && !network.isOnline()) {
                    throw new AppError(AppError.Kind.OFFLINE, e.getMessage(), e, 0, 0);
                }
                sleepQuietly(500L * attempt);
            }
        }
        throw last != null ? last : new AppError(AppError.Kind.UNKNOWN, "Request failed");
    }

    private RateLimiter limiterFor(String host) {
        synchronized (limiters) {
            RateLimiter l = limiters.get(host);
            return l != null ? l : defaultLimiter;
        }
    }

    static long parseRetryAfter(@Nullable String header) {
        if (header == null) return 2_000;
        try {
            return Math.max(1_000, Long.parseLong(header.trim()) * 1000L);
        } catch (NumberFormatException e) {
            return 5_000;
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
