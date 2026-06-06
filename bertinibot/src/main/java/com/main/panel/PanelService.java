package com.main.panel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.main.audio.AudioService;
import com.main.audio.GuildAudio;
import com.main.util.EmbedFactory;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

/**
 * Owns the lifecycle of the per-guild control panel message.
 *
 * Edit-in-place strategy: a guild has at most one panel at any time. When a
 * track changes (via {@link AudioService} state callback), the panel is
 * re-rendered into the existing message instead of spamming new ones.
 */
public final class PanelService {

    private static final Logger log = LoggerFactory.getLogger(PanelService.class);

    private final AudioService audio;
    private volatile JDA jda;

    public PanelService(AudioService audio) {
        this.audio = audio;
    }

    public void bind(JDA jda) {
        this.jda = jda;
    }

    /**
     * Refresh the existing panel message for the given guild, if any.
     * No-op when there is no registered panel.
     */
    public void update(Guild guild) {
        if (jda == null) return;
        GuildAudio g = audio.get(guild);
        Long msgId = g.panelMessageId();
        Long chId = g.panelChannelId();
        if (msgId == null || chId == null) return;

        TextChannel channel = jda.getTextChannelById(chId);
        if (channel == null) return;

        channel.editMessageEmbedsById(msgId, EmbedFactory.panel(g.scheduler()))
                .setComponents(
                        PanelButtons.transportRow(g.scheduler()),
                        PanelButtons.volumeRow(g.scheduler()))
                .queue(null, ex -> {
                    if (ex instanceof ErrorResponseException ere
                            && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                        g.clearPanel();
                        return;
                    }
                    log.warn("Panel update failed for guild {}: {}", guild.getId(), ex.getMessage());
                });
    }
}
