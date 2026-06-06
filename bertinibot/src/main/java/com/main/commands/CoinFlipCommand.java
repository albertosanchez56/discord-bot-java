package com.main.commands;

import java.awt.Color;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.main.core.PrefixCommand;
import com.main.core.SlashCommand;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class CoinFlipCommand implements SlashCommand, PrefixCommand {

    private static final String SPIN_GIF =
            "https://media1.giphy.com/media/v1.Y2lkPTc5MGI3NjExeWVmemxvZng1ajdyd25rbTM3NG05eHFndWJiYmR3bHBxcGZndDFoYiZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/q0ejq5xiOChlS/giphy.gif";
    private static final String HEADS_GIF =
            "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExZTVjZjdwM3k4MzNhcmljZmVxaGpha2ozdmxvdzkydHFhOHdsdHJ2byZlcD12MV9naWZzX3NlYXJjaCZjdD1n/l0MYt5jPR6QX5pnqM/giphy.gif";
    private static final String TAILS_GIF =
            "https://media0.giphy.com/media/v1.Y2lkPTc5MGI3NjExZnI4aW9kNzJyYnpwY2d0cWt6N2xocXE3NDJkam9maGs4cG0wcThteSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/IglQkzvuewsoD6E1Pj/giphy.gif";

    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "coinflip-scheduler");
        t.setDaemon(true);
        return t;
    });

    @Override
    public SlashCommandData data() {
        return Commands.slash("moneda", "Lanza una moneda al aire (cara o cruz).");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.replyEmbeds(spinEmbed()).queue(hook -> hook.retrieveOriginal().queue(this::scheduleReveal));
    }

    @Override
    public String name() { return "moneda"; }
    @Override
    public List<String> aliases() { return List.of("flip", "coin"); }
    @Override
    public String usage() { return "!moneda"; }
    @Override
    public String description() { return "Lanza una moneda al aire (cara o cruz)."; }

    @Override
    public void execute(MessageReceivedEvent event, String args) {
        event.getChannel().sendMessageEmbeds(spinEmbed()).queue(this::scheduleReveal);
    }

    private void scheduleReveal(Message msg) {
        scheduler.schedule(() -> {
            boolean heads = random.nextBoolean();
            MessageEmbed result = new EmbedBuilder()
                    .setTitle("Resultado: " + (heads ? "CARA" : "CRUZ"))
                    .setColor(heads ? Color.GREEN : Color.BLUE)
                    .setImage(heads ? HEADS_GIF : TAILS_GIF)
                    .setFooter("Vuelve a probar con /moneda o !moneda")
                    .build();
            msg.editMessageEmbeds(result).queue();
        }, 3, TimeUnit.SECONDS);
    }

    private static MessageEmbed spinEmbed() {
        return new EmbedBuilder()
                .setTitle("Lanzando moneda...")
                .setColor(Color.LIGHT_GRAY)
                .setImage(SPIN_GIF)
                .setFooter("Cara o cruz")
                .build();
    }
}
