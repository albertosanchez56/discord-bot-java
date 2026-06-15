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
 * Removes a single track from the upcoming queue by its 1-based position
 * (the same number shown in {@code /queue}).
 */
public final class RemoveCommand implements SlashCommand, PrefixCommand {

    private final AudioService audio;

    public RemoveCommand(AudioService audio) {
        this.audio = audio;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("remove", "Quita una pista de la cola por su posicion.")
                .addOption(OptionType.INTEGER, "posicion",
                        "Numero de pista en la cola (el que muestra /queue)", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando solo se puede usar en un servidor.").setEphemeral(true).queue();
            return;
        }
        int pos = (int) event.getOption("posicion").getAsLong();
        event.reply(doRemove(guild, pos)).queue();
    }

    @Override
    public String name() { return "remove"; }
    @Override
    public List<String> aliases() { return List.of("rm", "quitar", "borrar"); }
    @Override
    public String usage() { return "!remove <n>"; }
    @Override
    public String description() { return "Quita la pista N de la cola."; }

    @Override
    public void execute(MessageReceivedEvent event, String args) {
        int pos;
        try {
            pos = Integer.parseInt(args.trim());
        } catch (NumberFormatException ex) {
            event.getChannel().sendMessage("Uso: `!remove <numero>` (mira la posicion en `!queue`).").queue();
            return;
        }
        event.getChannel().sendMessage(doRemove(event.getGuild(), pos)).queue();
    }

    private String doRemove(Guild guild, int oneBased) {
        Scheduler s = audio.get(guild).scheduler();
        int size = s.snapshot().size();
        if (size == 0) {
            return "La cola esta vacia.";
        }
        if (oneBased < 1 || oneBased > size) {
            return "Posicion fuera de rango. La cola tiene **" + size + "** pista"
                    + (size == 1 ? "" : "s") + ".";
        }
        AudioTrack removed = s.removeAt(oneBased);
        if (removed == null) {
            return "No se pudo quitar la pista " + oneBased + ".";
        }
        return "Quitada `#" + oneBased + "` **" + removed.getInfo().title + "**.";
    }
}
