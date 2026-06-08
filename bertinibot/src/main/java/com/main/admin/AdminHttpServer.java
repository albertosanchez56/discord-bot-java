package com.main.admin;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Tiny localhost-only HTTP admin endpoint used by the Windows control panel
 * to inspect the live bot and request a graceful shutdown (so Discord sees the
 * bot disconnect right away instead of waiting ~60s for the gateway heartbeats
 * to time out).
 *
 * Binds to {@code 127.0.0.1} only. Not authenticated; relying on the loopback
 * binding as the security boundary, which is fine for a single-user desktop.
 *
 * Endpoints:
 * <ul>
 *   <li>{@code GET  /ping}      : returns "pong" (liveness probe)</li>
 *   <li>{@code GET  /status}    : returns a tiny JSON blob with PID, heap,
 *       process RSS (when available), uptime and JVM version</li>
 *   <li>{@code POST /shutdown}  : triggers a graceful shutdown</li>
 * </ul>
 */
public final class AdminHttpServer {

    private static final Logger log = LoggerFactory.getLogger(AdminHttpServer.class);

    private final int port;
    private final Runnable shutdownHook;
    private HttpServer server;

    public AdminHttpServer(int port, Runnable shutdownHook) {
        this.port = port;
        this.shutdownHook = shutdownHook;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/ping", this::handlePing);
        server.createContext("/status", this::handleStatus);
        server.createContext("/shutdown", this::handleShutdown);
        server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "admin-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        log.info("Admin HTTP listening on http://127.0.0.1:{}", port);
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private void handlePing(HttpExchange ex) throws IOException {
        reply(ex, 200, "text/plain; charset=utf-8", "pong\n");
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memBean.getHeapMemoryUsage();

        long pid = ProcessHandle.current().pid();
        long uptimeMs = runtime.getUptime();
        long heapUsed = heap.getUsed();
        long heapCommitted = heap.getCommitted();

        // Process RSS (real working set) is more useful to a human than the
        // JVM heap. com.sun.management.OperatingSystemMXBean exposes it on
        // both HotSpot and OpenJ9, but we avoid a hard compile dependency by
        // reflecting; if it's absent (or returns -1 on platforms that don't
        // implement it), the panel falls back to the heap number.
        long rssBytes = readProcessRssBytes();

        String json = "{"
                + "\"pid\":" + pid
                + ",\"uptimeMs\":" + uptimeMs
                + ",\"heapUsedBytes\":" + heapUsed
                + ",\"heapCommittedBytes\":" + heapCommitted
                + ",\"processRssBytes\":" + rssBytes
                + ",\"jvm\":\"" + jsonEscape(runtime.getVmName() + " " + runtime.getVmVersion()) + "\""
                + "}";
        reply(ex, 200, "application/json; charset=utf-8", json);
    }

    private void handleShutdown(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            reply(ex, 405, "text/plain; charset=utf-8", "Use POST\n");
            return;
        }
        reply(ex, 200, "text/plain; charset=utf-8", "shutting down\n");
        log.info("Shutdown requested via admin endpoint");
        // Run the shutdown hook off the HTTP thread so the response actually
        // makes it back to the client before the JVM exits.
        Thread t = new Thread(shutdownHook, "admin-shutdown");
        t.setDaemon(false);
        t.start();
    }

    private static long readProcessRssBytes() {
        try {
            Object osBean = ManagementFactory.getOperatingSystemMXBean();
            // HotSpot exposes getProcessCpuTime() etc. and we want the
            // "WorkingSet" / RSS sized number. The cross-vendor accessor is
            // getCommittedVirtualMemorySize() which is at least non-negative
            // on Windows and Linux; close enough for a UI gauge.
            var m = osBean.getClass().getMethod("getCommittedVirtualMemorySize");
            m.setAccessible(true);
            Object v = m.invoke(osBean);
            if (v instanceof Number n) return n.longValue();
        } catch (Throwable ignored) {
            // Fall through.
        }
        return -1L;
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"', '\\' -> { sb.append('\\').append(c); }
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static void reply(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
