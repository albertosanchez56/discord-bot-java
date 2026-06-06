package com.main.commands;

import com.main.core.SlashCommand;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class PingCommand implements SlashCommand {

    @Override
    public SlashCommandData data() {
        return Commands.slash("ping", "Mide la latencia con la API de Discord.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        long gateway = event.getJDA().getGatewayPing();
        event.reply("Pong. Gateway: **" + gateway + " ms**").queue();
    }
}
