# Connecting your playback API

Kuro ships **no player, no stream URLs and no scraping**. Anime metadata (AniList / Kitsu / Jikan)
is fully separate from playback. The "Watch" buttons go through one interface:

```
app/src/main/java/app/kuro/anime/playback/
├── EpisodePlaybackProvider.java      ← implement this
├── PlaybackRequest.java              ← what the user asked to watch
├── PlaybackProviders.java            ← install your provider here
└── UnconfiguredPlaybackProvider.java ← default: says "Playback isn't connected"
```

## What your provider receives

`PlaybackRequest` holds every reliable identifier Kuro has, so you can match without guessing by
title:

| Field | Meaning |
|---|---|
| `anilistId`, `malId` | Explicit cross-referenced IDs (0 if unknown) |
| `titleRomaji`, `titleEnglish`, `titleNative` | For display or as a last-resort lookup |
| `episodeNumber` | 1-based episode number |
| `episodeTitle` | If known |
| `totalEpisodes` | 0 if unknown |
| `preferredLanguageTag` | App UI locale, e.g. `ar` or `en` |

## Minimal implementation

```java
public final class MyBackendProvider implements EpisodePlaybackProvider {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Future<?> task;

    @NonNull @Override public String id() { return "my-backend"; }
    @NonNull @Override public String displayName() { return "My backend"; }
    @Override public boolean isAvailable() { return true; } // e.g. user signed in

    @Override
    public void resolve(@NonNull PlaybackRequest req, @NonNull Callback cb) {
        task = io.submit(() -> {
            try {
                MyResult r = myApi.lookup(req.anilistId, req.malId, req.episodeNumber); // your API, your format
                AppExecutors.main(() -> {
                    if (r == null) cb.onUnavailable("Not available");
                    else cb.onReady(host -> host.startActivity(MyPlayerActivity.intent(host, r)));
                });
            } catch (Exception e) {
                AppExecutors.main(() -> cb.onError(e));
            }
        });
    }

    @Override public void cancel() { if (task != null) task.cancel(true); }
}
```

Then register it in `KuroApp.onCreate()`:

```java
PlaybackProviders.install(new MyBackendProvider(/* … */));
```

That's all. `PlaybackLauncher` shows a cancellable "Preparing playback…" dialog, calls your
provider, and starts your `PlaybackSession`. Settings → Playback source shows your provider's name.

## Rules this contract follows

- Kuro never inspects your response; `PlaybackSession` is opaque.
- Your provider owns its player UI (ExoPlayer/Media3 or anything else), DRM and authentication.
- Keep secrets on your server; don't put API keys in the APK.
- On TV, your player activity should support D-pad controls; on cars, see `PLATFORMS.md`.
- Watched progress is stored by Kuro when the user marks episodes; if your player reports
  completion, call `KuroApp.get().library().setWatched(anime, n, n, true)` on a background thread.
