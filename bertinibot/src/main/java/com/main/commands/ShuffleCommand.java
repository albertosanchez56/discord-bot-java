package com.main.commands;

import java.util.List;

import com.main.audio.AudioService;
import com.main.core.PrefixCommand;
import com.main.core.SlashCommand;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class ShuffleCommand implements SlashCommand, PrefixCommand {

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
        event.reply(doShuffle(guild)).queue();
    }

    @Override
    public String name() { return "shuffle"; }
    @Override
    public List<String> aliases() { return List.of("sh", "mezcla", "mezclar"); }
    @Override
    public String usage() { return "!shuffle"; }
    @Override
    public String description() { return "Mezcla aleatoriamente la cola."; }

    @Override
    public void execute(MessageReceivedEvent event, String args) {
        event.getChannel().sendMessage(doShuffle(event.getGuild())).queue();
    }

    private String doShuffle(Guild guild) {
        var sched = audio.get(guild).scheduler();
        int n = sched.snapshot().size();
        sched.shuffle();
        return n == 0 ? "La cola esta vacia, no hay nada que mezclar." : "Mezcladas **" + n + "** pistas.";
    }
}
