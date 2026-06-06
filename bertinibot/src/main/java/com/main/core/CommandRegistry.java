package com.main.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * Holds the set of {@link SlashCommand}s, publishes them to Discord and
 * dispatches interactions to the right handler.
 */
public final class CommandRegistry extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(CommandRegistry.class);

    private final Map<String, SlashCommand> byName = new HashMap<>();

    public void register(SlashCommand command) {
        byName.put(command.name(), command);
    }

    public void registerAll(List<SlashCommand> commands) {
        commands.forEach(this::register);
    }

    public void publish(JDA jda) {
        List<SlashCommandData> data = byName.values().stream()
                .map(SlashCommand::data)
                .toList();
        jda.updateCommands().addCommands(data).queue(
                ok -> log.info("Published {} slash command(s).", ok.size()),
                err -> log.error("Failed to publish slash commands.", err)
        );
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        SlashCommand cmd = byName.get(event.getName());
        if (cmd == null) {
            event.reply("Comando desconocido.").setEphemeral(true).queue();
            return;
        }
        try {
            cmd.execute(event);
        } catch (Exception ex) {
            log.error("Slash command /{} threw", event.getName(), ex);
            String msg = "Error interno ejecutando /" + event.getName() + ".";
            if (!event.isAcknowledged()) {
                event.reply(msg).setEphemeral(true).queue();
            } else {
                event.getHook().sendMessage(msg).setEphemeral(true).queue();
            }
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        SlashCommand cmd = byName.get(event.getName());
        if (cmd instanceof SlashCommand.AutoCompletable ac) {
            try {
                ac.autoComplete(event);
            } catch (Exception ex) {
                log.warn("Autocomplete for /{} failed: {}", event.getName(), ex.getMessage());
            }
        }
    }
}
