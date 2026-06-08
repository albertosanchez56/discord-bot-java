package com.main.spotify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Credentials-free Spotify metadata source.
 *
 * <p>Fetches the public embed page that Spotify itself serves to its
 * {@code <iframe>} widgets ({@code https://open.spotify.com/embed/<type>/<id>})
 * and reads the {@code __NEXT_DATA__} blob baked into the HTML. That blob
 * contains the same metadata the official Web API would return (title,
 * artists, album, duration, cover art, trackList for albums/playlists),
 * so we can power Spotify URL playback without ever logging into the
 * Developer Dashboard.</p>
 *
 * <p>Limitations vs. the Web API:</p>
 * <ul>
 *   <li>Playlist / album track lists are capped at whatever Spotify decides
 *       to embed in the page (typically the first 100 tracks). Longer
 *       resources get silently truncated.</li>
 *   <li>{@code /artist/} URLs don't include top-tracks in the embed; this
 *       method throws so the caller can report a clear error.</li>
 *   <li>It's HTML scraping, so if Spotify changes the embed structure the
 *       parser needs an update. We try to be defensive about field names.</li>
 * </ul>
 */
public final class SpotifyEmbedScraper implements SpotifyMetadataProvider {

    private static final Logger log = LoggerFactory.getLogger(SpotifyEmbedScraper.class);
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36";

    @Override
    public String displayName() {
        return "Spotify embed scraper (no credentials)";
    }

    @Override
    public SpotifyTrack getTrack(String id) throws SpotifyException {
        JSONObject entity = fetchEntity("track", id);
        return parseTrackEntity(entity, null);
    }

    @Override
    public SpotifyBundle getAlbum(String id) throws SpotifyException {
        JSONObject entity = fetchEntity("album", id);
        String name = stringField(entity, "name", "title", "(album)");
        String mainArtist = firstSubtitleOrArtist(entity);
        String cover = firstCover(entity);
        String externalUrl = "https://open.spotify.com/album/" + id;

        List<SpotifyTrack> tracks = readTrackList(entity, name, cover);
        return new SpotifyBundle(SpotifyRef.Kind.ALBUM, name, mainArtist,
                tracks.size(), cover, externalUrl, tracks);
    }

    @Override
    public SpotifyBundle getPlaylist(String id) throws SpotifyException {
        JSONObject entity = fetchEntity("playlist", id);
        String name = stringField(entity, "name", "title", "(playlist)");
        String owner = firstSubtitleOrArtist(entity);
        String cover = firstCover(entity);
        String externalUrl = "https://open.spotify.com/playlist/" + id;

        List<SpotifyTrack> tracks = readTrackList(entity, null, null);
        return new SpotifyBundle(SpotifyRef.Kind.PLAYLIST, name, owner,
                tracks.size(), cover, externalUrl, tracks);
    }

    @Override
    public SpotifyBundle getArtistTop(String id) throws SpotifyException {
        // The /embed/artist/ page does not include a track list, so we have
        // no way to extract the top 10 without authenticated API access.
        // Throw a friendly error so the user knows to use a track/album/
        // playlist URL or to register a Spotify app.
        throw new SpotifyException(
                "URLs de /artist/ requieren las credenciales de Spotify (gratis "
                + "en developer.spotify.com/dashboard si tienes Premium). "
                + "Mientras tanto, copia la URL de una cancion o playlist concreta del artista.");
    }

    /* ------------------------------------------------------------------ */
    /*  HTML fetch + JSON extraction                                      */
    /* ------------------------------------------------------------------ */

    private JSONObject fetchEntity(String type, String id) throws SpotifyException {
        String url = "https://open.spotify.com/embed/" + type + "/" + id;
        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .userAgent(UA)
                    .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                    .timeout(10_000)
                    .followRedirects(true)
                    .get();
        } catch (IOException e) {
            throw new SpotifyException("No se pudo descargar la pagina de Spotify: " + e.getMessage(), e);
        }

        Element nextData = doc.getElementById("__NEXT_DATA__");
        if (nextData == null) {
            throw new SpotifyException("La pagina de Spotify no contiene __NEXT_DATA__. "
                    + "Puede que Spotify haya cambiado el formato o que el id no exista.");
        }
        JSONObject root;
        try {
            root = new JSONObject(nextData.data());
        } catch (Exception e) {
            throw new SpotifyException("__NEXT_DATA__ no es JSON valido: " + e.getMessage(), e);
        }
        JSONObject entity = digEntity(root);
        if (entity == null) {
            throw new SpotifyException("No encuentro la entidad en __NEXT_DATA__ para " + type + "/" + id);
        }
        return entity;
    }

    /**
     * Spotify shuffles the inner shape of __NEXT_DATA__ around occasionally,
     * so we walk a couple of known paths defensively before giving up.
     */
    private static JSONObject digEntity(JSONObject root) {
        // props.pageProps.state.data.entity
        JSONObject entity = path(root, "props", "pageProps", "state", "data", "entity");
        if (entity != null) return entity;
        // props.pageProps.props.state.data.entity
        entity = path(root, "props", "pageProps", "props", "state", "data", "entity");
        if (entity != null) return entity;
        // props.pageProps.data.entity (some variants)
        entity = path(root, "props", "pageProps", "data", "entity");
        if (entity != null) return entity;
        // last resort: scan the tree for a sub-object that looks like one.
        return findEntityRecursive(root, 0);
    }

    private static JSONObject path(JSONObject obj, String... keys) {
        JSONObject cur = obj;
        for (String key : keys) {
            if (cur == null) return null;
            cur = cur.optJSONObject(key);
        }
        return cur;
    }

    /** Find any sub-object that has a "trackList" or ("name" + "type"). */
    private static JSONObject findEntityRecursive(JSONObject node, int depth) {
        if (depth > 8 || node == null) return null;
        if (node.has("trackList") || (node.has("name") && node.has("type"))) {
            return node;
        }
        for (String key : node.keySet()) {
            Object v = node.opt(key);
            if (v instanceof JSONObject o) {
                JSONObject hit = findEntityRecursive(o, depth + 1);
                if (hit != null) return hit;
            } else if (v instanceof JSONArray arr) {
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (item instanceof JSONObject o) {
                        JSONObject hit = findEntityRecursive(o, depth + 1);
                        if (hit != null) return hit;
                    }
                }
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ */
    /*  Entity -> domain                                                  */
    /* ------------------------------------------------------------------ */

    private static SpotifyTrack parseTrackEntity(JSONObject entity, String fallbackAlbum) {
        String title = stringField(entity, "name", "title", "(unknown)");
        long duration = entity.optLong("duration", entity.optLong("durationMs", 0));
        String cover = firstCover(entity);
        String externalUrl = readExternalUrl(entity);

        List<String> artists = readArtists(entity);
        String album = stringField(entity, "albumName", "subtitle", fallbackAlbum != null ? fallbackAlbum : "");

        String id = readId(entity);
        return new SpotifyTrack(id, title, List.copyOf(artists),
                album == null ? "" : album, duration, cover, externalUrl);
    }

    /**
     * Reads the embed-style trackList[] block (used by album / playlist
     * entities). Each item carries a {@code title}, {@code subtitle}
     * (artist), {@code duration} (ms) and a {@code uri} like
     * {@code spotify:track:xyz}.
     */
    private static List<SpotifyTrack> readTrackList(JSONObject entity,
                                                     String fallbackAlbumName,
                                                     String fallbackCover) {
        List<SpotifyTrack> out = new ArrayList<>();
        JSONArray list = entity.optJSONArray("trackList");
        if (list == null) {
            JSONObject tracks = entity.optJSONObject("tracks");
            if (tracks != null) list = tracks.optJSONArray("items");
        }
        if (list == null) return out;

        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            // playlist items sometimes wrap the real track in .track
            if (item.has("track") && item.optJSONObject("track") != null) {
                item = item.getJSONObject("track");
            }
            String title = stringField(item, "title", "name", null);
            if (title == null) continue;
            long duration = item.optLong("duration", item.optLong("durationMs", 0));
            String cover = firstCover(item);
            if (cover == null) cover = fallbackCover;
            String id = readId(item);
            String externalUrl = readExternalUrl(item);

            List<String> artists = readArtists(item);
            if (artists.isEmpty()) {
                String subtitle = item.optString("subtitle", "");
                if (!subtitle.isBlank()) artists.add(subtitle);
            }

            out.add(new SpotifyTrack(id, title, List.copyOf(artists),
                    fallbackAlbumName == null ? "" : fallbackAlbumName,
                    duration, cover, externalUrl));
        }
        return out;
    }

    private static List<String> readArtists(JSONObject entity) {
        List<String> artists = new ArrayList<>();
        JSONArray arr = entity.optJSONArray("artists");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject a = arr.optJSONObject(i);
                if (a == null) continue;
                String n = a.optString("name", a.optString("title", ""));
                if (!n.isBlank()) artists.add(n);
            }
            if (!artists.isEmpty()) return artists;
        }
        // fallback: subtitle "Artist 1, Artist 2 - Album"
        String subtitle = entity.optString("subtitle", "");
        if (!subtitle.isBlank()) {
            int dash = subtitle.indexOf(" \u2022 "); // bullet, used in some embeds
            String head = dash > 0 ? subtitle.substring(0, dash) : subtitle;
            for (String chunk : head.split(",")) {
                String t = chunk.trim();
                if (!t.isBlank()) artists.add(t);
            }
        }
        return artists;
    }

    private static String firstSubtitleOrArtist(JSONObject entity) {
        List<String> artists = readArtists(entity);
        if (!artists.isEmpty()) return artists.get(0);
        return entity.optString("subtitle", "?");
    }

    private static String firstCover(JSONObject entity) {
        // 1) coverArt.sources[] (used by some album / playlist embeds)
        JSONObject coverArt = entity.optJSONObject("coverArt");
        if (coverArt != null) {
            String url = pickLargestImage(coverArt.optJSONArray("sources"));
            if (url != null) return url;
        }
        // 2) visualIdentity.image[] (used by track / artist embeds)
        JSONObject vi = entity.optJSONObject("visualIdentity");
        if (vi != null) {
            String url = pickLargestImage(vi.optJSONArray("image"));
            if (url != null) return url;
        }
        // 3) images[] (Spotify Web API style)
        String url = pickLargestImage(entity.optJSONArray("images"));
        if (url != null) return url;
        return null;
    }

    /** Returns the largest image URL from an array of {url, maxWidth/width, maxHeight/height}. */
    private static String pickLargestImage(JSONArray arr) {
        if (arr == null || arr.length() == 0) return null;
        String best = null;
        int bestSize = -1;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject img = arr.optJSONObject(i);
            if (img == null) continue;
            String u = img.optString("url", null);
            if (u == null || u.isBlank()) continue;
            int w = img.optInt("maxWidth", img.optInt("width", 0));
            int h = img.optInt("maxHeight", img.optInt("height", 0));
            int size = Math.max(w, h);
            if (size > bestSize) {
                bestSize = size;
                best = u;
            }
        }
        return best;
    }

    private static String readId(JSONObject entity) {
        String uri = entity.optString("uri", "");
        if (!uri.isBlank() && uri.contains(":")) {
            return uri.substring(uri.lastIndexOf(':') + 1);
        }
        return entity.optString("id", "");
    }

    private static String readExternalUrl(JSONObject entity) {
        String uri = entity.optString("uri", "");
        if (uri.startsWith("spotify:track:")) {
            return "https://open.spotify.com/track/" + uri.substring("spotify:track:".length());
        }
        if (uri.startsWith("spotify:album:")) {
            return "https://open.spotify.com/album/" + uri.substring("spotify:album:".length());
        }
        if (uri.startsWith("spotify:playlist:")) {
            return "https://open.spotify.com/playlist/" + uri.substring("spotify:playlist:".length());
        }
        return null;
    }

    private static String stringField(JSONObject obj, String key1, String key2, String fallback) {
        String v = obj.optString(key1, "");
        if (!v.isBlank()) return v;
        v = obj.optString(key2, "");
        if (!v.isBlank()) return v;
        return fallback;
    }

    /* For debugging during development. Wired through a logger only. */
    @SuppressWarnings("unused")
    private static void debug(JSONObject root) {
        log.debug("Spotify __NEXT_DATA__ root keys: {}", root.keySet());
    }
}
