package com.main.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Dispatcher for {@link PrefixCommand}s.
 *
 * Listens to {@link MessageReceivedEvent}, strips the configured prefix and
 * routes by command name / alias. Bot messages, DMs and non-prefixed messages
 * are ignored. Errors thrown by a command are logged but never propagated to
 * JDA so a misbehaving command can't take down the listener thread.
 */
public final class PrefixCommandRouter extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(PrefixCommandRouter.class);
    public static final String PREFIX = "!";

    private final Map<String, PrefixCommand> byName = new HashMap<>();
    private final List<PrefixCommand> registered = new ArrayList<>();

    public void register(PrefixCommand cmd) {
        registered.add(cmd);
        byName.put(cmd.name().toLowerCase(), cmd);
        for (String alias : cmd.aliases()) {
            byName.put(alias.toLowerCase(), cmd);
        }
    }

    public void registerAll(Collection<PrefixCommand> cmds) {
        cmds.forEach(this::register);
    }

    /** Returns the unique list of registered commands (no alias duplicates). */
    public List<PrefixCommand> commands() {
        return List.copyOf(registered);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        String content = event.getMessage().getContentRaw();
        if (!content.startsWith(PREFIX)) return;

        String body = content.substring(PREFIX.length()).trim();
        if (body.isEmpty()) return;

        int sep = indexOfWhitespace(body);
        String name = (sep == -1 ? body : body.substring(0, sep)).toLowerCase();
        String args = sep == -1 ? "" : body.substring(sep + 1).trim();

        PrefixCommand cmd = byName.get(name);
        if (cmd == null) return;

        try {
            cmd.execute(event, args);
        } catch (Exception e) {
            log.error("Error in prefix command !{}: {}", name, e.getMessage(), e);
            try {
                event.getChannel().sendMessage("Error ejecutando `!" + name + "`.").queue();
            } catch (Exception ignored) {
                // Channel may have been deleted or permissions revoked.
            }
        }
    }

    private static int indexOfWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return i;
        }
        return -1;
    }
}
