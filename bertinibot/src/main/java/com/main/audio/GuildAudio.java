package com.main.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * Per-guild audio state: player, scheduler, send handler and last text channel
 * used (where to publish announcements / the control panel).
 */
public final class GuildAudio {

    private final long guildId;
    private final AudioPlayer player;
    private final Scheduler scheduler;
    private final OpusSendHandler sendHandler;

    private volatile MessageChannel textChannel;
    private volatile Long panelMessageId;
    private volatile Long panelChannelId;
    private volatile String lastAnnouncedIdentifier;

    public GuildAudio(long guildId, AudioPlayerManager manager) {
        this.guildId = guildId;
        this.player = manager.createPlayer();
        this.scheduler = new Scheduler(player);
        this.player.addListener(scheduler);
        this.sendHandler = new OpusSendHandler(player);
    }

    public long guildId() { return guildId; }
    public AudioPlayer player() { return player; }
    public Scheduler scheduler() { return scheduler; }
    public OpusSendHandler sendHandler() { return sendHandler; }

    public MessageChannel textChannel() { return textChannel; }
    public void setTextChannel(MessageChannel ch) { this.textChannel = ch; }

    public Long panelMessageId() { return panelMessageId; }
    public Long panelChannelId() { return panelChannelId; }

    public void setPanel(long channelId, long messageId) {
        this.panelChannelId = channelId;
        this.panelMessageId = messageId;
    }

    public void clearPanel() {
        this.panelChannelId = null;
        this.panelMessageId = null;
    }

    public String lastAnnouncedIdentifier() { return lastAnnouncedIdentifier; }
    public void setLastAnnouncedIdentifier(String id) { this.lastAnnouncedIdentifier = id; }
}
