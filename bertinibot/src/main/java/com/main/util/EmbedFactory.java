package com.main.util;

import java.awt.Color;
import java.time.Duration;
import java.util.List;

import com.main.audio.Scheduler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/**
 * Factory for the embeds the bot uses across slash commands and panel updates.
 */
public final class EmbedFactory {

    private static final Color NOW_PLAYING_GREEN = new Color(0x1DB954);
    private static final Color ENQUEUED_GREEN = new Color(0x00C853);
    private static final Color QUEUE_PURPLE = new Color(0x6A0DAD);
    private static final Color PANEL_BLUE = new Color(0x3498DB);
    private static final int QUEUE_PREVIEW_LIMIT = 15;

    private EmbedFactory() {}

    public static MessageEmbed nowPlaying(AudioTrack track, String requesterName, String requesterAvatar) {
        AudioTrackInfo info = track.getInfo();
        EmbedBuilder eb = baseTrackEmbed(track, NOW_PLAYING_GREEN, "Reproduciendo")
                .setDescription("**Artista:** " + nullSafe(info.author) + "\n"
                              + "**Duracion:** " + formatTime(info.length));
        if (requesterName != null) eb.setFooter("Pedido por " + requesterName, safeUrl(requesterAvatar));
        return eb.build();
    }

    public static MessageEmbed enqueued(AudioTrack track, int positionInQueue,
                                        String requesterName, String requesterAvatar) {
        AudioTrackInfo info = track.getInfo();
        String pos = positionInQueue > 0 ? "\n**Posicion en cola:** " + positionInQueue : "";
        EmbedBuilder eb = baseTrackEmbed(track, ENQUEUED_GREEN, "En cola")
                .setDescription("**Artista:** " + nullSafe(info.author) + "\n"
                              + "**Duracion:** " + formatTime(info.length)
                              + pos);
        if (requesterName != null) eb.setFooter("Pedido por " + requesterName, safeUrl(requesterAvatar));
        return eb.build();
    }

    public static MessageEmbed queue(Scheduler scheduler) {
        AudioTrack now = scheduler.nowPlaying();
        List<AudioTrack> upcoming = scheduler.snapshot();

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Cola de reproduccion")
                .setColor(QUEUE_PURPLE);

        eb.addField("Now Playing",
                now != null ? now.getInfo().title : "_Nada_",
                false);

        if (upcoming.isEmpty()) {
            eb.addField("Proximas pistas", "_La cola esta vacia._", false);
        } else {
            StringBuilder sb = new StringBuilder();
            int max = Math.min(upcoming.size(), QUEUE_PREVIEW_LIMIT);
            for (int i = 0; i < max; i++) {
                sb.append("**").append(i + 1).append(".** ")
                  .append(upcoming.get(i).getInfo().title).append('\n');
            }
            if (upcoming.size() > max) {
                sb.append("_...y ").append(upcoming.size() - max).append(" pista(s) mas._");
            }
            eb.addField("Proximas pistas", sb.toString(), false);
        }

        eb.setFooter("Modo loop: " + scheduler.getLoopMode() + " | Volumen: " + scheduler.getVolume()
                   + " | Total: " + upcoming.size() + " pista(s) en cola");
        return eb.build();
    }

    public static MessageEmbed panel(Scheduler scheduler) {
        AudioTrack now = scheduler.nowPlaying();
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Panel de control")
                .setColor(PANEL_BLUE);

        if (now == null) {
            eb.setDescription("_Nada sonando ahora mismo._");
        } else {
            AudioTrackInfo info = now.getInfo();
            String thumb = thumbnailFor(now);
            if (thumb != null) eb.setThumbnail(thumb);

            String url = safeUrl(info.uri);
            String title = url != null ? "[" + info.title + "](" + url + ")" : info.title;

            eb.setDescription("**" + title + "**\n"
                            + "Artista: " + nullSafe(info.author) + "\n"
                            + "Progreso: " + formatTime(now.getPosition()) + " / " + formatTime(info.length));
        }

        int queued = scheduler.snapshot().size();
        eb.addField("Cola", queued == 0 ? "vacia" : queued + " pista(s)", true);
        eb.addField("Loop", scheduler.getLoopMode().name(), true);
        eb.addField("Volumen", scheduler.getVolume() + "%", true);
        return eb.build();
    }

    public static String formatTime(long millis) {
        if (millis <= 0) return "stream";
        Duration d = Duration.ofMillis(millis);
        long h = d.toHours();
        int m = d.toMinutesPart();
        int s = d.toSecondsPart();
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    private static EmbedBuilder baseTrackEmbed(AudioTrack track, Color color, String authorLabel) {
        AudioTrackInfo info = track.getInfo();
        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor(authorLabel, safeUrl(info.uri), null)
                .setTitle(info.title, safeUrl(info.uri))
                .setColor(color);
        String thumb = thumbnailFor(track);
        if (thumb != null) eb.setThumbnail(thumb);
        return eb;
    }

    private static String nullSafe(String s) {
        return s == null || s.isBlank() ? "_Desconocido_" : s;
    }

    private static String safeUrl(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            java.net.URI u = java.net.URI.create(s);
            String scheme = u.getScheme();
            if (scheme == null) return null;
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) return null;
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private static String thumbnailFor(AudioTrack track) {
        AudioTrackInfo info = track.getInfo();
        if (info.artworkUrl != null && !info.artworkUrl.isBlank()) {
            return info.artworkUrl;
        }
        String src = track.getSourceManager() != null ? track.getSourceManager().getSourceName() : "";
        if ("youtube".equalsIgnoreCase(src) && info.identifier != null) {
            return "https://img.youtube.com/vi/" + info.identifier + "/hqdefault.jpg";
        }
        return null;
    }
}
