package com.main.util;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
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

    private static final Color NOW_PLAYING_RED = new Color(0xE53935); // YouTube-ish
    private static final Color ENQUEUED_GREEN = new Color(0x00C853);
    private static final Color QUEUE_PURPLE = new Color(0x6A0DAD);
    private static final Color PANEL_BLUE = new Color(0x3498DB);
    private static final int QUEUE_PREVIEW_LIMIT = 15;
    private static final int PROGRESS_BAR_SIZE = 18;

    private EmbedFactory() {}

    public static MessageEmbed nowPlaying(AudioTrack track, String requesterName,
                                          String requesterAvatar, Scheduler scheduler) {
        AudioTrackInfo info = track.getInfo();
        String url = safeUrl(info.uri);
        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor("\u25B6  Reproduciendo ahora", url, null)
                .setTitle(info.title, url)
                .setColor(NOW_PLAYING_RED)
                .addField("Artista", "`" + nullSafe(info.author) + "`", true)
                .addField("Duracion", "`" + formatTime(info.length) + "`", true);

        if (scheduler != null) {
            int queued = scheduler.snapshot().size();
            if (queued > 0) {
                eb.addField("En cola", "`" + queued + " pista" + (queued == 1 ? "" : "s") + "`", true);
            }
        }

        String image = thumbnailFor(track);
        if (image != null) eb.setImage(image);

        if (requesterName != null) {
            eb.setFooter("Pedido por " + requesterName + "  \u00B7  usa /panel para progreso en vivo",
                         safeUrl(requesterAvatar));
        }
        eb.setTimestamp(Instant.now());
        return eb.build();
    }

    public static MessageEmbed enqueued(AudioTrack track, int positionInQueue,
                                        String requesterName, String requesterAvatar) {
        AudioTrackInfo info = track.getInfo();
        String url = safeUrl(info.uri);
        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor("\u2795  Anadida a la cola", url, null)
                .setTitle(info.title, url)
                .setColor(ENQUEUED_GREEN)
                .addField("Artista", "`" + nullSafe(info.author) + "`", true)
                .addField("Duracion", "`" + formatTime(info.length) + "`", true);

        if (positionInQueue > 0) {
            eb.addField("Posicion", "`#" + positionInQueue + "`", true);
        }

        String thumb = thumbnailFor(track);
        if (thumb != null) eb.setThumbnail(thumb);

        if (requesterName != null) {
            eb.setFooter("Pedido por " + requesterName, safeUrl(requesterAvatar));
        }
        eb.setTimestamp(Instant.now());
        return eb.build();
    }

    public static MessageEmbed queue(Scheduler scheduler) {
        AudioTrack now = scheduler.nowPlaying();
        List<AudioTrack> upcoming = scheduler.snapshot();

        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor("\uD83C\uDFB6  Cola de reproduccion", null, null)
                .setColor(QUEUE_PURPLE);

        if (now != null) {
            AudioTrackInfo info = now.getInfo();
            String url = safeUrl(info.uri);
            String title = url != null ? "[" + info.title + "](" + url + ")" : info.title;
            eb.addField("\u25B6  Sonando ahora",
                    title + "\n`" + formatTime(now.getPosition()) + " / " + formatTime(info.length) + "`",
                    false);
        } else {
            eb.addField("\u25B6  Sonando ahora", "_Nada._", false);
        }

        if (upcoming.isEmpty()) {
            eb.addField("\uD83D\uDCCB  Proximas pistas", "_La cola esta vacia._", false);
        } else {
            StringBuilder sb = new StringBuilder();
            int max = Math.min(upcoming.size(), QUEUE_PREVIEW_LIMIT);
            long totalMs = 0;
            for (int i = 0; i < max; i++) {
                AudioTrackInfo info = upcoming.get(i).getInfo();
                sb.append("`").append(String.format("%2d", i + 1)).append(".` ")
                  .append(truncate(info.title, 60))
                  .append(" `[").append(formatTime(info.length)).append("]`\n");
            }
            for (AudioTrack t : upcoming) totalMs += t.getInfo().length;
            if (upcoming.size() > max) {
                sb.append("_...y ").append(upcoming.size() - max).append(" pista(s) mas._");
            }
            eb.addField("\uD83D\uDCCB  Proximas pistas", sb.toString(), false);
            eb.addField("Total en cola", "`" + upcoming.size() + " pistas`", true);
            eb.addField("Duracion total", "`" + formatTime(totalMs) + "`", true);
        }

        eb.setFooter("Loop: " + scheduler.getLoopMode() + "  |  Volumen: " + scheduler.getVolume() + "%");
        return eb.build();
    }

    public static MessageEmbed panel(Scheduler scheduler) {
        AudioTrack now = scheduler.nowPlaying();
        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor("\uD83C\uDFB9  Panel de control", null, null)
                .setColor(PANEL_BLUE);

        if (now == null) {
            eb.setDescription("_Nada sonando ahora mismo._\nUsa `/play` o `!play` para empezar.");
        } else {
            AudioTrackInfo info = now.getInfo();
            String thumb = thumbnailFor(now);
            if (thumb != null) eb.setThumbnail(thumb);

            String url = safeUrl(info.uri);
            String title = url != null ? "[" + info.title + "](" + url + ")" : info.title;

            eb.setDescription("### " + title + "\n"
                            + "_por " + nullSafe(info.author) + "_\n\n"
                            + progressBar(now.getPosition(), info.length) + "\n"
                            + "`" + formatTime(now.getPosition()) + " / " + formatTime(info.length) + "`");
        }

        int queued = scheduler.snapshot().size();
        eb.addField("Cola", "`" + (queued == 0 ? "vacia" : queued + " pista(s)") + "`", true);
        eb.addField("Loop", "`" + scheduler.getLoopMode().name() + "`", true);
        eb.addField("Volumen", "`" + scheduler.getVolume() + "%`", true);
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

    /**
     * Renders a progress bar like {@code ▬▬▬▬🔘▬▬▬▬▬▬▬▬▬▬▬▬▬▬} using a
     * filled-track / position-marker / empty-track convention.
     */
    private static String progressBar(long positionMs, long totalMs) {
        if (totalMs <= 0) return "\uD83D\uDD34  EN VIVO";
        double ratio = Math.max(0, Math.min(1.0, (double) positionMs / (double) totalMs));
        int marker = Math.min(PROGRESS_BAR_SIZE - 1, (int) Math.round(ratio * (PROGRESS_BAR_SIZE - 1)));
        StringBuilder sb = new StringBuilder(PROGRESS_BAR_SIZE);
        for (int i = 0; i < PROGRESS_BAR_SIZE; i++) {
            if (i == marker) sb.append("\uD83D\uDD18"); // radio button
            else sb.append("\u25AC"); // black rectangle
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
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
