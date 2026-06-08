package com.main.panel.gui;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Lightweight HTTP client for the bot's admin endpoint.
 *
 * <p>Only used over {@code 127.0.0.1}, so there is no auth or TLS. All calls
 * are short-timeout (1-2s) because they're on the UI thread's critical path.</p>
 */
public final class AdminClient {

    private final HttpClient http;
    private final URI baseUri;

    public AdminClient(int port) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        this.baseUri = URI.create("http://127.0.0.1:" + port);
    }

    /** Returns the parsed {@code /status} payload, or empty if the bot did not respond. */
    public Optional<RemoteStatus> fetchStatus() {
        try {
            HttpRequest req = HttpRequest.newBuilder(baseUri.resolve("/status"))
                    .timeout(Duration.ofMillis(800))
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return Optional.empty();
            return Optional.of(RemoteStatus.parse(res.body()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Posts {@code /shutdown}; returns true if the bot accepted the request. */
    public boolean requestShutdown() {
        try {
            HttpRequest req = HttpRequest.newBuilder(baseUri.resolve("/shutdown"))
                    .timeout(Duration.ofSeconds(2))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Snapshot of the bot's self-reported runtime metrics. The bot is the
     * source of truth for "is alive", so a successful parse implies the
     * process is up and the JDA gateway is at least past startup.
     */
    public record RemoteStatus(long pid, long uptimeMs, long heapUsedBytes,
                                long processRssBytes, String jvm) {

        /**
         * Very small forgiving JSON parser tailored to the bot's payload. We
         * deliberately avoid bringing in {@code org.json} so the panel jar
         * has zero runtime dependencies beyond the JDK.
         */
        public static RemoteStatus parse(String json) {
            return new RemoteStatus(
                    readLong(json, "pid"),
                    readLong(json, "uptimeMs"),
                    readLong(json, "heapUsedBytes"),
                    readLong(json, "processRssBytes"),
                    readString(json, "jvm"));
        }

        private static long readLong(String src, String key) {
            String marker = "\"" + key + "\":";
            int i = src.indexOf(marker);
            if (i < 0) return -1;
            i += marker.length();
            int end = i;
            while (end < src.length()) {
                char c = src.charAt(end);
                if (c == ',' || c == '}') break;
                end++;
            }
            try { return Long.parseLong(src.substring(i, end).trim()); }
            catch (NumberFormatException e) { return -1; }
        }

        private static String readString(String src, String key) {
            String marker = "\"" + key + "\":\"";
            int i = src.indexOf(marker);
            if (i < 0) return "";
            i += marker.length();
            int end = src.indexOf('"', i);
            if (end < 0) return "";
            return src.substring(i, end);
        }
    }
}
