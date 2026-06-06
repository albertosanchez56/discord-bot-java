package com.main.commands;

import java.awt.Color;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class HelpCommand implements Command{
    
    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
       return"Muestra todos los comandos";
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
         EmbedBuilder eb = new EmbedBuilder()
            .setTitle("📖 Lista de Comandos")
            .setColor(new Color(0x9400D3));

        // Comandos con prefijo '!'
        eb.addField("__Comandos de audio `!`__", "​", false);
        eb.addField("!play <link|nombre>", "Reproduce audio desde YouTube (enlace o búsqueda).", false);
        eb.addField("!skip", "Salta a la siguiente canción en cola.", false);
        eb.addField("!clearList", "Limpia la cola de reproducción.", false);
        eb.addField("!list", "Muestra la cola de reproducción actual.", false);

        // Espacio
        eb.addBlankField(false);

        // Comandos Slash '/'
        eb.addField("__Comandos Slash `/`__", "​", false);
        eb.addField("/moneda", "Lanza una cara o cruz con animación.", false);
        eb.addField("/ping", "Comprueba la latencia del bot.", false);
        eb.addField("/info", "Muestra información del bot.", false);
        eb.addField("/echo <texto>", "Repite el texto que envíes.", false);
        eb.addField("/play <link|búsqueda>", "Reproduce audio desde YouTube usando slash command.", false);
        
        

        MessageEmbed embed = eb.build();
        event.replyEmbeds(embed).queue();
    }
}
