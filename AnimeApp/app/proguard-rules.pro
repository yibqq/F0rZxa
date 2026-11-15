# Kuro has no reflection-based serialization (org.json is used for all parsing), so R8 needs
# only the rules shipped inside OkHttp and Glide. AnimeQuery travels in Intents as Serializable.
-keepclassmembers class app.kuro.anime.data.model.AnimeQuery {
    private static final long serialVersionUID;
    <fields>;
}
-keepnames class app.kuro.anime.data.model.AnimeQuery$Sort
# Public integration point for a future playback backend.
-keep interface app.kuro.anime.playback.EpisodePlaybackProvider { *; }
-keep interface app.kuro.anime.playback.EpisodePlaybackProvider$* { *; }
-keep class app.kuro.anime.playback.PlaybackRequest { *; }
