package com.main.panel.gui;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Locations of the bot artefacts on disk.
 *
 * <p>The control panel is meant to live next to {@code bot.ps1} and the
 * {@code target/} directory of the Maven build, so all paths are resolved
 * relative to a single "bot root" directory. The root defaults to the working
 * directory the panel was launched from (which the launcher pins to the bot
 * folder), but it can be overridden with the {@code bertinibot.root} system
 * property.</p>
 */
public final class BotPaths {

    public static final String TASK_NAME = "BertiniBot";
    public static final String JAR_NAME = "bertinibot-2.0.0.jar";
    public static final int ADMIN_PORT = 8765;

    private final Path root;
    private final Path jarPath;
    private final Path logFile;
    private final Path pidFile;

    private BotPaths(Path root) {
        this.root = root;
        this.jarPath = root.resolve("target").resolve(JAR_NAME);
        this.logFile = root.resolve("logs").resolve("bertinibot.log");
        this.pidFile = root.resolve("target").resolve("bot.pid");
    }

    public static BotPaths detect() {
        String override = System.getProperty("bertinibot.root");
        Path root = (override != null && !override.isBlank())
                ? Paths.get(override).toAbsolutePath().normalize()
                : Paths.get("").toAbsolutePath().normalize();
        return new BotPaths(root);
    }

    public Path root()     { return root; }
    public Path jarPath()  { return jarPath; }
    public Path logFile()  { return logFile; }
    public Path pidFile()  { return pidFile; }
    public Path logDir()   { return logFile.getParent(); }
    public String taskName() { return TASK_NAME; }
    public int adminPort() { return ADMIN_PORT; }
}
