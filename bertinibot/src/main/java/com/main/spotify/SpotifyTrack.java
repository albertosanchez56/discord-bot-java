package com.main.spotify;

import java.util.List;

/**
 * Lightweight projection of a Spotify track: just what we need to look it
 * up on YouTube and to show a nice embed back to the user.
 */
public record SpotifyTrack(String id,
                            String title,
                            List<String> artists,
                            String albumName,
                            long durationMs,
                            String coverArtUrl,
                            String externalUrl) {

    /** Joins the artists into "A, B & C" for display. */
    public String artistsDisplay() {
        if (artists == null || artists.isEmpty()) return "Desconocido";
        if (artists.size() == 1) return artists.get(0);
        if (artists.size() == 2) return artists.get(0) + " & " + artists.get(1);
        return String.join(", ", artists.subList(0, artists.size() - 1))
                + " & " + artists.get(artists.size() - 1);
    }

    /**
     * Search query to feed to Lavaplayer's YouTube source.
     * "{primary artist} {title}" works much better than "{title}" alone:
     * disambiguates covers, remixes, and karaoke versions.
     */
    public String toYoutubeSearchQuery() {
        String primary = (artists == null || artists.isEmpty()) ? "" : artists.get(0);
        return (primary + " " + title).trim();
    }

    /**
     * Returns a copy with the given album name. Used when the source JSON
     * is an album page (where the nested track objects don't carry their
     * own {@code album}) and we want to backfill it from the parent.
     */
    public SpotifyTrack withAlbumName(String albumName) {
        if (albumName == null || albumName.equals(this.albumName)) return this;
        return new SpotifyTrack(id, title, artists, albumName, durationMs, coverArtUrl, externalUrl);
    }
}
