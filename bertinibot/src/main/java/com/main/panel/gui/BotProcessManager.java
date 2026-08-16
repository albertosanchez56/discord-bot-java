package com.main.panel.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Start / stop / restart / inspect the BertiniBot process from the control panel.
 *
 * <p>Everything is done with pure JDK primitives ({@link ProcessHandle},
 * {@link ProcessBuilder}, {@link java.net.http.HttpClient}) so spawning the
 * panel via {@code javaw.exe} never causes a stray console window to appear:
 * we do not invoke any console-subsystem helper (no {@code tasklist}, no
 * {@code wmic}, no {@code powershell}).</p>
 */
public final class BotProcessManager {

    private final BotPaths paths;
    private final AdminClient admin;

    public BotProcessManager(BotPaths paths) {
        this.paths = paths;
        this.admin = new AdminClient(paths.adminPort());
    }

    /* -------------------------------------------------------------------- */
    /*  Process discovery                                                   */
    /* -------------------------------------------------------------------- */

    /**
     * Best-effort match for the bot process. We prefer the PID written by the
     * bot itself to {@code target/bot.pid}, falling back to scanning every
     * live process for {@code javaw} executing {@code bertinibot-*.jar}.
     */
    public Optional<ProcessHandle> findBotProcess() {
        Optional<ProcessHandle> pidMatch = readPidFile()
                .flatMap(ProcessHandle::of)
                .filter(ProcessHandle::isAlive);
        if (pidMatch.isPresent()) return pidMatch;
        return scanForBot().stream().findFirst();
    }

    public List<ProcessHandle> findAllBotProcesses() {
        // De-dupe by PID across the pid-file and the scan.
        List<ProcessHandle> result = new ArrayList<>();
        readPidFile().flatMap(ProcessHandle::of)
                .filter(ProcessHandle::isAlive)
                .ifPresent(result::add);
        for (ProcessHandle h : scanForBot()) {
            if (result.stream().noneMatch(r -> r.pid() == h.pid())) result.add(h);
        }
        return result;
    }

