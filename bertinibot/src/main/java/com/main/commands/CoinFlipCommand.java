package com.main.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class CoinFlipCommand implements Command {

    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public String getName() {
        return "moneda";
    }

    @Override
    public String getDescription() {
       return"lanza una moneda.";
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
         EmbedBuilder spinEb = new EmbedBuilder()
            .setTitle("🪙 Lanzando moneda...")
            .setColor(Color.LIGHT_GRAY)
            .setImage("https://media1.giphy.com/media/v1.Y2lkPTc5MGI3NjExeWVmemxvZng1ajdyd25rbTM3NG05eHFndWJiYmR3bHBxcGZndDFoYiZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/q0ejq5xiOChlS/giphy.gif")
            .setFooter("Próximamente: Cara o Cruz!");

        // Responder con el embed inicial y luego editar
        event.replyEmbeds(spinEb.build()).queue(response -> {
            // Obtener el mensaje enviado
            response.retrieveOriginal().queue(message -> {
                // Esperar 3 segundos para simular animación
                scheduler.schedule(() -> {
                    boolean isHeads = random.nextBoolean();
                    String resultText = isHeads ? "**Cara!**" : "**Cruz!**";
                    String resultGif = isHeads
                        ? "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExZTVjZjdwM3k4MzNhcmljZmVxaGpha2ozdmxvdzkydHFhOHdsdHJ2byZlcD12MV9naWZzX3NlYXJjaCZjdD1n/l0MYt5jPR6QX5pnqM/giphy.gif"
                        : "https://media0.giphy.com/media/v1.Y2lkPTc5MGI3NjExZnI4aW9kNzJyYnpwY2d0cWt6N2xocXE3NDJkam9maGs4cG0wcThteSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/IglQkzvuewsoD6E1Pj/giphy.gif";

                    MessageEmbed resultEmbed = new EmbedBuilder()
                        .setTitle("🪙 Resultado: " + (isHeads ?  "¡CARA!" : "¡CRUZ!"))
                        //.setDescription(resultText)
                        .setColor(isHeads ? Color.GREEN : Color.BLUE)
                        .setImage(resultGif)
                        .setFooter("¡Intenta de nuevo con /moneda!")
                        .build();

                    // Editar el mensaje original con el embed de resultado
                    message.editMessageEmbeds(resultEmbed).queue();
                }, 3, TimeUnit.SECONDS);
            });
        });
    }
}
