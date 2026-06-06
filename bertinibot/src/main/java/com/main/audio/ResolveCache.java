package com.main.audio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Small LRU cache with TTL for resolved {@link AudioTrack}s.
 *
 * Avoids re-hitting youtube-source for repeated requests like {@code /play despacito}.
 * Entries are stored as clones (tracks are stateful: playback position, state).
 */
public final class ResolveCache {

    private static final int MAX_ENTRIES = 200;
    private static final long TTL_MS = 60L * 60L * 1000L;

    private record Entry(AudioTrack track, long expiresAt) {}

    private final Map<String, Entry> entries = new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public synchronized Optional<AudioTrack> get(String key) {
        Entry e = entries.get(key);
        if (e == null) return Optional.empty();
        if (System.currentTimeMillis() > e.expiresAt) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(e.track.makeClone());
    }

    public synchronized void put(String key, AudioTrack track) {
        entries.put(key, new Entry(track.makeClone(), System.currentTimeMillis() + TTL_MS));
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }

    /**
     * Returns a snapshot of the cache keys (most-recently-used first).
     * Used by command autocomplete to suggest previously-resolved queries.
     */
    public synchronized List<String> keysMruFirst() {
        List<String> keys = new ArrayList<>(entries.keySet());
        java.util.Collections.reverse(keys);
        return keys;
    }
}
