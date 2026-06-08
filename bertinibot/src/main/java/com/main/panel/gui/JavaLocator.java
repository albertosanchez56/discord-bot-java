package com.main.panel.gui;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Locates a {@code javaw.exe} (preferred) or {@code java.exe} on Windows.
 *
 * <p>Used when the panel needs to spawn a fresh JVM to run the bot. We always
 * prefer {@code javaw.exe} because, being a GUI subsystem binary, Windows
 * does not attach a console window to it; spawning the bot that way means
 * the user never sees a stray terminal.</p>
 */
public final class JavaLocator {

    private JavaLocator() {}

    public static Optional<Path> findJavaw() {
        return find("javaw.exe").or(() -> find("java.exe"));
    }

    private static Optional<Path> find(String exe) {
        // 1. Same JVM that's running the panel.
        String home = System.getProperty("java.home");
        if (home != null && !home.isBlank()) {
            Path candidate = Paths.get(home, "bin", exe);
            if (Files.isExecutable(candidate)) return Optional.of(candidate);
        }

        // 2. Common install locations (Eclipse Adoptium, Oracle Java).
        Path[] roots = {
                Paths.get("C:\\Program Files\\Eclipse Adoptium"),
                Paths.get("C:\\Program Files\\Java"),
                Paths.get("C:\\Program Files (x86)\\Eclipse Adoptium"),
                Paths.get("C:\\Program Files (x86)\\Java")
        };
        List<Path> matches = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
                for (Path jdk : ds) {
                    Path candidate = jdk.resolve("bin").resolve(exe);
                    if (Files.isExecutable(candidate)) matches.add(candidate);
                }
            } catch (IOException ignored) {
                // Skip the root we cannot read.
            }
        }
        // Prefer the highest-versioned JDK folder name (lexicographic is
        // a decent proxy here: jdk-21 > jdk-17 > jdk-11).
        matches.sort(Comparator.comparing((Path p) -> p.getParent().getParent().getFileName().toString()).reversed());
        if (!matches.isEmpty()) return Optional.of(matches.get(0));

        // 3. PATH lookup.
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
                if (dir.isBlank()) continue;
                Path candidate = Paths.get(dir, exe);
                if (Files.isExecutable(candidate)) return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
