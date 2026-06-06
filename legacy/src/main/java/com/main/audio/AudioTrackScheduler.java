package com.main.audio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.main.util.EmbedFactory;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class AudioTrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player; // Instancia de AudioPlayer para reproducir pistas
    private final Queue<AudioTrack> queue = new LinkedBlockingQueue<>(); // Cola FIFO de AudioTrack pendientes
    private final Map<AudioTrack, String> titleMap = new ConcurrentHashMap<>(); // Mapa pista→título
    private final Map<AudioTrack, String> thumbMap = new ConcurrentHashMap<>(); // Mapa pista→miniatura URL

    // Scheduler para programar desconexión tras inactividad
    private static final ScheduledExecutorService IDLE_SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> idleTask; // Tarea de desconexión pendiente
    private Runnable disconnectCallback; // Acción a ejecutar al estar inactivo
    private final GuildMusicManager gm;
    private boolean isFirstTrack = true;
    private final Map<AudioTrack, User> requesterMap = new ConcurrentHashMap<>();

    /**
     * Constructor: recibe el AudioPlayer que este scheduler controlará.
     */
    public AudioTrackScheduler(AudioPlayer player, GuildMusicManager manager) {
        super();
        this.player = player;
        this.gm = manager;
        // …
    }

    /**
     * Registra la acción (callback) a ejecutar cuando se detecte
     * inactividad (cola vacía y pasado el timeout).
     */
    public void setDisconnectCallback(Runnable cb) {
        this.disconnectCallback = cb;
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        // Ignoramos la primera pista para no duplicar el embed inicial
        if (isFirstTrack) {
        isFirstTrack = false;
        return;
    }

    // Recupera el requester que guardaste al encolar
    User requester = requesterMap.getOrDefault(
        track,
        gm.getTextChannel().getJDA().getSelfUser()
    );

    // Extrae videoId de thumbMap (o null si no existe)
    String thumb = thumbMap.get(track);
    String videoId = null;
    if (thumb != null && thumb.contains("/vi/")) {
        videoId = thumb.split("/vi/")[1].split("/")[0];
    }

    // URL de tu GIF animado
    String gifUrl = "https://media4.giphy.com/media/v1.Y2lkPTc5MGI3NjExdDN5azNpdGtsb3ZtN29pbnE0M2x4YnkweGNmdHNxNnhwaXQ4NXduaCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/j3gsT2RsH9K0w/giphy.gif";

    MessageChannel ch = gm.getTextChannel();
    if (ch != null) {
        ch.sendMessageEmbeds(
            EmbedFactory.nowPlayingEmbed(this, requester, videoId, gifUrl)
        ).queue();
    }
    }

    /**
     * Encola una pista con título y thumbnail personalizados.
     * Cancela cualquier desconexión programada antes de encolar.
     */
    public void queue(AudioTrack track, String title, String thumbnailUrl, User requester) {
        cancelIdleTask();
        titleMap.put(track, title);
        thumbMap.put(track, thumbnailUrl);
        requesterMap.put(track, requester);

        // Detectar si venimos de idle (no había pista en curso)
        boolean wasIdle = (player.getPlayingTrack() == null);
        if (wasIdle) {
            isFirstTrack = true;
        }
        // Sólo añadimos la miniatura si no es null:
        if (thumbnailUrl != null) {
            thumbMap.put(track, thumbnailUrl);
        }
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        }
    }

    /**
     * Sobrecarga: encola usando el título original de la pista y sin thumbnail.
     */
    public void queue(AudioTrack track, String title, User requester) {
        cancelIdleTask();
        queue(track, title, null, requester);
    }

    /**
     * Se invoca cuando una pista termina de reproducirse.
     * - Si puede empezar la siguiente (mayStartNext), la toma de la cola.
     * - Si la cola queda vacía, programa el callback de desconexión.
     */
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        // Si la pista terminó naturalmente, intentamos la siguiente
        if (endReason == AudioTrackEndReason.FINISHED) {
        AudioTrack next = queue.poll();
        if (next != null) {
            
            player.startTrack(next, false);
            return;
        }
        // Si cola vacía tras FINISHED, programar desconexión
        if (disconnectCallback != null) {
            cancelIdleTask();
            idleTask = IDLE_SCHEDULER.schedule(
                disconnectCallback,
                1, TimeUnit.MINUTES
            );
        }
    }
    }

    /**
     * Cancela la tarea de desconexión si está pendiente.
     */
    private void cancelIdleTask() {
        if (idleTask != null && !idleTask.isDone()) {
            idleTask.cancel(false);
        }
    }

    /**
     * Detiene la reproducción actual, limpia la cola, mapas de títulos/miniaturas
     * y cancela cualquier desconexión pendiente.
     */
    public void clear() {
        cancelIdleTask();
        player.stopTrack(); // Para la pista en curso
        queue.clear(); // Vacía la cola
        titleMap.clear();
        thumbMap.clear();
    }

    /**
     * Devuelve una lista con los AudioTrack pendientes en cola.
     */
    public List<AudioTrack> getQueueTracks() {
        return new ArrayList<>(queue);
    }

    /**
     * Devuelve los títulos de las pistas en cola, en orden.
     */
    public List<String> getQueueTitles() {
        List<String> list = new ArrayList<>();
        for (AudioTrack t : queue) {
            list.add(titleMap.getOrDefault(t, t.getInfo().title));
        }
        return list;
    }

    /**
     * Mapa inmutable de pista → título (incluye personalizados).
     */
    public Map<AudioTrack, String> getTitleMap() {
        return Collections.unmodifiableMap(titleMap);
    }

    /**
     * Mapa inmutable de pista → URL de miniatura.
     */
    public Map<AudioTrack, String> getThumbMap() {
        return Collections.unmodifiableMap(thumbMap);
    }

    /**
     * Acceso directo al AudioPlayer asociado.
     */
    public AudioPlayer getPlayer() {
        return player;
    }

    /**
     * Salta la pista actual y reproduce la siguiente de la cola,
     * cancelando cualquier desconexión programada.
     */
    public void skipTrack() {
        cancelIdleTask();
        player.stopTrack(); // Para la pista en curso
        AudioTrack next = queue.poll(); // Toma siguiente de la cola
        if (next != null) {
            player.startTrack(next, false);
        }else {
        // Si no hay más pistas, programar desconexión en 1 minuto
        if (disconnectCallback != null) {
            idleTask = IDLE_SCHEDULER.schedule(
                disconnectCallback,
                1, TimeUnit.MINUTES
            );
        }
    }
    }
}