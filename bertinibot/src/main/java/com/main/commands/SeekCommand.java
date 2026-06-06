package com.main.commands;

import com.main.audio.AudioService;
import com.main.audio.Scheduler;
import com.main.core.PrefixCommand;
import com.main.core.SlashCommand;
import com.main.util.EmbedFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class SeekCommand implements SlashCommand, PrefixCommand {

    private final AudioService audio;

    public SeekCommand(AudioService audio) {
        this.audio = audio;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("seek", "Salta a un punto de la pista actual (formato mm:ss o segundos).")
                .addOption(OptionType.STRING, "posicion", "Ejemplos: 90, 1:30, 1:02:30", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando solo se puede usar en un servidor.").setEphemeral(true).queue();
            return;
        }
        String raw = event.getOption("posicion").getAsString().trim();
        long millis;
        try {
            millis = parseToMillis(raw);
        } catch (IllegalArgumentException ex) {
            event.reply("Formato no valido: `" + raw + "`. Usa segundos, mm:ss o hh:mm:ss.")
                    .setEphemeral(true).queue();
            return;
        }
        Scheduler s = audio.get(guild).scheduler();
        if (!s.seek(millis)) {
            event.reply("No se puede saltar (no hay pista o no es seekable).").setEphemeral(true).queue();
            return;
        }
        event.reply("Saltado a **" + EmbedFactory.formatTime(millis) + "**.").queue();
    }

    @Override
    public String name() { return "seek"; }
    @Override
    public String usage() { return "!seek <pos>"; }
    @Override
    public String description() { return "Salta a una posicion (segundos, mm:ss o hh:mm:ss)."; }

    @Override
    public void execute(MessageReceivedEvent event, String args) {
        if (args.isBlank()) {
            event.getChannel().sendMessage("Uso: `!seek 90` o `!seek 1:30`.").queue();
            return;
        }
        long millis;
        try {
            millis = parseToMillis(args.trim());
        } catch (IllegalArgumentException ex) {
            event.getChannel().sendMessage("Formato no valido: `" + args + "`. Usa segundos, mm:ss o hh:mm:ss.").queue();
            return;
        }
        Scheduler s = audio.get(event.getGuild()).scheduler();
        if (!s.seek(millis)) {
            event.getChannel().sendMessage("No se puede saltar (no hay pista o no es seekable).").queue();
            return;
        }
        event.getChannel().sendMessage("Saltado a **" + EmbedFactory.formatTime(millis) + "**.").queue();
    }

    static long parseToMillis(String raw) {
        String[] parts = raw.split(":");
        long total = 0;
        for (String p : parts) {
            int v = Integer.parseInt(p);
            if (v < 0) throw new IllegalArgumentException("negative");
            total = total * 60 + v;
        }
        return total * 1000L;
    }
}
