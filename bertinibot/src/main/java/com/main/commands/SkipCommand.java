package com.main.commands;

import com.main.audio.GuildMusicManager;
import com.main.audio.PlayerManager;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class SkipCommand extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String msg = event.getMessage().getContentRaw();
        if (!msg.equalsIgnoreCase("!skip")) return;

        Member member = event.getMember();
        if (member == null || !member.getVoiceState().inAudioChannel()) {
            event.getChannel().sendMessage("¡Únete primero a un canal de voz!").queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        GuildMusicManager musicManager =
            PlayerManager.getInstance().getGuildMusicManager(guildId);

        // Ejecutamos el skip
        musicManager.scheduler.skipTrack();

        // Averiguamos qué está sonando ahora
        var now = musicManager.scheduler.getPlayer().getPlayingTrack();
        String title = now != null
            ? musicManager.scheduler.getTitleMap().getOrDefault(now, now.getInfo().title)
            : "Nada";

        event.getChannel()
             .sendMessage("⏭ Se ha saltado. Ahora reproduciendo: **" + title + "**")
             .queue();
    }
}