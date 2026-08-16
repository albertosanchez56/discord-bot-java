package com.main.audio;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.main.audio.Scheduler.LoopMode;
import com.main.config.Config;
import com.main.util.EmbedFactory;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.AndroidMusicWithThumbnail;
import dev.lavalink.youtube.clients.AndroidVrWithThumbnail;
import dev.lavalink.youtube.clients.MusicWithThumbnail;
import dev.lavalink.youtube.clients.TvHtml5SimplyWithThumbnail;
import dev.lavalink.youtube.clients.WebWithThumbnail;
import dev.lavalink.youtube.clients.WebEmbeddedWithThumbnail;
import dev.lavalink.youtube.clients.skeleton.Client;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.managers.AudioManager;

/**
 * Public API for audio operations.
 *
 * Owns the singleton {@link AudioPlayerManager}, the per-guild {@link GuildAudio}
 * registry, the resolution cache and ALL auto-disconnect schedulers (idle =
 * music stopped; alone = bot left without humans in the voice channel).
 *
 * Centralising both schedulers here ensures that any playback activity cancels
 * pending disconnects in a single place, avoiding the "join / leave / join /
 * leave" loop caused by two independent timers fighting each other.
 */
public final class AudioService {

    private static final Logger log = LoggerFactory.getLogger(AudioService.class);
    private static final long IDLE_TIMEOUT_SECONDS = 60;
    private static final long ALONE_GRACE_SECONDS = 60;

    private static final String FAREWELL_GIF =
            "https://tenor.com/view/hasta-la-proxima-float-fly-gif-14857954";
    private static final String FAREWELL_IDLE =
            "Me desconecto por inactividad. \u00a1Hasta la pr\u00f3xima!";
    private static final String FAREWELL_ALONE =
            "Me he quedado solo en el canal. \u00a1Hasta la pr\u00f3xima!";

