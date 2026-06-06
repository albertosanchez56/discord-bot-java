package com.main.commands;

import com.main.audio.AudioService;
import com.main.core.SlashCommand;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class ShuffleCommand implements SlashCommand {

    private final AudioService audio;

    public ShuffleCommand(AudioService audio) {
        this.audio = audio;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("shuffle", "Mezcla aleatoriamente la cola.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando solo se puede usar en un servidor.").setEphemeral(true).queue();
            return;
        }
        var sched = audio.get(guild).scheduler();
        int n = sched.snapshot().size();
        sched.shuffle();
        event.reply(n == 0 ? "La cola esta vacia, no hay nada que mezclar." : "Mezcladas **" + n + "** pistas.").queue();
    }
}
