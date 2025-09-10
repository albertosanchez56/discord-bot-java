package com.main.service;

import com.main.audio.YtDlpManager;
import com.main.model.TrackInfo;

public class YtDlpService {
    /**
     * Obtiene el título, la URL directa de audio y el ID de YouTube.
     * Si el input es un enlace HTTP, extrae el ID de la propia URL;
     * si es una búsqueda (ytsearch1:), usa yt-dlp para obtener el ID.
     *
     * @param input URL directa o prefijo "ytsearch1:consulta".
     * @return TrackInfo con título, directUrl y videoId.
     * @throws Exception si falla la ejecución de yt-dlp.
     */
    public TrackInfo fetchTrackInfo(String input) throws Exception {
        // 1) Obtener título y URL de audio con yt-dlp
        String title     = YtDlpManager.getVideoTitle(input);
        String directUrl = YtDlpManager.getAudioUrl(input);

        // 2) Determinar ID de YouTube
        String videoId;
        if (input.startsWith("http://") || input.startsWith("https://")) {
            // Enlace directo: extraer del parámetro 'v' o último segmento
            videoId = extractVideoId(input);
        } else {
            // Búsqueda: usar yt-dlp --get-id
            videoId = YtDlpManager.getVideoId(input);
        }

        return new TrackInfo(title, directUrl, videoId);
    }

    /**
     * Extrae el parámetro 'v' o 'video_id' de la URL, o toma los últimos 11 caracteres.
     */
    private String extractVideoId(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2 && (kv[0].equals("v") || kv[0].equals("video_id"))) {
                        return kv[1];
                    }
                }
            }
        } catch (Exception ignored) {}
        // fallback: last path segment
        String[] parts = url.split("/v=");
        return parts.length > 1 ? parts[1].substring(0, 11) : null;
    }
}
