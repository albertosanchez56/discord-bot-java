package com.main.core;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * Contract every slash command in the bot implements.
 *
 * {@link #data()} returns the registration metadata (one call at boot),
 * {@link #execute(SlashCommandInteractionEvent)} handles each interaction.
 *
 * Commands that want autocomplete suggestions implement
 * {@link AutoCompletable} in addition.
 */
public interface SlashCommand {

    SlashCommandData data();

    void execute(SlashCommandInteractionEvent event);

    default String name() {
        return data().getName();
    }

    interface AutoCompletable {
        void autoComplete(CommandAutoCompleteInteractionEvent event);
    }
}
