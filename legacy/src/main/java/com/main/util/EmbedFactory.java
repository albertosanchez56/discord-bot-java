package com.main.util;

import com.main.audio.AudioTrackScheduler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Fabrica de embeds para Now Playing y Queue.
 */
public class EmbedFactory {

    /**
     * Devuelve una URL http(s) válida para JDA o null.
     * (Evita casos como "null", espacios, esquemas raros, URLs malformadas, etc.)
     */
    private static String safeHttpUrl(String url) {
        if (url == null) return null;

        url = url.trim();
        if (url.isEmpty()) return null;
        if ("null".equalsIgnoreCase(url)) return null;

        try {
            java.net.URI u = java.net.URI.create(url);
            String scheme = u.getScheme();
            if (scheme == null) return null;

            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) return null;

            // JDA es muy tiquismiquis: si URI lo acepta, normalmente JDA también.
            return url;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Genera un embed detallado de la pista en reproducción.
     */
    public static MessageEmbed nowPlayingEmbed(
            AudioTrackScheduler scheduler,
            User requester,
            String videoId,
            String authorIconUrl // (no usado por ahora)
    ) {
        AudioTrack now = scheduler.getPlayer().getPlayingTrack();

        String title = now != null
                ? scheduler.getTitleMap().getOrDefault(now, now.getInfo().title)
                : "_Nada reproduciéndose_";

        String author = now != null ? now.getInfo().author : "";
        long durationMs = now != null ? now.getDuration() : 0;

        // Links seguros
        String videoUrl = safeHttpUrl(videoId != null ? "https://youtu.be/" + videoId : null);
        String footerIcon = safeHttpUrl(requester != null ? requester.getEffectiveAvatarUrl() : null);

        String thumbUrl = safeHttpUrl(videoId != null
                ? "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg"
                : null);

        EmbedBuilder eb = new EmbedBuilder()
                // IMPORTANTE: iconUrl del author en null para evitar errores
                .setAuthor("Reproduciendo en BertiniBot", videoUrl, null)
                .setTitle(title)
                .setDescription("**Artista:** " + author + "\n" +
                        "**Duración:** " + formatTime(durationMs))
                .setColor(new Color(0x1DB954))
                .setTimestamp(Instant.now());

        // Footer seguro
        if (requester != null) {
            eb.setFooter("Pedido por " + requester.getName(), footerIcon);
        }

        // Thumbnail seguro
        if (thumbUrl != null) {
            eb.setThumbnail(thumbUrl);
        }

        return eb.build();
    }

    /**
     * Genera un embed anunciando la pista encolada.
     */
    public static MessageEmbed enqueuedEmbed(
            String addedTitle,
            String addedArtist,
            long addedDurationMs,
            String addedVideoId,
            String requesterName,
            String requesterAvatarUrl
    ) {
        // Links seguros
        String videoUrl = safeHttpUrl((addedVideoId != null && !addedVideoId.isBlank())
                ? "https://youtu.be/" + addedVideoId
                : null);

        String footerIcon = safeHttpUrl(requesterAvatarUrl);

        String thumbUrl = safeHttpUrl((addedVideoId != null && !addedVideoId.isBlank())
                ? "https://img.youtube.com/vi/" + addedVideoId + "/mqdefault.jpg"
                : null);

        EmbedBuilder eb = new EmbedBuilder()
                // IMPORTANTE: iconUrl del author en null para evitar errores
                .setAuthor("En cola en BertiniBot", videoUrl, null)
                .setTitle(addedTitle != null ? addedTitle : "_Sin título_")
                .setDescription("**Artista:** " + (addedArtist != null ? addedArtist : "_Desconocido_") + "\n" +
                        "**Duración:** " + formatTime(addedDurationMs))
                .setColor(new Color(0x00C853))
                .setTimestamp(Instant.now());

        // Footer seguro
        if (requesterName != null && !requesterName.isBlank()) {
            eb.setFooter("Pedido por " + requesterName, footerIcon);
        }

        // Thumbnail seguro
        if (thumbUrl != null) {
            eb.setThumbnail(thumbUrl);
        }

        return eb.build();
    }

    /**
     * Genera un embed de la cola con Now Playing arriba.
     */
    public static MessageEmbed queueWithNowPlayingEmbed(
            AudioTrackScheduler scheduler,
            String currentTitle
    ) {
        List<String> titles = scheduler.getQueueTitles();

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🎶 Cola de reproducción")
                .setColor(new Color(0x6A0DAD));

        eb.addField("▶️ Now Playing", currentTitle != null ? currentTitle : "_Nada_", false);

        if (titles.isEmpty()) {
            eb.addField("Próximas pistas", "_La cola está vacía._", false);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < titles.size(); i++) {
                sb.append("**").append(i + 1).append(".** ")
                        .append(titles.get(i))
                        .append("\n");
            }
            eb.addField("Próximas pistas", sb.toString(), false);
        }

        eb.setFooter("Total: " + titles.size() + " pista(s)");
        return eb.build();
    }

    /**
     * Formatea milisegundos a HH:mm:ss o mm:ss.
     */
    private static String formatTime(long millis) {
        Duration d = Duration.ofMillis(Math.max(0, millis));
        long hours = d.toHours();
        int minutes = d.toMinutesPart();
        int seconds = d.toSecondsPart();
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}
