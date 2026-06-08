package com.main.spotify;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.main.util.AsyncHttp;

/**
 * Thin Spotify Web API client used to resolve tracks / albums / playlists /
 * artists into metadata the rest of the bot can turn into YouTube searches.
 *
 * <p>Uses the <strong>Client Credentials</strong> OAuth flow: no user login
 * needed, just an app registered at https://developer.spotify.com/dashboard.
 * The access token is cached in memory and refreshed lazily when expired.</p>
 *
 * <p>All calls go through {@link AsyncHttp}'s shared {@link HttpClient}, which
 * dispatches on virtual threads so concurrent resolutions (a 50-track
 * playlist, say) don't pin platform threads.</p>
 */
public final class SpotifyClient implements SpotifyMetadataProvider {

    @Override
    public String displayName() {
        return "Spotify Web API (client_credentials)";
    }

    private static final Logger log = LoggerFactory.getLogger(SpotifyClient.class);
    private static final URI TOKEN_ENDPOINT = URI.create("https://accounts.spotify.com/api/token");
    private static final String API_BASE = "https://api.spotify.com/v1";

    private final String clientId;
    private final String clientSecret;
    private final String market;
    private final HttpClient http;

    private final ReentrantLock tokenLock = new ReentrantLock();
    private volatile String accessToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public SpotifyClient(String clientId, String clientSecret, String market) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.market = (market == null || market.isBlank()) ? "ES" : market.toUpperCase();
        this.http = AsyncHttp.client();
    }

    /* ------------------------------------------------------------------ */
    /*  Public API                                                        */
    /* ------------------------------------------------------------------ */

    @Override
    public SpotifyTrack getTrack(String id) throws SpotifyException {
        JSONObject json = getJson("/tracks/" + enc(id));
        return parseTrack(json, null);
    }

    @Override
    public SpotifyBundle getAlbum(String id) throws SpotifyException {
        JSONObject album = getJson("/albums/" + enc(id));
        String name = album.optString("name", "(album)");
        String albumCover = firstImage(album.optJSONArray("images"));
        String externalUrl = externalUrl(album);
        JSONArray artistsArr = album.optJSONArray("artists");
        String mainArtist = firstArtistName(artistsArr);

        List<SpotifyTrack> tracks = new ArrayList<>();
        JSONObject page = album.optJSONObject("tracks");
        while (page != null) {
            JSONArray items = page.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    // Album track objects don't carry their own cover/album,
                    // so we backfill from the album we're iterating.
                    SpotifyTrack t = parseTrack(items.getJSONObject(i), albumCover);
                    if (t != null) tracks.add(t.withAlbumName(name));
                }
            }
            String next = page.optString("next", null);
            if (next == null || next.equals("null")) break;
            page = getJsonAbsolute(next);
        }
        int total = page != null ? page.optInt("total", tracks.size()) : tracks.size();
        return new SpotifyBundle(SpotifyRef.Kind.ALBUM, name, mainArtist,
                Math.max(total, tracks.size()), albumCover, externalUrl, tracks);
    }

    @Override
    public SpotifyBundle getPlaylist(String id) throws SpotifyException {
        JSONObject playlist = getJson("/playlists/" + enc(id));
        String name = playlist.optString("name", "(playlist)");
        String cover = firstImage(playlist.optJSONArray("images"));
        String externalUrl = externalUrl(playlist);
        String owner = "?";
        JSONObject ownerObj = playlist.optJSONObject("owner");
        if (ownerObj != null) owner = ownerObj.optString("display_name", ownerObj.optString("id", "?"));
        int total = playlist.optJSONObject("tracks") != null
                ? playlist.getJSONObject("tracks").optInt("total", 0) : 0;

        List<SpotifyTrack> tracks = new ArrayList<>();
        JSONObject page = playlist.optJSONObject("tracks");
        while (page != null) {
            JSONArray items = page.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    JSONObject track = item.optJSONObject("track");
                    // Playlist items can be null (removed tracks) or
                    // non-tracks (podcast episodes); skip them.
                    if (track == null || track.isNull("id")) continue;
                    if (!"track".equalsIgnoreCase(track.optString("type", "track"))) continue;
                    SpotifyTrack t = parseTrack(track, null);
                    if (t != null) tracks.add(t);
                }
            }
            String next = page.optString("next", null);
            if (next == null || next.equals("null")) break;
            page = getJsonAbsolute(next);
        }
        return new SpotifyBundle(SpotifyRef.Kind.PLAYLIST, name, owner,
                Math.max(total, tracks.size()), cover, externalUrl, tracks);
    }

    @Override
    public SpotifyBundle getArtistTop(String id) throws SpotifyException {
        JSONObject artist = getJson("/artists/" + enc(id));
        String name = artist.optString("name", "(artist)");
        String cover = firstImage(artist.optJSONArray("images"));
        String externalUrl = externalUrl(artist);

        JSONObject topJson = getJson("/artists/" + enc(id) + "/top-tracks?market=" + market);
        JSONArray arr = topJson.optJSONArray("tracks");
        List<SpotifyTrack> tracks = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                SpotifyTrack t = parseTrack(arr.getJSONObject(i), null);
                if (t != null) tracks.add(t);
            }
        }
        return new SpotifyBundle(SpotifyRef.Kind.ARTIST, name, name,
                tracks.size(), cover, externalUrl, tracks);
    }

    /* ------------------------------------------------------------------ */
    /*  HTTP plumbing                                                     */
    /* ------------------------------------------------------------------ */

    private JSONObject getJson(String path) throws SpotifyException {
        return getJsonAbsolute(API_BASE + path);
    }

    private JSONObject getJsonAbsolute(String url) throws SpotifyException {
        String token = ensureToken();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 401) {
                // Token expired between our cached expiry and now; force refresh once.
                tokenExpiry = Instant.EPOCH;
                token = ensureToken();
                req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                res = http.send(req, HttpResponse.BodyHandlers.ofString());
            }
            if (res.statusCode() / 100 != 2) {
                throw new SpotifyException("Spotify API " + res.statusCode() + " on " + url
                        + ": " + res.body());
            }
            return new JSONObject(res.body());
        } catch (SpotifyException e) {
            throw e;
        } catch (Exception e) {
            throw new SpotifyException("Spotify request failed (" + url + "): " + e.getMessage(), e);
        }
    }

    private String ensureToken() throws SpotifyException {
        if (Instant.now().isBefore(tokenExpiry) && accessToken != null) return accessToken;
        tokenLock.lock();
        try {
            if (Instant.now().isBefore(tokenExpiry) && accessToken != null) return accessToken;
            log.debug("Refreshing Spotify access token...");
            String basic = Base64.getEncoder().encodeToString(
                    (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(TOKEN_ENDPOINT)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new SpotifyException("Spotify token endpoint " + res.statusCode()
                        + ": " + res.body());
            }
            JSONObject body = new JSONObject(res.body());
            accessToken = body.getString("access_token");
            int expiresIn = body.optInt("expires_in", 3600);
            // Refresh 60s before the real expiry to give ourselves margin.
            tokenExpiry = Instant.now().plusSeconds(Math.max(60, expiresIn - 60));
            log.info("Got fresh Spotify token, valid for {}s", expiresIn);
            return accessToken;
        } catch (SpotifyException e) {
            throw e;
        } catch (Exception e) {
            throw new SpotifyException("Could not obtain Spotify token: " + e.getMessage(), e);
        } finally {
            tokenLock.unlock();
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Parsing helpers                                                   */
    /* ------------------------------------------------------------------ */

    private static SpotifyTrack parseTrack(JSONObject json, String fallbackCover) {
        if (json == null || json.isNull("id")) return null;
        String id = json.optString("id", "");
        String title = json.optString("name", "(unknown)");
        long durationMs = json.optLong("duration_ms", 0);
        String externalUrl = externalUrl(json);

        List<String> artists = new ArrayList<>();
        JSONArray artistsArr = json.optJSONArray("artists");
        if (artistsArr != null) {
            for (int i = 0; i < artistsArr.length(); i++) {
                JSONObject a = artistsArr.optJSONObject(i);
                if (a != null) {
                    String name = a.optString("name", "");
                    if (!name.isBlank()) artists.add(name);
                }
            }
        }

        String albumName = "";
        String cover = fallbackCover;
        JSONObject album = json.optJSONObject("album");
        if (album != null) {
            albumName = album.optString("name", "");
            if (cover == null) cover = firstImage(album.optJSONArray("images"));
        }
        return new SpotifyTrack(id, title, List.copyOf(artists),
                albumName, durationMs, cover, externalUrl);
    }

    private static String firstImage(JSONArray images) {
        if (images == null || images.isEmpty()) return null;
        JSONObject img = images.optJSONObject(0);
        if (img == null) return null;
        return img.optString("url", null);
    }

    private static String firstArtistName(JSONArray artists) {
        if (artists == null || artists.isEmpty()) return "?";
        JSONObject first = artists.optJSONObject(0);
        return first == null ? "?" : first.optString("name", "?");
    }

    private static String externalUrl(JSONObject obj) {
        JSONObject ext = obj.optJSONObject("external_urls");
        if (ext == null) return null;
        return ext.optString("spotify", null);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