    private Optional<Long> readPidFile() {
        try {
            if (!Files.exists(paths.pidFile())) return Optional.empty();
            String raw = Files.readString(paths.pidFile()).trim();
            if (raw.isEmpty()) return Optional.empty();
            return Optional.of(Long.parseLong(raw));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private List<ProcessHandle> scanForBot() {
        String jarMarker = paths.jarPath().getFileName().toString().toLowerCase();
        List<ProcessHandle> matches = new ArrayList<>();
        ProcessHandle.allProcesses().forEach(h -> {
            try {
                ProcessHandle.Info info = h.info();
                String cmd = info.command().orElse("").toLowerCase();
                if (!(cmd.endsWith("javaw.exe") || cmd.endsWith("java.exe")
                        || cmd.endsWith("javaw") || cmd.endsWith("java"))) {
                    return;
                }
                // Match on either commandLine or arguments: the JDK gives us
                // one or the other depending on platform / permissions.
                String full = info.commandLine().orElse("").toLowerCase();
                if (full.contains(jarMarker)) { matches.add(h); return; }
                String[] args = info.arguments().orElse(new String[0]);
                for (String a : args) {
                    if (a != null && a.toLowerCase().contains(jarMarker)) {
                        matches.add(h);
                        return;
                    }
                }
            } catch (Exception ignored) {
                // Some PIDs aren't introspectable; skip them.
            }
        });
        return matches;
    }

    /* -------------------------------------------------------------------- */
    /*  Status                                                              */
    /* -------------------------------------------------------------------- */

    public BotStatus status(boolean taskInstalled) {
        Optional<AdminClient.RemoteStatus> remote = admin.fetchStatus();
        Optional<ProcessHandle> handle = findBotProcess();

        if (remote.isPresent()) {
            AdminClient.RemoteStatus rs = remote.get();
            Instant start = handle.flatMap(h -> h.info().startInstant()).orElseGet(() ->
                    rs.uptimeMs() >= 0
                            ? Instant.now().minusMillis(rs.uptimeMs())
                            : null);
            long mem = rs.processRssBytes() > 0 ? rs.processRssBytes() : rs.heapUsedBytes();
            return new BotStatus(true, rs.pid(), mem, start, taskInstalled);
        }
        if (handle.isPresent()) {
            ProcessHandle h = handle.get();
            return new BotStatus(true, h.pid(), -1,
                    h.info().startInstant().orElse(null), taskInstalled);
        }
        return BotStatus.offline(taskInstalled);
    }

    /* -------------------------------------------------------------------- */
    /*  Start / Stop / Restart                                              */
    /* -------------------------------------------------------------------- */

    public Result start() {
        if (findBotProcess().isPresent()) {
            return new Result(false, "El bot ya esta corriendo.");
        }
        if (!Files.exists(paths.jarPath())) {
            return new Result(false, "No existe el JAR: " + paths.jarPath()
                    + "\nCompila primero con: .\\mvnw.cmd -B -ntp -DskipTests package");
        }
        Path javaw = JavaLocator.findJavaw().orElse(null);
        if (javaw == null) {
            return new Result(false, "No se encuentra java/javaw. Instala JDK 21 (Eclipse Adoptium).");
        }
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(javaw.toString());
        cmd.addAll(SslTruststore.jvmArgs(paths.root()));
        cmd.add("-jar");
        cmd.add(paths.jarPath().toString());

        // Capture startup crashes (missing Main-Class, NoClassDefFoundError,
        // etc.) into logs/ so the panel message "no parece haber arrancado"
        // is not a black box. stdout stays discarded to avoid a console.
        Path errLog = paths.logDir().resolve("bot-stderr.log");
        try {
            Files.createDirectories(paths.logDir());
        } catch (IOException ignored) {
            // best-effort
        }

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(paths.root().toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(errLog.toFile());
        try {
            pb.start();
        } catch (IOException e) {
            return new Result(false, "No se pudo arrancar javaw: " + e.getMessage());
        }
        // Wait for it to come up. The admin endpoint becoming reachable is
        // the most reliable proof that JDA finished its handshake.
        for (int i = 0; i < 30; i++) {
            sleep(500);
            if (admin.fetchStatus().isPresent()) {
                return new Result(true, "Bot arrancado correctamente.");
            }
        }
        // Fall back to process detection: maybe the bot is up but the admin
        // endpoint is still warming.
        if (findBotProcess().isPresent()) {
            return new Result(true, "Bot arrancado (a\u00fan inicializando JDA).");
        }
        String hint = "";
        try {
            if (Files.exists(errLog) && Files.size(errLog) > 0) {
                String tail = Files.readString(errLog);
                if (tail.length() > 400) tail = tail.substring(Math.max(0, tail.length() - 400));
                hint = "\nDetalle: " + tail.trim();
            }
        } catch (IOException ignored) {}
        return new Result(false, "El bot no parece haber arrancado. Revisa los logs." + hint);
    }

    public Result stop() {
        List<ProcessHandle> procs = findAllBotProcesses();
        if (procs.isEmpty()) {
            return new Result(true, "No hay ninguna instancia del bot corriendo.");
        }

        boolean graceful = admin.requestShutdown();
        if (graceful) {
            // Give the bot ~10s to disconnect from Discord cleanly.
            for (int i = 0; i < 20; i++) {
                sleep(500);
                if (findAllBotProcesses().isEmpty()) break;
            }
        }

        List<ProcessHandle> remaining = findAllBotProcesses();
        for (ProcessHandle h : remaining) {
            try { h.destroyForcibly(); } catch (Exception ignored) {}
        }
        if (!remaining.isEmpty()) sleep(800);

        try { Files.deleteIfExists(paths.pidFile()); } catch (Exception ignored) {}

        String msg = graceful
                ? "Detenidas " + procs.size() + " instancia(s) (shutdown limpio en Discord)."
                : "Detenidas " + procs.size() + " instancia(s) (kill forzado: Discord puede tardar ~1 min en marcarlo offline).";
        return new Result(true, msg);
    }

    public Result restart() {
        Result stop = stop();
        sleep(500);
        Result start = start();
        if (!start.success()) return start;
        return new Result(true, stop.message() + "\n" + start.message());
    }

    /* -------------------------------------------------------------------- */
    /*  Helpers                                                             */
    /* -------------------------------------------------------------------- */

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    /** Outcome of a panel action, suitable for showing in the footer toast. */
    public record Result(boolean success, String message) {}
}
