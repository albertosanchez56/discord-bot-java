package com.main.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/**
 * Resolves configuration from environment variables first and a local
 * {@code config.properties} (classpath) as a development fallback.
 *
 * Discord token is required; everything else is optional.
 */
public final class Config {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) PROPS.load(in);
        } catch (IOException ignored) {
            // Missing dev properties file is fine; env vars take over.
        }
    }

    private Config() {}

    public static String discordToken() {
        return optional("DISCORD_TOKEN", "discord.token", "botToken")
                .orElseThrow(() -> new IllegalStateException(
                        "Missing Discord token. Set the DISCORD_TOKEN environment variable "
                                + "or define 'discord.token' in config.properties."));
    }

    public static Optional<String> guildId() {
        return optional("GUILD_ID", "discord.guild.id");
    }

    /**
     * Comma-separated list of block rules. Each rule is one or more tokens
     * joined by {@code +} (AND); a track is blocked when its
     * {@code "title | author"} contains <strong>all</strong> tokens of
     * <strong>any</strong> rule, case-insensitive.
     *
     * Defaults to {@code "roxanne+arizona"} so only "Roxanne" by Arizona
     * Zervas is rejected (returning the "aqui no hay quien viva" GIF),
     * letting through other songs that happen to be called Roxanne.
     *
     * Set {@code BLOCKED_TITLES} env var (or {@code blocked.titles} property)
     * to override, including the empty string to disable blocking entirely.
     */
    public static String blockedTitlesCsv() {
        return optional("BLOCKED_TITLES", "blocked.titles").orElse("roxanne+arizona");
    }

    private static Optional<String> optional(String envName, String... propertyKeys) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) return Optional.of(envValue.trim());
        for (String key : propertyKeys) {
            String prop = PROPS.getProperty(key);
            if (prop != null && !prop.isBlank()) return Optional.of(prop.trim());
        }
        return Optional.empty();
    }
}
