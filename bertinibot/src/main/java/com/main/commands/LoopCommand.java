package com.main.commands;

import com.main.audio.AudioService;
import com.main.audio.Scheduler.LoopMode;
import com.main.core.PrefixCommand;
import com.main.core.SlashCommand;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class LoopCommand implements SlashCommand, PrefixCommand {

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

    @Override
    public String name() { return "loop"; }
    @Override
    public String usage() { return "!loop <off|track|queue>"; }
    @Override
    public String description() { return "Configura el modo de loop (off, track o queue)."; }

    @Override
    public void execute(MessageReceivedEvent event, String args) {
        LoopMode mode = parseMode(args);
        if (mode == null) {
            event.getChannel().sendMessage("Uso: `!loop <off|track|queue>` (alias `pista`, `cola`, `apagado`).").queue();
            return;
        }
        audio.get(event.getGuild()).scheduler().setLoopMode(mode);
        event.getChannel().sendMessage("Loop ahora: **" + mode + "**.").queue();
    }

    private static LoopMode parseMode(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase()) {
            case "off", "apagado", "no", "none" -> LoopMode.OFF;
            case "track", "pista", "song", "cancion", "1" -> LoopMode.TRACK;
            case "queue", "cola", "all", "todo" -> LoopMode.QUEUE;
            default -> null;
        };
    }
}
