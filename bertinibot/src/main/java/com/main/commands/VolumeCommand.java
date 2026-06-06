package com.main.commands;

import java.util.List;

import com.main.audio.AudioService;
import com.main.core.PrefixCommand;
import com.main.core.SlashCommand;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class VolumeCommand implements SlashCommand, PrefixCommand {

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
        apply(guild, level);
        event.reply("Volumen ajustado a **" + level + "%**.").queue();
    }

    @Override
    public String name() { return "volume"; }
    @Override
    public List<String> aliases() { return List.of("vol", "volumen"); }
    @Override
    public String usage() { return "!volume <0-150>"; }
    @Override
    public String description() { return "Ajusta el volumen de reproduccion."; }

    @Override
    public void execute(MessageReceivedEvent event, String args) {
        if (args.isBlank()) {
            int current = audio.get(event.getGuild()).scheduler().getVolume();
            event.getChannel().sendMessage("Volumen actual: **" + current + "%**. Uso: `!volume <0-150>`.").queue();
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args.trim());
        } catch (NumberFormatException ex) {
            event.getChannel().sendMessage("Nivel no valido. Usa un numero entre 0 y 150.").queue();
            return;
        }
        if (level < 0 || level > 150) {
            event.getChannel().sendMessage("El volumen debe estar entre 0 y 150.").queue();
            return;
        }
        apply(event.getGuild(), level);
        event.getChannel().sendMessage("Volumen ajustado a **" + level + "%**.").queue();
    }

    private void apply(Guild guild, int level) {
        audio.get(guild).scheduler().setVolume(level);
    }
}
