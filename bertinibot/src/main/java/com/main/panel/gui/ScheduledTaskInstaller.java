package com.main.panel.gui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Manage the Windows Scheduled Task that auto-starts the bot at user login.
 *
 * <p>Uses {@code schtasks.exe} with XML payloads (so we can specify a working
 * directory, which the {@code /TR} flag does not support).</p>
 *
 * <p>Because the panel is launched through {@code javaw.exe} (no parent
 * console), the {@code schtasks} subprocess inherits "no console" and runs
 * silently in the background; no terminal window appears.</p>
 */
public final class ScheduledTaskInstaller {

    private final BotPaths paths;

    public ScheduledTaskInstaller(BotPaths paths) {
        this.paths = paths;
    }

    public boolean isInstalled() {
        ProcResult r = runSchtasks(List.of("/Query", "/TN", paths.taskName()));
        return r.exitCode() == 0;
    }

    /**
     * Returns true if a task is installed but still points at a stale java
     * binary (e.g. an old {@code java.exe} that pops a console). The panel
     * uses this to silently re-register the task at startup the first time
     * the user opens it after upgrading.
     */
    public boolean needsUpgrade() {
        ProcResult r = runSchtasks(List.of("/Query", "/TN", paths.taskName(), "/XML"));
        if (r.exitCode() != 0) return false;
        // schtasks /XML output is UTF-16; decode forgivingly.
        String xml = decodePossiblyUtf16(r.stdout());
        int i = xml.indexOf("<Command>");
        int j = xml.indexOf("</Command>", i + 1);
        if (i < 0 || j < 0) return false;
        String cmd = xml.substring(i + "<Command>".length(), j).trim().toLowerCase();
        // java.exe (console) is the bad case; javaw.exe is what we want.
        return cmd.endsWith("java.exe");
    }

