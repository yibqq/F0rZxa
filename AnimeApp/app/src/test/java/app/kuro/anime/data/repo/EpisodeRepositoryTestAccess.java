package app.kuro.anime.data.repo;

import java.util.List;
import java.util.Map;

import androidx.annotation.VisibleForTesting;

import app.kuro.anime.data.model.Anime;
import app.kuro.anime.data.model.Episode;
import app.kuro.anime.data.model.StreamingEpisode;
import app.kuro.anime.data.source.kitsu.KitsuMapping;

/** Exposes the pure merge rules of {@link EpisodeRepository} to unit tests. */
@VisibleForTesting
public final class EpisodeRepositoryTestAccess {
    private EpisodeRepositoryTestAccess() {}

    public static int total(Anime a, KitsuMapping m) { return EpisodeRepository.totalEpisodes(a, m); }

    public static boolean merge(Map<Integer, Episode> t, List<Episode> in, boolean flags) {
        return EpisodeRepository.mergeInto(t, in, flags);
    }

    public static boolean streaming(Map<Integer, Episode> t, List<StreamingEpisode> s) {
        return EpisodeRepository.applyStreamingThumbnails(t, s);
    }

    public static boolean verified(Anime a, KitsuMapping m) { return EpisodeRepository.verified(a, m); }
}
