package com.main.audio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.nio.file.Path;

public class YtDlpManager {
    private static final String RESOURCE_PATH = "/yt-dlp.exe";
    private static final String ytdlpPath;

    static {
        try (InputStream in = YtDlpManager.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Recurso no encontrado: " + RESOURCE_PATH);
            }
            Path temp = Files.createTempFile("yt-dlp", ".exe");
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            temp.toFile().setExecutable(true);
            ytdlpPath = temp.toAbsolutePath().toString();
            System.out.println("yt-dlp extraído en: " + ytdlpPath);
        } catch (IOException e) {
            throw new ExceptionInInitializerError("No se pudo extraer yt-dlp: " + e.getMessage());
        }
    }

    /**
     * Ejecuta: yt-dlp --no-warnings -f bestaudio -g <videoUrl>
     * usando el ejecutable extraído, y devuelve la URL directa del mejor stream de
     * audio.
     */
    public static String getAudioUrl(String videoUrl) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                ytdlpPath,
                "--no-warnings",
                "-f", "bestaudio[acodec=opus]/best",
                "-g",
                videoUrl);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        if (!proc.waitFor(30, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new RuntimeException("yt-dlp tardó demasiado en responder");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("http")) {
                    return line.trim();
                }
            }
            throw new RuntimeException("No se obtuvo URL de audio de yt-dlp");
        }
    }

    /**
     * Ejecuta: yt-dlp --no-warnings -e <videoUrl>
     * devolviendo el título del vídeo.
     */
    public static String getVideoTitle(String videoUrl) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                ytdlpPath,
                "--no-warnings",
                "-e",
                videoUrl);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        if (!proc.waitFor(10, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new RuntimeException("yt-dlp tardó demasiado en responder para obtener título");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Salta líneas vacías o de advertencia
                if (!line.startsWith("WARNING")) {
                    return line.trim();
                }
            }
            throw new RuntimeException("No se obtuvo título del vídeo");
        }
    }

    public static String getVideoId(String url) throws Exception {
    ProcessBuilder pb = new ProcessBuilder("yt-dlp", "--get-id", url);
    pb.redirectErrorStream(true);
    Process proc = pb.start();
    if (!proc.waitFor(30, TimeUnit.SECONDS)) throw new RuntimeException("timeout id");
    try (BufferedReader in = 
           new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
       String line = in.readLine();
       if (line == null || line.isEmpty()) throw new RuntimeException("no id");
       return line.trim();
    }
}

}
