package com.main.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

/**
 * Per-guild queue + loop/shuffle state on top of a Lavaplayer {@link AudioPlayer}.
 *
 * Notifies registered callbacks on track change and on becoming idle so the
 * audio service can update the panel embed and schedule disconnects.
 */
public final class Scheduler extends AudioEventAdapter {

    public enum LoopMode { OFF, TRACK, QUEUE }

    private final AudioPlayer player;
    private final ConcurrentLinkedDeque<AudioTrack> queue = new ConcurrentLinkedDeque<>();
    private volatile LoopMode loopMode = LoopMode.OFF;
    private volatile Runnable onIdle;
    private volatile Runnable onChange;

    public Scheduler(AudioPlayer player) {
        this.player = player;
    }

    public void setOnIdle(Runnable r) { this.onIdle = r; }
    public void setOnChange(Runnable r) { this.onChange = r; }

    public LoopMode getLoopMode() { return loopMode; }

    public void setLoopMode(LoopMode mode) {
        this.loopMode = mode;
        fireChange();
    }

    /**
     * Enqueue a track. If nothing is playing, starts immediately.
     */
    public void enqueue(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        }
        fireChange();
    }

    /**
     * Skip the current track. Returns true if a next track started, false if idle.
     */
    public boolean skip() {
        AudioTrack next = queue.poll();
        if (next != null) {
            player.startTrack(next, false);
            fireChange();
            return true;
        }
        player.stopTrack();
        fireIdleIfEmpty();
        return false;
    }

    public void clear() {
        queue.clear();
        loopMode = LoopMode.OFF;
        player.stopTrack();
        fireChange();
        fireIdleIfEmpty();
    }

    public void shuffle() {
        List<AudioTrack> list = new ArrayList<>(queue);
        Collections.shuffle(list);
        queue.clear();
        queue.addAll(list);
        fireChange();
    }

    public List<AudioTrack> snapshot() {
        return List.copyOf(queue);
    }

    public AudioTrack nowPlaying() {
        return player.getPlayingTrack();
    }

    public boolean isPaused() {
        return player.isPaused();
    }

    public void setPaused(boolean paused) {
        player.setPaused(paused);
        fireChange();
    }

    public int getVolume() {
        return player.getVolume();
    }

    public void setVolume(int volume) {
        player.setVolume(Math.max(0, Math.min(150, volume)));
        fireChange();
    }

    public boolean seek(long positionMs) {
        AudioTrack current = player.getPlayingTrack();
        if (current == null || !current.isSeekable()) return false;
        long clamped = Math.max(0, Math.min(positionMs, current.getDuration() - 100));
        current.setPosition(clamped);
        fireChange();
        return true;
    }

    @Override
    public void onTrackEnd(AudioPlayer p, AudioTrack track, AudioTrackEndReason reason) {
        if (!reason.mayStartNext) return;

        if (loopMode == LoopMode.TRACK) {
            player.startTrack(track.makeClone(), false);
            return;
        }

        AudioTrack next = queue.poll();
        if (next != null) {
            if (loopMode == LoopMode.QUEUE) {
                queue.offer(track.makeClone());
            }
            player.startTrack(next, false);
            fireChange();
            return;
        }

        if (loopMode == LoopMode.QUEUE) {
            player.startTrack(track.makeClone(), false);
            return;
        }

        fireIdleIfEmpty();
    }

    private void fireChange() {
        Runnable r = onChange;
        if (r != null) r.run();
    }

    private void fireIdleIfEmpty() {
        if (player.getPlayingTrack() == null && queue.isEmpty()) {
            fireChange();
            Runnable r = onIdle;
            if (r != null) r.run();
        }
    }
}
