package com.main.spotify;

import java.util.List;

/**
 * Result of resolving an album / playlist / artist on the Spotify side.
 *
 * Carries the display metadata (name, owner, cover, external URL) plus the
 * list of tracks to look up on YouTube. The list may be shorter than the
 * resource's total count if Spotify returned local-only or unavailable
 * tracks that we had to skip.
 */
public record SpotifyBundle(SpotifyRef.Kind kind,
                             String name,
                             String ownerOrArtist,
                             int totalTracks,
                             String coverArtUrl,
                             String externalUrl,
                             List<SpotifyTrack> tracks) {

    public String kindLabel() {
        return switch (kind) {
            case ALBUM -> "\u00e1lbum";
            case PLAYLIST -> "playlist";
            case ARTIST -> "artista";
            case TRACK -> "tema";
        };
    }
}
