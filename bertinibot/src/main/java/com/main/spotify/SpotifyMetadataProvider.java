package com.main.spotify;

/**
 * Common shape for "give me Spotify metadata" implementations.
 *
 * Two implementations exist:
 * <ul>
 *   <li>{@link SpotifyClient}: the official Web API (needs a client id /
 *       secret pair, more reliable and supports paginated playlists).</li>
 *   <li>{@link SpotifyEmbedScraper}: scrapes the public
 *       {@code open.spotify.com/embed/...} pages (no credentials required,
 *       no Premium needed). Used as the default fallback so the bot works
 *       out-of-the-box without registering a Spotify app.</li>
 * </ul>
 */
public interface SpotifyMetadataProvider {

    SpotifyTrack getTrack(String id) throws SpotifyException;

    SpotifyBundle getAlbum(String id) throws SpotifyException;

    SpotifyBundle getPlaylist(String id) throws SpotifyException;

    SpotifyBundle getArtistTop(String id) throws SpotifyException;

    /** Free-form name to surface in startup logs. */
    String displayName();
}
