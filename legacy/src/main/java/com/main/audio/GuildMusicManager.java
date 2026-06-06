package com.main.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class GuildMusicManager {
    public final AudioPlayer player;
    public final AudioTrackScheduler scheduler;

    // Canal donde enviar embeds (Now Playing, etc.)
    private MessageChannel textChannel;

    public GuildMusicManager(PlayerManager manager) {
        this.player = manager.getPlayerManager().createPlayer();
        // Ahora pasamos 'this' al scheduler para que pueda usar getTextChannel()
        this.scheduler = new AudioTrackScheduler(player, this);
        player.addListener(scheduler);
    }

    /**
     * Establece el canal de texto donde el bot publicará los embeds.
     */
    public void setTextChannel(MessageChannel channel) {
        this.textChannel = channel;
    }

    /**
     * Recupera el canal de texto registrado.
     */
    public MessageChannel getTextChannel() {
        return this.textChannel;
    }
}
