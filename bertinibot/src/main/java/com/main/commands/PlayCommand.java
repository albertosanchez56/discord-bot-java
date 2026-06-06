package com.main.commands;

import com.main.audio.AudioService;
import com.main.audio.AudioService.LoadResult;
import com.main.audio.GuildAudio;
import com.main.core.SlashCommand;
import com.main.filters.TrackFilter;
import com.main.util.EmbedFactory;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class PlayCommand implements SlashCommand, SlashCommand.AutoCompletable {

    private final AudioService audio;
    private final TrackFilter filter;

    public PlayCommand(AudioService audio, TrackFilter filter) {
        this.audio = audio;
        this.filter = filter;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("play", "Reproduce audio desde YouTube (URL o busqueda).")
                .addOption(OptionType.STRING, "query", "URL de YouTube o texto a buscar", true, true);
    }

    @Override
    public void autoComplete(CommandAutoCompleteInteractionEvent event) {
        if (!"query".equals(event.getFocusedOption().getName())) return;
        String input = event.getFocusedOption().getValue().trim().toLowerCase();
        if (input.length() < 2) {
            event.replyChoiceStrings(List.of()).queue();
            return;
        }
        List<String> matches = audio.cache().keysMruFirst().stream()
                .filter(k -> k.contains(input))
                .limit(5)
                .toList();
        event.replyChoiceStrings(matches).queue();
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply("Este comando solo se puede usar en un servidor.").setEphemeral(true).queue();
            return;
        }

        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.reply("Tienes que estar en un canal de voz primero.").setEphemeral(true).queue();
            return;
        }
        AudioChannelUnion channel = voiceState.getChannel();
        if (channel == null) {
            event.reply("No puedo unirme a tu canal de voz.").setEphemeral(true).queue();
            return;
        }

        String query = event.getOption("query").getAsString();
        User requester = event.getUser();

        event.deferReply().queue();

        GuildAudio g = audio.get(guild);
        g.setTextChannel(event.getChannel());
        audio.connect(guild, channel);

        audio.enqueue(guild, query).whenComplete((result, err) -> {
            if (err != null) {
                event.getHook().sendMessage("Error procesando la peticion: " + err.getMessage()).queue();
                return;
            }
            switch (result) {
                case LoadResult.Single s -> handleSingle(event, requester, g, s);
                case LoadResult.Playlist p -> event.getHook()
                        .sendMessage("Encoladas **" + p.playlist().getTracks().size()
                                + "** pistas de _" + p.playlist().getName() + "_.").queue();
                case LoadResult.NoMatches n -> event.getHook()
                        .sendMessage("No encontre nada para: `" + query + "`").queue();
                case LoadResult.Failed f -> event.getHook()
                        .sendMessage("No se pudo cargar la pista: " + f.error().getMessage()).queue();
            }
        });
    }

    private void handleSingle(SlashCommandInteractionEvent event, User requester,
                              GuildAudio g, LoadResult.Single single) {
        var track = single.track();
        if (filter.isBlocked(track.getInfo().title)) {
            g.scheduler().clear();
            event.getHook().sendMessage("La cancion **" + track.getInfo().title
                    + "** esta bloqueada por filtros.").queue();
            return;
        }

        boolean nowPlaying = g.scheduler().nowPlaying() == track;
        var embed = nowPlaying
                ? EmbedFactory.nowPlaying(track, requester.getName(), requester.getEffectiveAvatarUrl())
                : EmbedFactory.enqueued(track, g.scheduler().snapshot().size(),
                                        requester.getName(), requester.getEffectiveAvatarUrl());
        event.getHook().sendMessageEmbeds(embed).queue();
    }
}