    public BotProcessManager.Result install() {
        Path javaw = JavaLocator.findJavaw().orElse(null);
        if (javaw == null) {
            return new BotProcessManager.Result(false,
                    "No se encuentra javaw.exe. Instala JDK 21 (Eclipse Adoptium).");
        }

        String userId = currentUserId();
        String xml = buildTaskXml(javaw, paths.jarPath(), paths.root(), userId);

        Path tmp;
        try {
            tmp = Files.createTempFile("bertinibot-task-", ".xml");
            Files.write(tmp, encodeUtf16Le(xml));
        } catch (IOException e) {
            return new BotProcessManager.Result(false,
                    "No se pudo escribir el XML de la tarea: " + e.getMessage());
        }

        try {
            ProcResult r = runSchtasks(List.of(
                    "/Create", "/F",
                    "/TN", paths.taskName(),
                    "/XML", tmp.toString()));
            if (r.exitCode() != 0) {
                String msg = r.stderr().isBlank() ? decodePossiblyUtf16(r.stdout()) : r.stderr();
                return new BotProcessManager.Result(false,
                        "schtasks /Create fallo (" + r.exitCode() + "): " + firstLine(msg));
            }
            return new BotProcessManager.Result(true,
                    "Auto-arranque activado (Java: " + javaw + ").");
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    public BotProcessManager.Result uninstall() {
        if (!isInstalled()) {
            return new BotProcessManager.Result(true, "No habia ninguna tarea registrada.");
        }
        ProcResult r = runSchtasks(List.of(
                "/Delete", "/F", "/TN", paths.taskName()));
        if (r.exitCode() != 0) {
            String msg = r.stderr().isBlank() ? decodePossiblyUtf16(r.stdout()) : r.stderr();
            return new BotProcessManager.Result(false,
                    "schtasks /Delete fallo (" + r.exitCode() + "): " + firstLine(msg));
        }
        return new BotProcessManager.Result(true, "Auto-arranque desactivado.");
    }

    /* -------------------------------------------------------------------- */

    private static String currentUserId() {
        String domain = System.getenv("USERDOMAIN");
        String user = System.getenv("USERNAME");
        if (user == null || user.isBlank()) user = System.getProperty("user.name");
        if (domain == null || domain.isBlank() || domain.equalsIgnoreCase(user)) return user;
        return domain + "\\" + user;
    }

    private static String buildTaskXml(Path javaw, Path jar, Path workDir, String userId) {
        String xmlUser = xmlEscape(userId);
        return "<?xml version=\"1.0\" encoding=\"UTF-16\"?>\n"
                + "<Task version=\"1.2\" xmlns=\"http://schemas.microsoft.com/windows/2004/02/mit/task\">\n"
                + "  <RegistrationInfo>\n"
                + "    <Author>BertiniBot Panel</Author>\n"
                + "    <Description>BertiniBot V2 - auto-start at user logon.</Description>\n"
                + "  </RegistrationInfo>\n"
                + "  <Triggers>\n"
                + "    <LogonTrigger>\n"
                + "      <Enabled>true</Enabled>\n"
                + "      <UserId>" + xmlUser + "</UserId>\n"
                + "    </LogonTrigger>\n"
                + "  </Triggers>\n"
                + "  <Principals>\n"
                + "    <Principal id=\"Author\">\n"
                + "      <UserId>" + xmlUser + "</UserId>\n"
                + "      <LogonType>InteractiveToken</LogonType>\n"
                + "      <RunLevel>LeastPrivilege</RunLevel>\n"
                + "    </Principal>\n"
                + "  </Principals>\n"
                + "  <Settings>\n"
                + "    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>\n"
                + "    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>\n"
                + "    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>\n"
                + "    <AllowHardTerminate>true</AllowHardTerminate>\n"
                + "    <StartWhenAvailable>true</StartWhenAvailable>\n"
                + "    <Enabled>true</Enabled>\n"
                + "    <Hidden>false</Hidden>\n"
                + "    <RunOnlyIfIdle>false</RunOnlyIfIdle>\n"
                + "    <WakeToRun>false</WakeToRun>\n"
                + "    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>\n"
                + "    <Priority>7</Priority>\n"
                + "    <RestartOnFailure>\n"
                + "      <Interval>PT1M</Interval>\n"
                + "      <Count>5</Count>\n"
                + "    </RestartOnFailure>\n"
                + "  </Settings>\n"
                + "  <Actions Context=\"Author\">\n"
                + "    <Exec>\n"
                + "      <Command>" + xmlEscape(javaw.toString()) + "</Command>\n"
                + "      <Arguments>-jar &quot;" + xmlEscape(jar.toString()) + "&quot;</Arguments>\n"
                + "      <WorkingDirectory>" + xmlEscape(workDir.toString()) + "</WorkingDirectory>\n"
                + "    </Exec>\n"
                + "  </Actions>\n"
                + "</Task>\n";
    }

    private static byte[] encodeUtf16Le(String xml) {
        // schtasks /Create /XML expects a UTF-16 file with the BOM. We use
        // little-endian which is what Windows produces by default.
        byte[] bom = { (byte) 0xFF, (byte) 0xFE };
        byte[] body = xml.getBytes(StandardCharsets.UTF_16LE);
        byte[] out = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(body, 0, out, bom.length, body.length);
        return out;
    }

    private static String decodePossiblyUtf16(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String xmlEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return (nl < 0 ? s : s.substring(0, nl)).trim();
    }

    /* -------------------------------------------------------------------- */

    private static ProcResult runSchtasks(List<String> args) {
        List<String> cmd = new java.util.ArrayList<>(args.size() + 1);
        cmd.add("schtasks");
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        try {
            Process p = pb.start();
            // Drain in parallel so a verbose schtasks /Query /XML doesn't
            // deadlock on a full stdout pipe.
            byte[] outBytes;
            byte[] errBytes;
            try (var outStream = p.getInputStream();
                 var errStream = p.getErrorStream()) {
                ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
                ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
                Thread t1 = new Thread(() -> copy(outStream, outBuf));
                Thread t2 = new Thread(() -> copy(errStream, errBuf));
                t1.start(); t2.start();
                int code = p.waitFor();
                t1.join(); t2.join();
                outBytes = outBuf.toByteArray();
                errBytes = errBuf.toByteArray();
                String err = new String(errBytes, StandardCharsets.UTF_8);
                return new ProcResult(code, outBytes, err);
            }
        } catch (Exception e) {
            return new ProcResult(-1, new byte[0], e.getMessage() == null ? "" : e.getMessage());
        }
    }

    private static void copy(java.io.InputStream in, ByteArrayOutputStream out) {
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (IOException ignored) {}
    }

    private record ProcResult(int exitCode, byte[] stdout, String stderr) {}
}
