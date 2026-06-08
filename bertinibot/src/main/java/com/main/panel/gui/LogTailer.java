package com.main.panel.gui;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Incremental log file reader. Each call to {@link #poll()} returns the new
 * bytes appended since the previous call, decoded as UTF-8.
 *
 * <p>If the file is truncated or rotated (current size smaller than what we
 * have already read), the tailer resets to the beginning and signals the
 * caller via {@link Snapshot#rotated()}.</p>
 */
public final class LogTailer {

    private final Path file;
    private long lastSize = 0L;

    public LogTailer(Path file) {
        this.file = file;
    }

    public Snapshot poll() {
        if (!Files.exists(file)) {
            // Reset our cursor so we don't accidentally skip data once it
            // reappears (e.g. the bot was just started for the first time).
            if (lastSize > 0) lastSize = 0L;
            return new Snapshot("", false);
        }
        long size;
        try { size = Files.size(file); }
        catch (IOException e) { return new Snapshot("", false); }

        boolean rotated = false;
        if (size < lastSize) {
            // Either truncated (logback rolling) or recreated; replay from 0.
            lastSize = 0L;
            rotated = true;
        }
        if (size == lastSize) return new Snapshot("", rotated);

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(lastSize);
            int len = (int) Math.min(size - lastSize, 256 * 1024);
            byte[] buf = new byte[len];
            int read = raf.read(buf);
            if (read <= 0) return new Snapshot("", rotated);
            lastSize += read;
            return new Snapshot(new String(buf, 0, read, StandardCharsets.UTF_8), rotated);
        } catch (IOException e) {
            return new Snapshot("", rotated);
        }
    }

    public void resetToEnd() {
        try { lastSize = Files.exists(file) ? Files.size(file) : 0L; }
        catch (IOException e) { lastSize = 0L; }
    }

    public Path file() { return file; }

    public record Snapshot(String newText, boolean rotated) {}
}
