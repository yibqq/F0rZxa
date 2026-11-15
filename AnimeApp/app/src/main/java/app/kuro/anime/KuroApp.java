package app.kuro.anime;

import android.app.Application;

import app.kuro.anime.data.local.KuroDatabase;
import app.kuro.anime.data.local.ResponseCache;
import app.kuro.anime.data.net.HttpClient;
import app.kuro.anime.data.net.NetworkMonitor;
import app.kuro.anime.data.repo.AnimeRepository;
import app.kuro.anime.data.repo.CachedExecutor;
import app.kuro.anime.data.repo.EpisodeRepository;
import app.kuro.anime.data.repo.LibraryRepository;
import app.kuro.anime.data.repo.SearchHistoryRepository;
import app.kuro.anime.data.repo.SettingsStore;
import app.kuro.anime.data.source.anilist.AniListSource;
import app.kuro.anime.data.source.jikan.JikanSource;
import app.kuro.anime.data.source.kitsu.KitsuSource;

/** Application object and lightweight dependency container. */
public class KuroApp extends Application {

    private static KuroApp instance;

    private NetworkMonitor network;
    private ResponseCache responseCache;
    private AnimeRepository animeRepository;
    private EpisodeRepository episodeRepository;
    private LibraryRepository libraryRepository;
    private SearchHistoryRepository searchHistory;
    private SettingsStore settings;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        KuroDatabase db = new KuroDatabase(this);
        network = new NetworkMonitor(this);
        HttpClient http = new HttpClient(network);
        responseCache = new ResponseCache(db);
        CachedExecutor exec = new CachedExecutor(responseCache);
        AniListSource anilist = new AniListSource(http);
        JikanSource jikan = new JikanSource(http);
        KitsuSource kitsu = new KitsuSource(http);
        animeRepository = new AnimeRepository(anilist, jikan, exec);
        episodeRepository = new EpisodeRepository(kitsu, jikan, exec);
        libraryRepository = new LibraryRepository(db);
        searchHistory = new SearchHistoryRepository(db);
        settings = new SettingsStore(this);

        // Connect your own streaming backend here, e.g.:
        // PlaybackProviders.install(new MyBackendPlaybackProvider(...));
    }

    public static KuroApp get() { return instance; }

    public NetworkMonitor network() { return network; }
    public ResponseCache responseCache() { return responseCache; }
    public AnimeRepository anime() { return animeRepository; }
    public EpisodeRepository episodes() { return episodeRepository; }
    public LibraryRepository library() { return libraryRepository; }
    public SearchHistoryRepository searchHistory() { return searchHistory; }
    public SettingsStore settings() { return settings; }
}
