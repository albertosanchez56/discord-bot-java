package com.main.commands;

import com.main.audio.GuildMusicManager;
import com.main.audio.PlayerManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class CleanListCommand extends ListenerAdapter {
     @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String msg = event.getMessage().getContentRaw();
        if (!msg.equalsIgnoreCase("!clearList")) return;

        Member member = event.getMember();
        if (member == null || !member.getVoiceState().inAudioChannel()) {
            event.getChannel()
                 .sendMessage("¡Únete primero a un canal de voz!")
                 .queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        GuildMusicManager musicManager =
            PlayerManager.getInstance().getGuildMusicManager(guildId);

        // Si la cola está vacía, avisamos
        if (musicManager.scheduler.getQueueTitles().isEmpty()) {
            event.getChannel()
                 .sendMessage("La cola ya está vacía.")
                 .queue();
        } else {
            // Limpiamos la cola y detenemos la pista actual
            musicManager.scheduler.clear();
            event.getChannel()
                 .sendMessage("🗑️ Cola de reproducción limpiada.")
                 .queue();
        }
    }
}