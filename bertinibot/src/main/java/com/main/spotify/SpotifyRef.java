package com.main.spotify;

/**
 * A parsed Spotify reference (kind + 22-char base62 id).
 *
 * @param kind which kind of resource the id points at
 * @param id   the bare resource id (no URI prefix, no URL parameters)
 */
public record SpotifyRef(Kind kind, String id) {

    public enum Kind { TRACK, ALBUM, PLAYLIST, ARTIST }
}
