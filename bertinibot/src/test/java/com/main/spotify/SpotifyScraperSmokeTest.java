package com.main.spotify;

/**
 * Tiny manual smoke test for {@link SpotifyEmbedScraper}.
 *
 * Not wired into the JUnit lifecycle on purpose (we don't want CI to depend
 * on Spotify uptime). Run with:
 *
 * <pre>
 *   ./mvnw.cmd -q exec:java -Dexec.mainClass=com.main.spotify.SpotifyScraperSmokeTest \
 *               -Dexec.classpathScope=test
 * </pre>
 */
public final class SpotifyScraperSmokeTest {

    public static void main(String[] args) throws Exception {
        SpotifyEmbedScraper scraper = new SpotifyEmbedScraper();

        System.out.println("== Track: Blinding Lights ==");
        SpotifyTrack t = scraper.getTrack("0VjIjW4GlUZAMYd2vXMi3b");
        System.out.println("  title   : " + t.title());
        System.out.println("  artists : " + t.artists());
        System.out.println("  album   : " + t.albumName());
        System.out.println("  duration: " + t.durationMs() + " ms");
        System.out.println("  cover   : " + t.coverArtUrl());
        System.out.println("  yt query: " + t.toYoutubeSearchQuery());

        System.out.println();
        System.out.println("== Album: After Hours ==");
        SpotifyBundle a = scraper.getAlbum("4yP0hdKOZPNshxUOjY0cZj");
        System.out.println("  name    : " + a.name());
        System.out.println("  artist  : " + a.ownerOrArtist());
        System.out.println("  tracks  : " + a.totalTracks());
        a.tracks().stream().limit(3).forEach(tr ->
                System.out.println("    - " + tr.toYoutubeSearchQuery()));

        System.out.println();
        System.out.println("== Playlist: Today's Top Hits ==");
        SpotifyBundle p = scraper.getPlaylist("37i9dQZF1DXcBWIGoYBM5M");
        System.out.println("  name    : " + p.name());
        System.out.println("  owner   : " + p.ownerOrArtist());
        System.out.println("  tracks  : " + p.totalTracks());
        p.tracks().stream().limit(3).forEach(tr ->
                System.out.println("    - " + tr.toYoutubeSearchQuery()));
    }
}
