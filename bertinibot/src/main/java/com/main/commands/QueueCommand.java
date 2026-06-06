package com.main.commands;

import com.main.audio.AudioService;
import com.main.core.SlashCommand;
import com.main.util.EmbedFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class QueueCommand implements SlashCommand {

    private final AudioService audio;

    public QueueCommand(AudioService audio) {
        this.audio = audio;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("queue", "Muestra la cola actual.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando solo se puede usar en un servidor.").setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(EmbedFactory.queue(audio.get(guild).scheduler())).queue();
    }
}
