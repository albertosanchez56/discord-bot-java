package com.main.commands;

import com.main.audio.AudioService;
import com.main.audio.Scheduler;
import com.main.util.EmbedFactory;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/**
 * Handles clicks on the {@code /queue} pagination buttons. Custom IDs are
 * shaped as {@code queue:nav:<page>}; the {@code noop} variant is the
 * disabled label in the middle and is silently acknowledged.
 */
public final class QueueButtonHandler {

    private final AudioService audio;

    public QueueButtonHandler(AudioService audio) {
        this.audio = audio;
    }

    public void handle(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Solo en servidores.").setEphemeral(true).queue();
            return;
        }

        String id = event.getComponentId();
        String[] parts = id.split(":");
        if (parts.length < 3) {
            event.deferEdit().queue();
            return;
        }
        String payload = parts[2];
        if ("noop".equals(payload)) {
            event.deferEdit().queue();
            return;
        }
        int requestedPage;
        try {
            requestedPage = Integer.parseInt(payload);
        } catch (NumberFormatException ex) {
            event.deferEdit().queue();
            return;
        }

        Scheduler sched = audio.get(guild).scheduler();
        int size = sched.snapshot().size();
        int totalPages = Math.max(1, (size + EmbedFactory.QUEUE_PAGE_SIZE - 1) / EmbedFactory.QUEUE_PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        var editor = event.editMessageEmbeds(
                EmbedFactory.queuePage(sched, page, EmbedFactory.QUEUE_PAGE_SIZE));

        ActionRow nav = QueueButtons.navRow(sched, page);
        if (nav != null) {
            editor.setComponents(nav).queue();
        } else {
            // Queue shrank below one page since the embed was posted; drop the row.
            editor.setComponents().queue();
        }
    }
}
