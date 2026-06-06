package com.main.commands;

import java.awt.Color;
import java.lang.management.ManagementFactory;
import java.time.Duration;

import com.main.core.SlashCommand;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class InfoCommand implements SlashCommand {

    @Override
    public SlashCommandData data() {
        return Commands.slash("info", "Muestra informacion del bot.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Duration up = Duration.ofMillis(uptimeMs);
        long usedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);

        var eb = new EmbedBuilder()
                .setTitle("BertiniBot V2")
                .setColor(new Color(0x3498DB))
                .addField("Version", "2.0.0", true)
                .addField("Java", System.getProperty("java.version"), true)
                .addField("Uptime", up.toHoursPart() + "h " + up.toMinutesPart() + "m " + up.toSecondsPart() + "s", true)
                .addField("Servidores", String.valueOf(event.getJDA().getGuilds().size()), true)
                .addField("Memoria usada", usedMb + " MB", true)
                .addField("Latencia", event.getJDA().getGatewayPing() + " ms", true);
        event.replyEmbeds(eb.build()).queue();
    }
}
