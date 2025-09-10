package com.main.commands;

import java.awt.Color;
import java.util.List;

import com.main.audio.AudioTrackScheduler;
import com.main.audio.GuildMusicManager;
import com.main.audio.PlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ListCommand extends ListenerAdapter {
   @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.getMessage().getContentRaw().equalsIgnoreCase("!list")) {
            return;
        }

        // Asegurarnos de que el usuario esté en un canal de voz
        if (event.getMember() == null || !event.getMember().getVoiceState().inAudioChannel()) {
            event.getChannel()
                 .sendMessage("¡Únete primero a un canal de voz para ver la lista!")
                 .queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        GuildMusicManager musicManager =
            PlayerManager.getInstance().getGuildMusicManager(guildId);
        AudioTrackScheduler scheduler = musicManager.scheduler;

        // 1) Canción en reproducción
        AudioTrack now = scheduler.getPlayer().getPlayingTrack();
        String nowTitle = (now != null)
            ? scheduler.getTitleMap().getOrDefault(now, now.getInfo().title)
            : "_No hay nada reproduciéndose_";

        // 2) Las próximas en cola
        List<String> upcoming = scheduler.getQueueTitles();

        // 3) Construcción del embed
        EmbedBuilder eb = new EmbedBuilder()
            .setTitle("🎶 Cola de reproducción")
            .setColor(new Color(0x6A0DAD))
            .addField("Reproduciendo ahora", nowTitle, false);

        if (upcoming.isEmpty()) {
            eb.setDescription("_La cola está vacía._");
        } else {
            StringBuilder desc = new StringBuilder("**Próximas en cola:**\n");
            for (int i = 0; i < upcoming.size(); i++) {
                desc.append(i + 1)
                    .append(". ")
                    .append(upcoming.get(i))
                    .append("\n");
            }
            eb.setDescription(desc.toString());
        }

        MessageChannelUnion canal = event.getChannel();
        canal.sendMessageEmbeds(eb.build()).queue();
    }
}
