package com.main.panel.gui;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Builds a bot-local Java truststore that includes Windows antivirus MITM
 * roots (Avast, Kaspersky, etc.).
 *
 * <p>Windows apps trust those roots via the system store, but the JDK ships
 * its own {@code cacerts} and rejects HTTPS when HTTPS scanning is enabled.
 * Symptom: the bot joins voice, resolves tracks, then fails with
 * {@code PKIX path building failed}. Copying {@code cacerts} next to the bot
 * and importing the AV root fixes it without needing admin rights on the
 * global JDK truststore.</p>
 */
public final class SslTruststore {

    public static final String PASSWORD = "changeit";
    private static final String RELATIVE = "truststore/cacerts";

    private SslTruststore() {}

    /**
     * Ensures {@code <root>/truststore/cacerts} exists (creating it from the
     * running JDK's cacerts + any detected AV roots) and returns the JVM
     * flags that make the bot use it. Empty if we cannot prepare one.
     */
    public static List<String> jvmArgs(Path botRoot) {
        Optional<Path> store = ensure(botRoot);
        if (store.isEmpty()) return List.of();
        return List.of(
                "-Djavax.net.ssl.trustStore=" + store.get().toAbsolutePath(),
                "-Djavax.net.ssl.trustStorePassword=" + PASSWORD
        );
    }

    public static Optional<Path> ensure(Path botRoot) {
        Path dest = botRoot.resolve(RELATIVE).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dest.getParent());
            if (!Files.exists(dest)) {
                Path system = systemCacerts().orElse(null);
                if (system == null) return Optional.empty();
                Files.copy(system, dest);
            }
            importWindowsMitmRoots(dest);
            return Optional.of(dest);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<Path> systemCacerts() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) return Optional.empty();
        Path p = Path.of(javaHome, "lib", "security", "cacerts");
        return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    private static void importWindowsMitmRoots(Path truststore) throws Exception {
        List<X509Certificate> mitm = loadWindowsMitmRoots();
        if (mitm.isEmpty()) return;

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream in = new BufferedInputStream(Files.newInputStream(truststore))) {
            ks.load(in, PASSWORD.toCharArray());
        }

        boolean changed = false;
        int i = 0;
        for (X509Certificate cert : mitm) {
            String alias = "windows-mitm-" + i++;
            // Skip if an identical cert is already present under any alias.
            if (alreadyContains(ks, cert)) continue;
            ks.setCertificateEntry(alias, cert);
            changed = true;
        }
        if (!changed) return;

        try (OutputStream out = Files.newOutputStream(truststore)) {
            ks.store(out, PASSWORD.toCharArray());
        }
    }

    private static boolean alreadyContains(KeyStore ks, X509Certificate cert) throws Exception {
        var aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String a = aliases.nextElement();
            if (!ks.isCertificateEntry(a)) continue;
            if (cert.equals(ks.getCertificate(a))) return true;
        }
        return false;
    }

    /**
     * Pulls AV HTTPS-scanning roots from the Windows LocalMachine\Root store
     * via PowerShell (available on every Windows box we care about). Failures
     * are silent: we just skip the import and the bot keeps the stock cacerts.
     */
    private static List<X509Certificate> loadWindowsMitmRoots() {
        List<X509Certificate> out = new ArrayList<>();
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return out;
        }
        try {
            // Emit each matching cert as base64 DER on its own line. Avoids
            // temp-file races and quoting hell with Windows paths.
            String ps = String.join(" ",
                    "$pat='Avast|Kaspersky|ESET|Bitdefender|Norton|McAfee|Sophos|Fiddler|Charles|Webroot|Malwarebytes|AVG|Panda|Trend Micro|BullGuard|Avira|HTTPS Scanning|Web/Mail Shield';",
                    "Get-ChildItem Cert:\\LocalMachine\\Root | Where-Object { $_.Subject -match $pat } | ForEach-Object {",
                    "  [Convert]::ToBase64String($_.RawData)",
                    "}"
            );
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", ps);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream in = p.getInputStream();
                 java.io.BufferedReader reader = new java.io.BufferedReader(
                         new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    byte[] der = java.util.Base64.getDecoder().decode(line);
                    out.add((X509Certificate) cf.generateCertificate(
                            new java.io.ByteArrayInputStream(der)));
                }
            }
            p.waitFor();
        } catch (Exception ignored) {
            // leave out empty
        }
        return out;
    }
}
