package com.main.commands;

import java.util.List;

import com.main.audio.AudioService;
import com.main.core.PrefixCommand;
import com.main.core.SlashCommand;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class SkipCommand implements SlashCommand, PrefixCommand {

    private final AudioService audio;

    public SkipCommand(AudioService audio) {
        this.audio = audio;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("skip", "Salta a la siguiente pista de la cola.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando solo se puede usar en un servidor.").setEphemeral(true).queue();
            return;
        }
        boolean hadNext = audio.skip(guild);
        if (hadNext) {
            // The onTrackStart hook is publishing the rich "Reproduciendo ahora"
            // embed for the new track; reply silently so we don't duplicate it.
            event.reply("\u23ED\uFE0F Saltado.").setEphemeral(true).queue();
        } else {
            event.reply("\u23ED\uFE0F Saltado. No quedan mas pistas en la cola.").queue();
        }
    }

    @Override
    public String name() { return "skip"; }
    @Override
    public List<String> aliases() { return List.of("s"); }
    @Override
    public String usage() { return "!skip"; }
    @Override
    public String description() { return "Salta a la siguiente pista de la cola."; }

    @Override
    public void execute(MessageReceivedEvent event, String args) {
        Guild guild = event.getGuild();
        boolean hadNext = audio.skip(guild);
        if (hadNext) {
            event.getMessage().addReaction(Emoji.fromUnicode("\u23ED\uFE0F")).queue(null, e -> {});
        } else {
            event.getChannel().sendMessage("\u23ED\uFE0F Saltado. No quedan mas pistas en la cola.").queue();
        }
    }
}
