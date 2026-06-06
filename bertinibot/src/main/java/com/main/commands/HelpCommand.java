package com.main.commands;

import java.awt.Color;

import com.main.core.SlashCommand;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class HelpCommand implements SlashCommand {

    @Override
    public SlashCommandData data() {
        return Commands.slash("help", "Lista de comandos disponibles.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var eb = new EmbedBuilder()
                .setTitle("Comandos disponibles")
                .setColor(new Color(0x3498DB))
                .addField("Musica",
                        "`/play <url|texto>` - Reproduce / encola una pista\n"
                      + "`/skip` - Salta a la siguiente pista\n"
                      + "`/queue` - Muestra la cola\n"
                      + "`/clear` - Vacia la cola y detiene\n"
                      + "`/loop <modo>` - Loop off / pista / cola\n"
                      + "`/shuffle` - Mezcla la cola\n"
                      + "`/seek <mm:ss>` - Salta a una posicion en la pista\n"
                      + "`/volume <0-150>` - Ajusta volumen\n"
                      + "`/panel` - Panel de control con botones",
                        false)
                .addField("Utilidades",
                        "`/moneda` - Lanza cara o cruz\n"
                      + "`/build <champion> [mode]` - Build de LoL desde METAsrc\n"
                      + "`/ping` - Latencia del bot\n"
                      + "`/info` - Info del bot",
                        false);
        event.replyEmbeds(eb.build()).queue();
    }
}
