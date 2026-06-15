package com.main.commands;

import java.util.List;

import com.main.audio.AudioService;
import com.main.audio.Scheduler;
import com.main.core.PrefixCommand;
import com.main.core.SlashCommand;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * Moves a track inside the upcoming queue. Both positions are 1-based and
 * match the numbers shown by {@code /queue}.
 */
public final class MoveCommand implements SlashCommand, PrefixCommand {

    private final AudioService audio;

    public MoveCommand(AudioService audio) {
        this.audio = audio;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("move", "Mueve una pista a otra posicion de la cola.")
                .addOption(OptionType.INTEGER, "desde", "Posicion actual (1 = la primera de la cola)", true)
                .addOption(OptionType.INTEGER, "hasta", "Posicion destino (1 = al frente de la cola)", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando solo se puede usar en un servidor.").setEphemeral(true).queue();
            return;
        }
        int from = (int) event.getOption("desde").getAsLong();
        int to = (int) event.getOption("hasta").getAsLong();
        event.reply(doMove(guild, from, to)).queue();
    }

    @Override
    public String name() { return "move"; }
    @Override
    public List<String> aliases() { return List.of("mv", "mover"); }
    @Override
    public String usage() { return "!move <desde> <hasta>"; }
    @Override
    public String description() { return "Mueve la pista <desde> a la posicion <hasta>."; }

    @Override
    public void execute(MessageReceivedEvent event, String args) {
        String[] parts = args.trim().split("\\s+");
        if (parts.length < 2) {
            event.getChannel().sendMessage("Uso: `!move <desde> <hasta>` (p.ej. `!move 5 1`).").queue();
            return;
        }
        int from, to;
        try {
            from = Integer.parseInt(parts[0]);
            to = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            event.getChannel().sendMessage("Los dos parametros tienen que ser numeros enteros.").queue();
            return;
        }
        event.getChannel().sendMessage(doMove(event.getGuild(), from, to)).queue();
    }

    private String doMove(Guild guild, int from, int to) {
        Scheduler s = audio.get(guild).scheduler();
        int size = s.snapshot().size();
        if (size == 0) {
            return "La cola esta vacia.";
        }
        if (from < 1 || from > size) {
            return "Origen fuera de rango. La cola tiene **" + size + "** pista"
                    + (size == 1 ? "" : "s") + ".";
        }
        int clampedTo = Math.max(1, Math.min(to, size));
        AudioTrack moved = s.move(from, clampedTo);
        if (moved == null) {
            return "No se pudo mover la pista " + from + ".";
        }
        String suffix = (clampedTo != to)
                ? "  _(destino ajustado a " + clampedTo + " por estar fuera de rango)_"
                : "";
        return "Movida **" + moved.getInfo().title + "** de `#" + from + "` a `#" + clampedTo + "`." + suffix;
    }
}
