package com.main.spotify;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.main.audio.AudioService;
import com.main.audio.AudioService.LoadResult;
import com.main.audio.RequesterInfo;

import net.dv8tion.jda.api.entities.Guild;

/**
 * Bridges Spotify metadata and the audio pipeline.
 *
 * <p>Spotify URLs never carry playable audio (DRM); this resolver fetches
 * track names + artists from the Spotify API and feeds them as YouTube
 * searches to {@link AudioService#enqueue}. For albums / playlists / artist
 * top tracks the first item is queued synchronously so the user hears
 * something immediately, then the rest are streamed in on a background
 * virtual thread and aggregated into a {@link BatchStats} future that the
 * caller can chain onto to post the "all done" message.</p>
 */
public final class SpotifyResolver {

    private static final Logger log = LoggerFactory.getLogger(SpotifyResolver.class);

    /** Hard cap so that pathologically large playlists don't melt YouTube. */
    private static final int MAX_TRACKS_PER_BUNDLE = 200;

    private final SpotifyMetadataProvider client;
    private final AudioService audio;
    private final Executor executor;

    public SpotifyResolver(SpotifyMetadataProvider client, AudioService audio) {
        this.client = client;
        this.audio = audio;
        this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .name("spotify-resolver-", 1).factory());
    }

    /** Convenience for callers that don't yet know if a query is Spotify. */
    public Optional<SpotifyRef> tryParse(String query) {
        return SpotifyUrlParser.parse(query);
    }

    /**
     * Resolve a Spotify reference and start enqueueing into the guild's
     * audio scheduler. Returns asynchronously with an {@link Outcome} once
     * the FIRST track is queued (so the user sees the embed quickly); the
     * rest of the bundle (if any) keeps loading in the background.
     */
    public CompletableFuture<Outcome> resolveAndEnqueue(Guild guild, SpotifyRef ref, RequesterInfo requester) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return switch (ref.kind()) {
                    case TRACK    -> resolveSingle(guild, ref, requester);
                    case ALBUM    -> resolveBundle(guild, client.getAlbum(ref.id()), requester);
                    case PLAYLIST -> resolveBundle(guild, client.getPlaylist(ref.id()), requester);
                    case ARTIST   -> resolveBundle(guild, client.getArtistTop(ref.id()), requester);
                };
            } catch (SpotifyException e) {
                log.warn("Spotify resolve failed for {}: {}", ref, e.getMessage());
                return new Outcome.Error(e);
            }
        }, executor);
    }

    /* ------------------------------------------------------------------ */
    /*  Internals                                                         */
    /* ------------------------------------------------------------------ */

    private Outcome resolveSingle(Guild guild, SpotifyRef ref, RequesterInfo requester)
            throws SpotifyException {
        SpotifyTrack track = client.getTrack(ref.id());
        try {
            LoadResult lr = audio.enqueue(guild, track.toYoutubeSearchQuery(), requester).join();
            return new Outcome.Single(track, lr);
        } catch (Exception e) {
            return new Outcome.Single(track, new LoadResult.Failed(
                    new com.sedmelluq.discord.lavaplayer.tools.FriendlyException(
                            e.getMessage(), com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS, e)));
        }
    }

    private Outcome resolveBundle(Guild guild, SpotifyBundle bundle, RequesterInfo requester) {
        if (bundle.tracks().isEmpty()) {
            return new Outcome.EmptyBundle(bundle);
        }

        List<SpotifyTrack> tracks = bundle.tracks().size() > MAX_TRACKS_PER_BUNDLE
                ? bundle.tracks().subList(0, MAX_TRACKS_PER_BUNDLE)
                : bundle.tracks();
        final int total = tracks.size();

        // Queue the first one in line so the user hears something asap.
        SpotifyTrack first = tracks.get(0);
        boolean firstOk = enqueueOne(guild, first, requester);

        AtomicInteger queued = new AtomicInteger(firstOk ? 1 : 0);
        AtomicInteger failed = new AtomicInteger(firstOk ? 0 : 1);

        CompletableFuture<BatchStats> batch = new CompletableFuture<>();
        List<SpotifyTrack> remaining = tracks.subList(1, total);

        if (remaining.isEmpty()) {
            batch.complete(new BatchStats(queued.get(), failed.get(), total));
        } else {
            // Background load + queue of the remaining tracks. We use a
            // single virtual thread so they keep their order in the queue;
            // parallelising would shuffle them.
            executor.execute(() -> {
                for (SpotifyTrack t : remaining) {
                    if (enqueueOne(guild, t, requester)) queued.incrementAndGet();
                    else failed.incrementAndGet();
                }
                batch.complete(new BatchStats(queued.get(), failed.get(), total));
            });
        }
        return new Outcome.Bundle(bundle, total, batch);
    }

    private boolean enqueueOne(Guild guild, SpotifyTrack track, RequesterInfo requester) {
        try {
            LoadResult lr = audio.enqueue(guild, track.toYoutubeSearchQuery(), requester).join();
            return switch (lr) {
                case LoadResult.Single s -> true;
                case LoadResult.Playlist p -> true;
                case LoadResult.Blocked b -> false;
                case LoadResult.NoMatches n -> {
                    log.debug("No YouTube match for Spotify track '{}' - '{}'",
                            track.artistsDisplay(), track.title());
                    yield false;
                }
                case LoadResult.Failed f -> {
                    log.debug("YouTube load failed for '{}': {}",
                            track.title(), f.error().getMessage());
                    yield false;
                }
            };
        } catch (Exception e) {
            log.debug("enqueue() for Spotify track '{}' threw: {}", track.title(), e.getMessage());
            return false;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Result types                                                      */
    /* ------------------------------------------------------------------ */

    public sealed interface Outcome
            permits Outcome.Single, Outcome.Bundle, Outcome.EmptyBundle, Outcome.Error {

        /** Spotify URL pointed at a single track. */
        record Single(SpotifyTrack track, LoadResult loadResult) implements Outcome {}

        /**
         * Spotify URL pointed at an album / playlist / artist. The first
         * track is already on its way; {@code firstQueuedCount} tells how
         * many we managed to enqueue immediately (0 or 1) and
         * {@code completion} resolves once the whole batch is done so the
         * caller can post a final "Encoladas X de N" message.
         */
        record Bundle(SpotifyBundle bundle, int totalToTry,
                       CompletableFuture<BatchStats> completion) implements Outcome {}

        record EmptyBundle(SpotifyBundle bundle) implements Outcome {}

        record Error(SpotifyException error) implements Outcome {}
    }

    public record BatchStats(int queued, int failed, int total) {}
}
