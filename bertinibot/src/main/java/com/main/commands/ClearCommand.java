package com.main.commands;

import com.main.audio.AudioService;
import com.main.core.SlashCommand;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class ClearCommand implements SlashCommand {

    private final AudioService audio;

    public ClearCommand(AudioService audio) {
        this.audio = audio;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("clear", "Vacia la cola y detiene la reproduccion.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando solo se puede usar en un servidor.").setEphemeral(true).queue();
            return;
        }
        audio.clear(guild);
        event.reply("Cola vaciada y reproduccion detenida.").queue();
    }
}
