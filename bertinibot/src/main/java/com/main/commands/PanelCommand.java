package com.main.commands;

import com.main.audio.AudioService;
import com.main.audio.GuildAudio;
import com.main.core.SlashCommand;
import com.main.panel.PanelButtons;
import com.main.util.EmbedFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class PanelCommand implements SlashCommand {

    private final AudioService audio;

    public PanelCommand(AudioService audio) {
        this.audio = audio;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("panel", "Abre el panel de control con botones de reproduccion.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando solo se puede usar en un servidor.").setEphemeral(true).queue();
            return;
        }
        GuildAudio g = audio.get(guild);
        g.setTextChannel(event.getChannel());

        event.replyEmbeds(EmbedFactory.panel(g.scheduler()))
                .addComponents(
                        PanelButtons.transportRow(g.scheduler()),
                        PanelButtons.volumeRow(g.scheduler()))
                .queue(hook -> hook.retrieveOriginal().queue(msg ->
                        g.setPanel(event.getChannel().getIdLong(), msg.getIdLong())));
    }
}
