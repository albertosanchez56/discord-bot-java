package com.main.commands;

import java.awt.Color;

import com.main.core.PrefixCommand;
import com.main.core.SlashCommand;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class HelpCommand implements SlashCommand, PrefixCommand {

    @Override
    public SlashCommandData data() {
        return Commands.slash("help", "Lista de comandos disponibles.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.replyEmbeds(build()).queue();
    }

    @Override
    public String name() { return "help"; }
    @Override
    public String usage() { return "!help"; }
    @Override
    public String description() { return "Lista de comandos disponibles."; }

    @Override
    public void execute(MessageReceivedEvent event, String args) {
        event.getChannel().sendMessageEmbeds(build()).queue();
    }

    private static MessageEmbed build() {
        return new EmbedBuilder()
                .setTitle("Comandos disponibles")
                .setColor(new Color(0x3498DB))
                .setDescription("Puedes usarlos como slash (`/`) o con prefijo `!`.\n"
                        + "Entre parentesis los alias del prefijo.")
                .addField("\uD83C\uDFB5  Reproduccion",
                        "`/play | !play <url|texto>` (`!p`) - Reproduce / encola una pista. "
                        + "Acepta YouTube, busqueda libre y URLs de Spotify (track/album/playlist).\n"
                      + "`/skip | !skip` (`!s`) - Salta a la siguiente pista\n"
                      + "`/seek | !seek <mm:ss>` - Salta a una posicion en la pista\n"
                      + "`/loop | !loop <off|track|queue>` - Modo de loop\n"
                      + "`/panel | !panel` - Panel de control con botones",
                        false)
                .addField("\uD83D\uDCCB  Cola",
                        "`/queue | !queue` (`!q`, `!cola`) - Muestra la cola, paginada con botones\n"
                      + "`/remove | !remove <n>` (`!rm`, `!quitar`, `!borrar`) - Quita la pista N de la cola\n"
                      + "`/move | !move <desde> <hasta>` (`!mv`, `!mover`) - Mueve una pista de posicion\n"
                      + "`/shuffle | !shuffle` (`!sh`, `!mezcla`, `!mezclar`) - Mezcla aleatoriamente la cola\n"
                      + "`/clear | !clear` (`!stop`) - Vacia la cola y desconecta",
                        false)
                .addField("\uD83D\uDD0A  Ajustes",
                        "`/volume | !volume <0-150>` (`!vol`, `!volumen`) - Ajusta el volumen",
                        false)
                .addField("\uD83D\uDEE0\uFE0F  Utilidades",
                        "`/moneda | !moneda` (`!flip`, `!coin`) - Lanza cara o cruz\n"
                      + "`/build | !build <champion> [mode]` - Build de LoL desde METAsrc\n"
                      + "`/ping | !ping` - Latencia del bot\n"
                      + "`/info | !info` - Info del bot\n"
                      + "`/help | !help` - Esta ayuda",
                        false)
                .setFooter("Tip: los botones de la cola te llevan a la primera/anterior/siguiente/ultima pagina.")
                .build();
    }
}
