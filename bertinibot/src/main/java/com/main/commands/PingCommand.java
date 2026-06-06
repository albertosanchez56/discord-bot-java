package com.main.commands;

import com.main.core.PrefixCommand;
import com.main.core.SlashCommand;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class PingCommand implements SlashCommand, PrefixCommand {

    @Override
    public SlashCommandData data() {
        return Commands.slash("ping", "Mide la latencia con la API de Discord.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.reply(format(event.getJDA().getGatewayPing())).queue();
    }

    @Override
    public String name() { return "ping"; }
    @Override
    public String usage() { return "!ping"; }
    @Override
    public String description() { return "Mide la latencia con la API de Discord."; }

    @Override
    public void execute(MessageReceivedEvent event, String args) {
        event.getChannel().sendMessage(format(event.getJDA().getGatewayPing())).queue();
    }

    private static String format(long gateway) {
        return "Pong. Gateway: **" + gateway + " ms**";
    }
}
