package com.main.core;

import java.util.List;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * Contract for text-based commands invoked with a "!" prefix.
 *
 * Lives alongside {@link SlashCommand}: a class can implement both, sharing
 * its business logic between the two delivery surfaces (slash interaction
 * and raw message). Prefix commands require the privileged
 * {@code MESSAGE_CONTENT} intent and are useful for quick typing
 * (e.g. {@code !play eminem}).
 */
public interface PrefixCommand {

    String name();

    /**
     * Optional alternative names (e.g. {@code "s"} for skip).
     * Default: no aliases.
     */
    default List<String> aliases() {
        return List.of();
    }

    /**
     * Short, one-line usage string shown by {@code !help}.
     * Example: {@code "!play <url|texto>"}.
     */
    String usage();

    /**
     * Short human description shown by {@code !help}.
     */
    String description();

    /**
     * @param event the message event that triggered the command
     * @param args  trimmed text after the command name (may be empty)
     */
    void execute(MessageReceivedEvent event, String args);
}
