package com.main.commands;

import java.awt.Color;
import java.lang.management.ManagementFactory;
import java.time.Duration;

import com.main.core.PrefixCommand;
import com.main.core.SlashCommand;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class InfoCommand implements SlashCommand, PrefixCommand {

    @Override
    public SlashCommandData data() {
        return Commands.slash("info", "Muestra informacion del bot.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.replyEmbeds(build(event.getJDA())).queue();
    }

    @Override
    public String name() { return "info"; }
    @Override
    public String usage() { return "!info"; }
    @Override
    public String description() { return "Muestra informacion del bot."; }

    @Override
    public void execute(MessageReceivedEvent event, String args) {
        event.getChannel().sendMessageEmbeds(build(event.getJDA())).queue();
    }

    private static MessageEmbed build(JDA jda) {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Duration up = Duration.ofMillis(uptimeMs);
        long usedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);

        return new EmbedBuilder()
                .setTitle("BertiniBot V2")
                .setColor(new Color(0x3498DB))
                .addField("Version", "2.0.0", true)
                .addField("Java", System.getProperty("java.version"), true)
                .addField("Uptime", up.toHoursPart() + "h " + up.toMinutesPart() + "m " + up.toSecondsPart() + "s", true)
                .addField("Servidores", String.valueOf(jda.getGuilds().size()), true)
                .addField("Memoria usada", usedMb + " MB", true)
                .addField("Latencia", jda.getGatewayPing() + " ms", true)
                .build();
    }
}