    private final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
    private final Map<Long, GuildAudio> guilds = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> idleTasks = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> aloneTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService disconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "audio-disconnect-scheduler");
        t.setDaemon(true);
        return t;
    });
    private final ResolveCache cache = new ResolveCache();
    private volatile Consumer<Guild> onGuildUpdate = g -> {};
    private volatile BiPredicate<String, String> trackBlockedFilter = (title, author) -> false;

    public AudioService() {
        // Remote cipher is required nowadays: YouTube rotates the player
        // script often and local extraction (must find sig function) fails
        // for weeks between youtube-source releases. Defaults to the public
        // yt-cipher instance; override via YOUTUBE_REMOTE_CIPHER_URL.
        String cipherUrl = Config.youtubeRemoteCipherUrl();
        String cipherPassword = Config.youtubeRemoteCipherPassword().orElse(null);

        YoutubeSourceOptions options = new YoutubeSourceOptions()
                .setAllowSearch(true)
                .setAllowDirectVideoIds(true)
                .setAllowDirectPlaylistIds(true)
                .setRemoteCipher(cipherUrl, cipherPassword, "BertiniBot");

        // Order matters: first client that can answer wins. Mix of search-
        // capable + playback-capable clients, preferring ones that still
        // return Opus when possible.
        Client[] clients = {
                new MusicWithThumbnail(),
                new WebWithThumbnail(),
                new AndroidVrWithThumbnail(),
                new AndroidMusicWithThumbnail(),
                new TvHtml5SimplyWithThumbnail(),
                new WebEmbeddedWithThumbnail()
        };

        YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(options, clients);
        playerManager.registerSourceManager(youtube);

        // Exclude Lavaplayer's built-in (deprecated) YouTube source so it
        // does not race / override youtube-source.
        AudioSourceManagers.registerRemoteSources(playerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class);
        AudioSourceManagers.registerLocalSource(playerManager);

        log.info("YouTube source ready (youtube-source + remote cipher at {}).", cipherUrl);
    }

    public AudioPlayerManager playerManager() { return playerManager; }

    public ResolveCache cache() { return cache; }

    public GuildAudio get(Guild guild) {
        return guilds.computeIfAbsent(guild.getIdLong(), id -> {
            GuildAudio g = new GuildAudio(id, playerManager);
            g.scheduler().setOnIdle(() -> scheduleIdleDisconnect(guild));
            g.scheduler().setOnChange(() -> onGuildUpdate.accept(guild));
            g.scheduler().setOnTrackStart(track -> publishNowPlaying(guild, track));
            return g;
        });
    }

    public void setOnGuildUpdate(Consumer<Guild> listener) {
        this.onGuildUpdate = listener != null ? listener : g -> {};
    }

    /**
     * Plug in a predicate that decides if a track is blocked, based on title
     * and author. Validated before enqueueing so blocked tracks never enter
     * the queue or trigger the {@code onTrackStart} announcement.
     */
    public void setTrackBlockedFilter(BiPredicate<String, String> filter) {
        this.trackBlockedFilter = filter != null ? filter : (title, author) -> false;
    }

    /**
     * Connect to {@code channel}, idempotent if already connected to the same
     * channel. Cancels any pending auto-disconnect because we're clearly active.
     */
    public void connect(Guild guild, AudioChannelUnion channel) {
        AudioManager am = guild.getAudioManager();
        AudioChannel current = am.getConnectedChannel();
        am.setSendingHandler(get(guild).sendHandler());
        am.setAutoReconnect(true);
        if (current == null || current.getIdLong() != channel.getIdLong()) {
            log.debug("connect(): opening voice in guild={} channel={}", guild.getId(), channel.getId());
            am.openAudioConnection(channel);
        }
        cancelAllDisconnects(guild);
        signalHumanPresenceChange(guild);
    }

    public void disconnect(Guild guild) {
        try {
            log.debug("disconnect(): closing voice in guild {}", guild.getId());
            cancelAllDisconnects(guild);

            GuildAudio g = guilds.get(guild.getIdLong());
            if (g != null) {
                // Stop any residual playback so JDA doesn't keep the voice
                // socket open waiting for the next packet. Keep the player
                // instance and the queue alive so future /play calls work.
                g.player().stopTrack();
            }
            AudioManager am = guild.getAudioManager();
            am.setSendingHandler(null);
            am.closeAudioConnection();
        } catch (Exception e) {
            log.warn("Failed to disconnect from guild {}: {}", guild.getId(), e.getMessage());
        }
    }

    /**
     * Resolve {@code query} (URL or text) and enqueue the resulting track(s).
     * Cached resolutions short-circuit the network call.
     *
     * The {@code requester} is attached to each track via
     * {@link AudioTrack#setUserData(Object)} so that the auto-published
     * "Reproduciendo ahora" embed (fired from {@code onTrackStart}) can credit
     * who added the song without plumbing the user through more APIs.
     */
    public CompletableFuture<LoadResult> enqueue(Guild guild, String query, RequesterInfo requester) {
        cancelAllDisconnects(guild);
        String identifier = resolveIdentifier(query);
        String key = normalizeKey(identifier);
        Optional<AudioTrack> cached = cache.get(key);
        if (cached.isPresent()) {
            AudioTrack copy = cached.get().makeClone();
            if (isBlocked(copy)) {
                return CompletableFuture.completedFuture(new LoadResult.Blocked(copy));
            }
            tag(copy, requester);
            get(guild).scheduler().enqueue(copy);
            return CompletableFuture.completedFuture(new LoadResult.Single(copy, true));
        }

        CompletableFuture<LoadResult> future = new CompletableFuture<>();

        playerManager.loadItem(identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                cancelAllDisconnects(guild);
                if (isBlocked(track)) {
                    future.complete(new LoadResult.Blocked(track));
                    return;
                }
                cache.put(key, track);
                tag(track, requester);
                get(guild).scheduler().enqueue(track);
                future.complete(new LoadResult.Single(track, false));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                cancelAllDisconnects(guild);
                if (playlist.isSearchResult()) {
                    AudioTrack first = playlist.getTracks().get(0);
                    if (isBlocked(first)) {
                        future.complete(new LoadResult.Blocked(first));
                        return;
                    }
                    cache.put(key, first);
                    tag(first, requester);
                    get(guild).scheduler().enqueue(first);
                    future.complete(new LoadResult.Single(first, false));
                } else {
                    int skipped = 0;
                    for (AudioTrack t : playlist.getTracks()) {
                        if (isBlocked(t)) { skipped++; continue; }
                        tag(t, requester);
                        get(guild).scheduler().enqueue(t);
                    }
                    future.complete(new LoadResult.Playlist(playlist, skipped));
                }
            }

            @Override
            public void noMatches() {
                future.complete(new LoadResult.NoMatches());
            }

            @Override
            public void loadFailed(FriendlyException ex) {
                future.complete(new LoadResult.Failed(ex));
            }
        });
        return future;
    }

    private boolean isBlocked(AudioTrack track) {
        if (track == null) return false;
        var info = track.getInfo();
        return trackBlockedFilter.test(info.title, info.author);
    }

    private static void tag(AudioTrack track, RequesterInfo requester) {
        if (requester != null) track.setUserData(requester);
    }

    /**
     * Hook invoked by {@link Scheduler#onTrackStart(com.sedmelluq.discord.lavaplayer.player.AudioPlayer, AudioTrack)}.
     *
     * Publishes the rich "Reproduciendo ahora" embed in the guild's text
     * channel so the user gets a visible announcement on every transition
     * (auto-advance, manual skip, panel skip, ...).
     *
     * Skips announcements for {@link LoopMode#TRACK} repetitions of the same
     * identifier to avoid spamming when a single song is on loop.
     */
    private void publishNowPlaying(Guild guild, AudioTrack track) {
        GuildAudio g = guilds.get(guild.getIdLong());
        if (g == null || track == null) return;

        String identifier = track.getInfo().identifier;
        if (g.scheduler().getLoopMode() == LoopMode.TRACK
                && identifier != null
                && identifier.equals(g.lastAnnouncedIdentifier())) {
            return;
        }
        g.setLastAnnouncedIdentifier(identifier);

        MessageChannel channel = g.textChannel();
        if (channel == null) return;

        String name = null;
        String avatar = null;
        if (track.getUserData() instanceof RequesterInfo ri) {
            name = ri.name();
            avatar = ri.avatarUrl();
        }
        try {
            channel.sendMessageEmbeds(EmbedFactory.nowPlaying(track, name, avatar, g.scheduler()))
                    .queue(null,
                            err -> log.debug("Could not announce now-playing in guild {}: {}",
                                    guild.getId(), err.getMessage()));
        } catch (Exception ignored) {
            // Channel may have been deleted between checks. Best-effort.
        }
    }

    public boolean skip(Guild guild) {
        return get(guild).scheduler().skip();
    }

    public void clear(Guild guild) {
        GuildAudio g = get(guild);
        g.setLastAnnouncedIdentifier(null);
        g.scheduler().clear();
    }

    // ------------------------------------------------------------------
    // Auto-disconnect: idle (music stopped) + alone (no humans in channel)
    // ------------------------------------------------------------------

    /**
     * Notify that the human population in the bot's voice channel may have
     * changed. Schedules an "alone" disconnect if the bot is alone, cancels it
     * otherwise.
     */
    public void signalHumanPresenceChange(Guild guild) {
        AudioChannel botChannel = guild.getAudioManager().getConnectedChannel();
        if (botChannel == null) {
            log.debug("signalHumanPresenceChange(): bot not connected in guild {}, cancelling alone timer", guild.getId());
            cancelAloneDisconnect(guild);
            return;
        }
        boolean humans = hasHumans(botChannel);
        log.debug("signalHumanPresenceChange(): guild={} channel={} hasHumans={} members={}",
                guild.getId(), botChannel.getId(), humans, botChannel.getMembers().size());
        if (humans) {
            cancelAloneDisconnect(guild);
        } else {
            scheduleAloneDisconnect(guild);
        }
    }

    public void cancelAllDisconnects(Guild guild) {
        cancelIdleDisconnect(guild);
        cancelAloneDisconnect(guild);
    }

    private void scheduleIdleDisconnect(Guild guild) {
        cancelIdleDisconnect(guild);
        ScheduledFuture<?> task = disconnectScheduler.schedule(() -> {
            log.info("Idle timeout reached for guild {}, disconnecting.", guild.getId());
            announceFarewell(guild, FAREWELL_IDLE);
            disconnect(guild);
        }, IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        idleTasks.put(guild.getIdLong(), task);
    }

    private void cancelIdleDisconnect(Guild guild) {
        ScheduledFuture<?> existing = idleTasks.remove(guild.getIdLong());
        if (existing != null && !existing.isDone()) existing.cancel(false);
    }

    private void scheduleAloneDisconnect(Guild guild) {
        cancelAloneDisconnect(guild);
        log.debug("scheduleAloneDisconnect(): bot is alone in guild {}, will disconnect in {}s",
                guild.getId(), ALONE_GRACE_SECONDS);
        ScheduledFuture<?> task = disconnectScheduler.schedule(() -> {
            AudioChannel current = guild.getAudioManager().getConnectedChannel();
            if (current == null) return;
            if (hasHumans(current)) {
                log.debug("alone-task fired but humans present, skipping");
                return;
            }
            log.info("Voice channel for guild {} has no humans after grace, disconnecting.", guild.getId());
            announceFarewell(guild, FAREWELL_ALONE);
            disconnect(guild);
        }, ALONE_GRACE_SECONDS, TimeUnit.SECONDS);
        aloneTasks.put(guild.getIdLong(), task);
    }

    /**
     * Send a goodbye message + GIF to the last text channel where the bot was
     * invoked, mimicking the V1 behaviour. Best-effort: any failure (missing
     * channel, missing permission, ratelimit) is logged at debug and swallowed
     * so it never blocks the actual voice disconnect.
     */
    private void announceFarewell(Guild guild, String message) {
        GuildAudio g = guilds.get(guild.getIdLong());
        if (g == null) return;
        var channel = g.textChannel();
        if (channel == null) return;
        try {
            channel.sendMessage("\u23F9\uFE0F " + message).queue(
                ok -> channel.sendMessage(FAREWELL_GIF).queue(
                    null,
                    err -> log.debug("Could not send farewell gif in guild {}: {}", guild.getId(), err.getMessage())
                ),
                err -> log.debug("Could not send farewell message in guild {}: {}", guild.getId(), err.getMessage())
            );
        } catch (Exception ignored) {
            // Channel may have been deleted between checks. Not worth surfacing.
        }
    }

    private void cancelAloneDisconnect(Guild guild) {
        ScheduledFuture<?> existing = aloneTasks.remove(guild.getIdLong());
        if (existing != null && !existing.isDone()) existing.cancel(false);
    }

    private static boolean hasHumans(AudioChannel channel) {
        return channel.getMembers().stream().anyMatch(m -> !m.getUser().isBot());
    }

    public void shutdown() {
        disconnectScheduler.shutdownNow();
        playerManager.shutdown();
    }

    private static boolean isUrl(String s) {
        return s.startsWith("http://") || s.startsWith("https://");
    }

    private static String normalizeKey(String s) {
        return s.trim().toLowerCase();
    }

    /**
     * Build the identifier passed to Lavaplayer:
     *
     *  - Plain text   -> {@code ytsearch:<text>}.
     *  - YouTube URL with both {@code v=} and {@code list=RD...} (a "Mix" /
     *    radio playlist) -> strip the mix and return a plain video URL.
     *    YouTube generates mixes dynamically based on the requester's account
     *    history, so the bot would otherwise queue a random mix that doesn't
     *    match what the user sees in their client.
     *  - Anything else -> the URL as-is (real playlists with {@code list=PL...},
     *    {@code list=LL...}, etc. keep working).
     */
    static String resolveIdentifier(String query) {
        if (!isUrl(query)) return "ytsearch:" + query;
        String stripped = stripYoutubeMix(query);
        if (stripped != null) {
            log.debug("Stripped YouTube mix from query, using video only: {}", stripped);
            return stripped;
        }
        return query;
    }

    private static String stripYoutubeMix(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            if (host == null) return null;
            boolean isYoutube = host.endsWith("youtube.com") || host.endsWith("youtu.be");
            if (!isYoutube) return null;

            java.util.Map<String, String> params = parseQuery(uri.getRawQuery());
            String list = params.get("list");
            if (list == null || !list.startsWith("RD")) return null;

            String videoId = params.get("v");
            if (videoId == null && host.endsWith("youtu.be")) {
                String path = uri.getPath();
                if (path != null && path.length() > 1) videoId = path.substring(1);
            }
            if (videoId == null || videoId.isBlank()) return null;
            return "https://www.youtube.com/watch?v=" + videoId;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static java.util.Map<String, String> parseQuery(String raw) {
        if (raw == null || raw.isEmpty()) return java.util.Map.of();
        java.util.Map<String, String> out = new java.util.HashMap<>();
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) out.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return out;
    }

    public sealed interface LoadResult
            permits LoadResult.Single, LoadResult.Playlist, LoadResult.Blocked,
                    LoadResult.NoMatches, LoadResult.Failed {
        record Single(AudioTrack track, boolean fromCache) implements LoadResult {}
        record Playlist(AudioPlaylist playlist, int skippedBlocked) implements LoadResult {}
        record Blocked(AudioTrack track) implements LoadResult {}
        record NoMatches() implements LoadResult {}
        record Failed(FriendlyException error) implements LoadResult {}
    }
}
