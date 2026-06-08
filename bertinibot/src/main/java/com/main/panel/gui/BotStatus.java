package com.main.panel.gui;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * UI-facing snapshot of the bot state combining process info, admin endpoint
 * data and Windows scheduled task state.
 */
public record BotStatus(boolean running,
                         long pid,
                         long memoryBytes,
                         Instant startInstant,
                         boolean taskInstalled) {

    public static BotStatus offline(boolean taskInstalled) {
        return new BotStatus(false, -1, -1, null, taskInstalled);
    }

    public Optional<Duration> uptime() {
        if (startInstant == null) return Optional.empty();
        return Optional.of(Duration.between(startInstant, Instant.now()));
    }

    public double memoryMb() {
        return memoryBytes <= 0 ? -1 : memoryBytes / (1024.0 * 1024.0);
    }
}
