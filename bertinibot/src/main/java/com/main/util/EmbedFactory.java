package com.main.util;

import com.main.audio.AudioTrackScheduler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Fabrica de embeds para Now Playing y Queue.
 */
public class EmbedFactory {
    /**
     * Genera un embed detallado de la pista en reproducción.
     */
    public static MessageEmbed nowPlayingEmbed(AudioTrackScheduler scheduler, User requester, String videoId, String authorIconUrl) {
        AudioTrack now = scheduler.getPlayer().getPlayingTrack();
        String title = now != null
            ? scheduler.getTitleMap().getOrDefault(now, now.getInfo().title)
            : "_Nada reproduciéndose_";
        String author = now != null ? now.getInfo().author : "";
        long durationMs = now != null ? now.getDuration() : 0;

        String thumbUrl = videoId != null
            ? "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg"
            : null;

        EmbedBuilder eb = new EmbedBuilder()
            .setAuthor(
                "Reproduciendo en BertiniBot",
                videoId != null ? "https://youtu.be/" + videoId : null,
                null
            )
            .setTitle(title)
            .setDescription("**Artista:** " + author + "\n" +
                            "**Duración:** " + formatTime(durationMs))
            .setColor(new Color(0x1DB954))
            .setTimestamp(Instant.now())
            .setFooter("Pedido por " + requester.getName(), requester.getEffectiveAvatarUrl());

        if (thumbUrl != null) eb.setThumbnail(thumbUrl);
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
        String thumb = addedVideoId != null
            ? "https://img.youtube.com/vi/" + addedVideoId + "/mqdefault.jpg"
            : null;

        String videoId = null;
        if (thumb != null) {
            try {
                String[] parts = thumb.split("/vi/");
                if (parts.length > 1) {
                    videoId = parts[1].split("/")[0];
                }
            } catch (Exception ignore) {
                videoId = null;
            }
        }

        EmbedBuilder eb = new EmbedBuilder()
        .setAuthor(
                "Reproduciendo en BertiniBot",
                videoId != null ? "https://youtu.be/" + videoId : null,
                "https://media4.giphy.com/media/v1.Y2lkPTc5MGI3NjExdDN5azNpdGtsb3ZtN29pbnE0M2x4YnkweGNmdHNxNnhwaXQ4NXduaCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/j3gsT2RsH9K0w/giphy.gif"
            )
            .setTitle("" + addedTitle)
            .setDescription("**Artista:** " + addedArtist + "\n" +
                            "**Duración:** " + formatTime(addedDurationMs))
            .setColor(new Color(0x00C853))
            .setTimestamp(Instant.now())
            .setFooter("Pedido por " + requesterName, requesterAvatarUrl);

        if (thumb != null) eb.setThumbnail(thumb);
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

        // Now Playing
        eb.addField("▶️ Now Playing", currentTitle, false);

        if (titles.isEmpty()) {
            eb.addField("Próximas pistas", "_La cola está vacía._", false);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < titles.size(); i++) {
                sb.append("**").append(i + 1).append(".** ")
                  .append(titles.get(i)).append("\n");
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
        Duration d = Duration.ofMillis(millis);
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

