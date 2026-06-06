package com.main.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.main.audio.AudioService;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Voice channel housekeeping.
 *
 * Pure event router: delegates ALL disconnect-timer logic to
 * {@link AudioService} so we don't have two schedulers fighting each other.
 *
 *  - Bot fully disconnects -> wipe queue + panel + cancel any pending timers.
 *  - Bot joins a channel   -> AudioService re-evaluates presence (no timer
 *                              races: the connect() path already does this).
 *  - A human moves in/out of the bot's channel -> ask AudioService to
 *                              re-evaluate the "alone" timer.
 */
public final class VoiceChannelListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(VoiceChannelListener.class);

    private final AudioService audio;

    public VoiceChannelListener(AudioService audio) {
        this.audio = audio;
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        Guild guild = event.getGuild();
        long selfId = guild.getSelfMember().getIdLong();
        boolean isSelf = event.getMember().getIdLong() == selfId;

        AudioChannelUnion left = event.getChannelLeft();
        AudioChannelUnion joined = event.getChannelJoined();

        log.debug("voice update: guild={} member={} isSelf={} left={} joined={}",
                guild.getId(),
                event.getMember().getEffectiveName(),
                isSelf,
                left == null ? "null" : left.getId(),
                joined == null ? "null" : joined.getId());

        if (isSelf) {
            if (joined == null) {
                log.debug("Bot left voice in guild {}, clearing state.", guild.getId());
                audio.get(guild).scheduler().clear();
                audio.get(guild).clearPanel();
                audio.cancelAllDisconnects(guild);
            } else {
                audio.signalHumanPresenceChange(guild);
            }
            return;
        }

        AudioChannel botChannel = guild.getAudioManager().getConnectedChannel();
        if (botChannel == null) return;

        boolean affectsBotChannel = (left != null && left.getIdLong() == botChannel.getIdLong())
                || (joined != null && joined.getIdLong() == botChannel.getIdLong());
        if (!affectsBotChannel) return;

        audio.signalHumanPresenceChange(guild);
    }
}
