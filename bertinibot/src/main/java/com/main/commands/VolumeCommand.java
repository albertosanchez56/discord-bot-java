package com.main.commands;

import com.main.audio.AudioService;
import com.main.core.SlashCommand;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class VolumeCommand implements SlashCommand {

    private final AudioService audio;

    public VolumeCommand(AudioService audio) {
        this.audio = audio;
    }

    @Override
    public SlashCommandData data() {
        OptionData level = new OptionData(OptionType.INTEGER, "nivel",
                "Volumen 0 (silencio) a 150 (max)", true)
                .setMinValue(0).setMaxValue(150);
        return Commands.slash("volume", "Ajusta el volumen de reproduccion.").addOptions(level);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando solo se puede usar en un servidor.").setEphemeral(true).queue();
            return;
        }
        int level = (int) event.getOption("nivel").getAsLong();
        audio.get(guild).scheduler().setVolume(level);
        event.reply("Volumen ajustado a **" + level + "%**.").queue();
    }
}
