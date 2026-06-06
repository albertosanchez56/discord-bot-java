package com.main.commands;

import java.awt.Color;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class InfoCommand implements Command{
    
    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String getDescription() {
       return"Displays information about the bot.";
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Bot Information");
        embedBuilder.setDescription("Bot para reproducir canciones de youtube");
        embedBuilder.setColor(new Color(148, 0, 211));
        embedBuilder.addField("Author", "Mellkot", false);
        

        MessageEmbed embed = embedBuilder.build();
        event.replyEmbeds(embed).queue();
    }
}
