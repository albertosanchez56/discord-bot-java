package com.main.commands;

import com.main.audio.AudioService;
import com.main.audio.Scheduler.LoopMode;
import com.main.core.SlashCommand;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class LoopCommand implements SlashCommand {

    private final AudioService audio;

    public LoopCommand(AudioService audio) {
        this.audio = audio;
    }

    @Override
    public SlashCommandData data() {
        OptionData mode = new OptionData(OptionType.STRING, "modo", "Modo de loop", true)
                .addChoice("apagado", "OFF")
                .addChoice("pista actual", "TRACK")
                .addChoice("cola entera", "QUEUE");
        return Commands.slash("loop", "Configura el modo de loop.").addOptions(mode);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando solo se puede usar en un servidor.").setEphemeral(true).queue();
            return;
        }
        LoopMode mode = LoopMode.valueOf(event.getOption("modo").getAsString());
        audio.get(guild).scheduler().setLoopMode(mode);
        event.reply("Loop ahora: **" + mode + "**.").queue();
    }
}
