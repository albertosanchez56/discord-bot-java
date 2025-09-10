package com.main.util;

public class TrackFilter {
    /**
     * Bloquea si el título contiene palabras prohibidas.
     */
    public static boolean isBlocked(String title) {
        return title.toLowerCase().contains("roxanne");
    }
}