package com.main.commands;

import java.util.List;

import com.main.audio.AudioService;
import com.main.audio.Scheduler;
import com.main.core.PrefixCommand;
import com.main.core.SlashCommand;
import com.main.util.EmbedFactory;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class QueueCommand implements SlashCommand, PrefixCommand {

    private final AudioService audio;

    public QueueCommand(AudioService audio) {
        this.audio = audio;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("queue", "Muestra la cola actual (paginada).");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando solo se puede usar en un servidor.").setEphemeral(true).queue();
            return;
        }
        Scheduler sched = audio.get(guild).scheduler();
        MessageEmbed embed = EmbedFactory.queuePage(sched, 0, EmbedFactory.QUEUE_PAGE_SIZE);
        ActionRow nav = QueueButtons.navRow(sched, 0);
        if (nav != null) {
            event.replyEmbeds(embed).setComponents(nav).queue();
        } else {
            event.replyEmbeds(embed).queue();
        }
    }

    @Override
    public String name() { return "queue"; }
    @Override
    public List<String> aliases() { return List.of("q", "cola"); }
    @Override
    public String usage() { return "!queue"; }
    @Override
    public String description() { return "Muestra la cola actual (paginada)."; }

    @Override
    public void execute(MessageReceivedEvent event, String args) {
        Guild guild = event.getGuild();
        Scheduler sched = audio.get(guild).scheduler();
        MessageEmbed embed = EmbedFactory.queuePage(sched, 0, EmbedFactory.QUEUE_PAGE_SIZE);
        ActionRow nav = QueueButtons.navRow(sched, 0);
        if (nav != null) {
            event.getChannel().sendMessageEmbeds(embed).setComponents(nav).queue();
        } else {
            event.getChannel().sendMessageEmbeds(embed).queue();
        }
    }
}
