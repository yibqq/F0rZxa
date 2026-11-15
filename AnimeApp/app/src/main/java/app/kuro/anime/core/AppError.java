package app.kuro.anime.core;

import androidx.annotation.NonNull;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** A classified failure that the UI can turn into a clear, localized message. */
public final class AppError extends Exception {

    public enum Kind { OFFLINE, TIMEOUT, RATE_LIMITED, SERVER, NOT_FOUND, PARSE, UNKNOWN }

    public final Kind kind;
    /** HTTP status when known, otherwise 0. */
    public final int httpCode;
    /** Server-advised wait in milliseconds (for {@link Kind#RATE_LIMITED}), otherwise 0. */
    public final long retryAfterMs;

    public AppError(@NonNull Kind kind, String message, Throwable cause, int httpCode, long retryAfterMs) {
        super(message, cause);
        this.kind = kind;
        this.httpCode = httpCode;
        this.retryAfterMs = retryAfterMs;
    }

    public AppError(@NonNull Kind kind, String message) {
        this(kind, message, null, 0, 0);
    }

    public static AppError from(Throwable t) {
        if (t instanceof AppError) return (AppError) t;
        if (t instanceof UnknownHostException || t instanceof ConnectException) {
            return new AppError(Kind.OFFLINE, t.getMessage(), t, 0, 0);
        }
        if (t instanceof SocketTimeoutException || t instanceof InterruptedIOException) {
            return new AppError(Kind.TIMEOUT, t.getMessage(), t, 0, 0);
        }
        if (t instanceof org.json.JSONException || t instanceof ClassCastException
                || t instanceof NumberFormatException) {
            return new AppError(Kind.PARSE, t.getMessage(), t, 0, 0);
        }
        return new AppError(Kind.UNKNOWN, t == null ? null : t.getMessage(), t, 0, 0);
    }

    /** Whether trying a different data source could plausibly help. */
    public boolean isSourceFailure() {
        return kind == Kind.SERVER || kind == Kind.RATE_LIMITED || kind == Kind.TIMEOUT
                || kind == Kind.PARSE || kind == Kind.UNKNOWN;
    }
}
