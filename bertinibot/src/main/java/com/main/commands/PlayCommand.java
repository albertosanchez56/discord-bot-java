package com.main.commands;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.main.BertiniBot;
import com.main.audio.AudioPlayerSendHandler;
import com.main.audio.AudioTrackScheduler;
import com.main.audio.GuildMusicManager;
import com.main.audio.PlayerManager;
import com.main.audio.YtDlpManager;
import com.main.model.TrackInfo;
import com.main.service.YtDlpService;
import com.main.util.EmbedFactory;
import com.main.util.TrackFilter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.Guild;

public class PlayCommand extends ListenerAdapter {
    private final YtDlpService ytDlp = new YtDlpService();

    private static final ScheduledExecutorService IDLE_SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    private static final int IDLE_TIMEOUT_MINUTES = 5;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String[] parts = event.getMessage().getContentRaw().split(" ", 2);
        if (!parts[0].equalsIgnoreCase("!play") || parts.length < 2)
            return;

        MessageChannel text = event.getChannel();
        Member member = event.getMember();
        if (member == null || !member.getVoiceState().inAudioChannel()) {
            text.sendMessage("¡Únete primero a un canal de voz!").queue();
            return;
        }

        AudioChannelUnion chan = member.getVoiceState().getChannel();
        Guild guild = event.getGuild();
        guild.getAudioManager().openAudioConnection(chan);

        User requester = event.getAuthor();
        long guildId = guild.getIdLong();
        GuildMusicManager mm = PlayerManager.getInstance().getGuildMusicManager(guildId);
        mm.setTextChannel(event.getChannel());
        guild.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(mm.player));

        // Registrar callback de desconexión pasiva
        mm.scheduler.setDisconnectCallback(() -> {
            guild.getAudioManager().closeAudioConnection();
            text.sendMessage("⏹️ Me desconecto por inactividad. ¡Hasta la próxima!").queue();
            text.sendMessage("https://tenor.com/view/hasta-la-proxima-float-fly-gif-14857954").queue();
        });

        String argument = parts[1].trim();
        boolean isUrl = argument.matches("^https?://.*");
        String ytdlpInput = isUrl ? argument.split("&")[0] : "ytsearch1:" + argument;

        BertiniBot.YTDLP_POOL.submit(() -> {
            try {
                if (isUrl) {
                    text.sendMessage("Obteniendo información del enlace... ⏳").queue();
                } else {
                    text.sendMessage("🔍 Buscando '" + argument + "' en YouTube... ⏳").queue();
                }

                TrackInfo info = ytDlp.fetchTrackInfo(ytdlpInput);
                if (TrackFilter.isBlocked(info.title())) {
                    text.sendMessage("🚫 La canción **" + info.title() + "** está bloqueada.").queue();
                    text.sendMessage("https://tenor.com/view/aqui-no-hay-quien-viva-gif-14069994").queue();

                    // Programar desconexión tras X minutos de inactividad:
                    IDLE_SCHEDULER.schedule(() -> {
                        // Solo desconectamos si efectivamente sigue sin reproducir ni tener cola:
                        boolean noPlaying = mm.scheduler.getPlayer().getPlayingTrack() == null;
                        boolean emptyQueue = mm.scheduler.getQueueTitles().isEmpty();
                        if (noPlaying && emptyQueue) {
                            guild.getAudioManager().closeAudioConnection();
                            text.sendMessage("⏹️ Me desconecto por inactividad. ¡Hasta pronto!").queue();
                        }
                    }, IDLE_TIMEOUT_MINUTES, TimeUnit.MINUTES);

                    return;
                }

                PlayerManager.getInstance()
                    .getPlayerManager()
                    .loadItem(info.directUrl(), new AudioLoadResultHandler() {
                        @Override
                        public void trackLoaded(AudioTrack track) {
                            // Encola la pista
                            String thumbUrl = info.videoId() != null
                                ? "https://img.youtube.com/vi/" + info.videoId() + "/hqdefault.jpg"
                                : null;

                            // Encolar con requester
                            mm.scheduler.queue(track, info.title(), thumbUrl, requester);

                            // 1) Embed “Enqueued”
                            text.sendMessageEmbeds(
                                EmbedFactory.enqueuedEmbed(
                                    info.title(),
                                    track.getInfo().author,
                                    track.getDuration(),
                                    info.videoId(),
                                    requester.getName(),
                                    requester.getEffectiveAvatarUrl()
                                )
                            ).queue();

                            // 2) Embed cola con “Now Playing”
                            AudioTrack now = mm.scheduler.getPlayer().getPlayingTrack();
                            String nowTitle = now != null
                                ? mm.scheduler.getTitleMap().getOrDefault(now, now.getInfo().title)
                                : "_Nada_";

                            text.sendMessageEmbeds(
                                EmbedFactory.queueWithNowPlayingEmbed(mm.scheduler, nowTitle)
                            ).queue();
                        }

                        @Override
                        public void playlistLoaded(AudioPlaylist playlist) {
                            // Por simplicidad solo encolo la primera pista aquí
                            // Puedes encolar todas o solo la primera
                            for (AudioTrack t : playlist.getTracks()) {
                                mm.scheduler.queue(t, t.getInfo().title, null, requester);
                            }
                            text.sendMessage("✅ Playlist encolada: **" + playlist.getName() + "** ("
                                + playlist.getTracks().size() + " pistas)").queue();
                        }

                        @Override
                        public void noMatches() {
                            text.sendMessage("❌ No encontré: " + argument).queue();
                        }

                        @Override
                        public void loadFailed(FriendlyException e) {
                            text.sendMessage("⚠️ Error al cargar: " + e.getMessage()).queue();
                        }
                    });
            } catch (Exception e) {
                text.sendMessage("Error al procesar: " + e.getMessage()).queue();
            }
        });
    }
}
