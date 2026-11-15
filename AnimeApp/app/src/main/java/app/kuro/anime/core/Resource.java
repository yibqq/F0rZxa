package app.kuro.anime.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Loading / success / error wrapper delivered to the UI.
 * <p>{@code stale} means the data came from the local cache because the network failed.
 * {@code fallback} means a secondary data source answered because the primary one failed.</p>
 */
public final class Resource<T> {

    public enum Status { LOADING, SUCCESS, ERROR }

    @NonNull public final Status status;
    @Nullable public final T data;
    @Nullable public final AppError error;
    public final boolean stale;
    public final boolean fallback;

    private Resource(@NonNull Status status, @Nullable T data, @Nullable AppError error,
                     boolean stale, boolean fallback) {
        this.status = status;
        this.data = data;
        this.error = error;
        this.stale = stale;
        this.fallback = fallback;
    }

    public static <T> Resource<T> loading(@Nullable T previous) {
        return new Resource<>(Status.LOADING, previous, null, false, false);
    }

    public static <T> Resource<T> success(@NonNull T data) {
        return new Resource<>(Status.SUCCESS, data, null, false, false);
    }

    public static <T> Resource<T> success(@NonNull T data, boolean stale, boolean fallback) {
        return new Resource<>(Status.SUCCESS, data, null, stale, fallback);
    }

    public static <T> Resource<T> error(@NonNull AppError error, @Nullable T previous) {
        return new Resource<>(Status.ERROR, previous, error, false, false);
    }

    public boolean isLoading() { return status == Status.LOADING; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isError() { return status == Status.ERROR; }
}
