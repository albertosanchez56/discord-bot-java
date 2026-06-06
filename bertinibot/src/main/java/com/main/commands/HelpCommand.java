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
                .setDescription("Puedes usarlos como slash (`/`) o con prefijo `!`.")
                .addField("Musica",
                        "`/play | !play <url|texto>` - Reproduce / encola una pista\n"
                      + "`/skip | !skip` (alias `!s`) - Salta a la siguiente pista\n"
                      + "`/queue | !queue` (alias `!q`) - Muestra la cola\n"
                      + "`/clear | !clear` (alias `!stop`) - Vacia la cola y detiene\n"
                      + "`/loop | !loop <off|track|queue>` - Modo de loop\n"
                      + "`/shuffle | !shuffle` (alias `!sh`) - Mezcla la cola\n"
                      + "`/seek | !seek <mm:ss>` - Salta a una posicion en la pista\n"
                      + "`/volume | !volume <0-150>` (alias `!vol`) - Ajusta volumen\n"
                      + "`/panel | !panel` - Panel de control con botones",
                        false)
                .addField("Utilidades",
                        "`/moneda | !moneda` (alias `!flip`) - Lanza cara o cruz\n"
                      + "`/build | !build <champion> [mode]` - Build de LoL desde METAsrc\n"
                      + "`/ping | !ping` - Latencia del bot\n"
                      + "`/info | !info` - Info del bot\n"
                      + "`/help | !help` - Esta ayuda",
                        false)
                .build();
    }
}
