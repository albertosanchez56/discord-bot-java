package com.main.listeners;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.main.audio.PlayerManager;

import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class VoiceChannelListener extends ListenerAdapter {
     @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        AudioChannelUnion connected = event.getGuild().getAudioManager().getConnectedChannel();
        if (connected == null) {
            return; // El bot no estaba conectado
        }

        var left = event.getChannelLeft();
        // Si el miembro que salió es el propio bot y estaba en ese canal
        if (left != null && left.equals(connected)
            && event.getMember().getUser().isBot()
            && event.getMember().getIdLong() == event.getGuild().getSelfMember().getIdLong()) {

            long guildId = event.getGuild().getIdLong();
            // Limpia cola y detiene reproducción
            PlayerManager
                .getInstance()
                .getGuildMusicManager(guildId)
                .scheduler
                .clear();
        }
    }
}