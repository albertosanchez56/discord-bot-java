package com.main.spotify;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises Spotify share URLs and URIs and decomposes them into a
 * {@link SpotifyRef}.
 *
 * Accepted formats:
 * <ul>
 *   <li>{@code https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b}</li>
 *   <li>{@code https://open.spotify.com/intl-es/album/4yP0hdKOZPNshxUOjY0cZj}</li>
 *   <li>{@code https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc}</li>
 *   <li>{@code https://open.spotify.com/artist/06HL4z0CvFAxyc27GXpf02}</li>
 *   <li>{@code spotify:track:0VjIjW4GlUZAMYd2vXMi3b}</li>
 * </ul>
 */
public final class SpotifyUrlParser {

    /** Captures optional /intl-xx/ prefix and the resource kind + id. */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^/(?:intl-[a-z]{2}/)?(track|album|playlist|artist)/([A-Za-z0-9]+)/?$");

    private static final Pattern URI_PATTERN = Pattern.compile(
            "^spotify:(track|album|playlist|artist):([A-Za-z0-9]+)$");

    private SpotifyUrlParser() {}

    public static Optional<SpotifyRef> parse(String raw) {
        if (raw == null) return Optional.empty();
        String s = raw.trim();
        if (s.isEmpty()) return Optional.empty();

        // spotify:track:id URI scheme.
        Matcher uriMatch = URI_PATTERN.matcher(s);
        if (uriMatch.matches()) {
            return Optional.of(new SpotifyRef(kindOf(uriMatch.group(1)), uriMatch.group(2)));
        }

        if (!(s.startsWith("http://") || s.startsWith("https://"))) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(s);
            String host = uri.getHost();
            if (host == null) return Optional.empty();
            if (!(host.equalsIgnoreCase("open.spotify.com") || host.equalsIgnoreCase("spotify.com"))) {
                return Optional.empty();
            }
            String path = uri.getPath();
            if (path == null) return Optional.empty();
            Matcher m = URL_PATTERN.matcher(path);
            if (!m.matches()) return Optional.empty();
            return Optional.of(new SpotifyRef(kindOf(m.group(1)), m.group(2)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static boolean looksLikeSpotify(String raw) {
        if (raw == null) return false;
        String s = raw.trim().toLowerCase();
        return s.startsWith("spotify:")
                || s.contains("open.spotify.com/")
                || s.contains("spotify.com/track/")
                || s.contains("spotify.com/album/")
                || s.contains("spotify.com/playlist/")
                || s.contains("spotify.com/artist/");
    }

    private static SpotifyRef.Kind kindOf(String token) {
        return switch (token.toLowerCase()) {
            case "track" -> SpotifyRef.Kind.TRACK;
            case "album" -> SpotifyRef.Kind.ALBUM;
            case "playlist" -> SpotifyRef.Kind.PLAYLIST;
            case "artist" -> SpotifyRef.Kind.ARTIST;
            default -> throw new IllegalArgumentException("Unknown Spotify kind: " + token);
        };
    }
}
