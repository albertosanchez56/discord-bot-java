package com.main.commands;

import java.util.List;
import java.util.function.Consumer;

import com.main.audio.AudioService;
import com.main.audio.AudioService.LoadResult;
import com.main.audio.GuildAudio;
import com.main.audio.RequesterInfo;
import com.main.core.PrefixCommand;
import com.main.core.SlashCommand;
import com.main.spotify.SpotifyRef;
import com.main.spotify.SpotifyResolver;
import com.main.spotify.SpotifyUrlParser;
import com.main.util.EmbedFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class PlayCommand implements SlashCommand, SlashCommand.AutoCompletable, PrefixCommand {

    private static final String BLOCKED_GIF = "https://tenor.com/view/aqui-no-hay-quien-viva-gif-14069994";

    private final AudioService audio;
    /** Optional: only set when Spotify credentials are configured. */
    private final SpotifyResolver spotify;

    public PlayCommand(AudioService audio) {
        this(audio, null);
    }

    public PlayCommand(AudioService audio, SpotifyResolver spotify) {
        this.audio = audio;
        this.spotify = spotify;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("play", "Reproduce audio desde YouTube o Spotify (URL o busqueda).")
                .addOption(OptionType.STRING, "query", "URL de YouTube/Spotify o texto a buscar", true, true);
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
        event.deferReply().queue();
        play(guild, channel, event.getChannel(), event.getUser(), query,
                msg -> event.getHook().sendMessage(msg).queue(),
                embed -> event.getHook().sendMessageEmbeds(embed).queue(),
                ack -> event.getHook().sendMessage(ack).setEphemeral(true).queue());
    }

    @Override
    public String name() { return "play"; }
    @Override
    public List<String> aliases() { return List.of("p"); }
    @Override
    public String usage() { return "!play <url|texto>"; }
    @Override
    public String description() { return "Reproduce audio desde YouTube (URL o busqueda)."; }

    @Override
    public void execute(MessageReceivedEvent event, String args) {
        MessageChannel ch = event.getChannel();
        if (args.isBlank()) {
            ch.sendMessage("Uso: `!play <url o texto>`. Ej.: `!play eminem lose yourself`.").queue();
            return;
        }
        Member member = event.getMember();
        if (member == null) {
            ch.sendMessage("Solo se puede usar en un servidor.").queue();
            return;
        }
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            ch.sendMessage("Tienes que estar en un canal de voz primero.").queue();
            return;
        }
        AudioChannelUnion channel = voiceState.getChannel();
        if (channel == null) {
            ch.sendMessage("No puedo unirme a tu canal de voz.").queue();
            return;
        }

        play(event.getGuild(), channel, ch, event.getAuthor(), args.trim(),
                msg -> ch.sendMessage(msg).queue(),
                embed -> ch.sendMessageEmbeds(embed).queue(),
                ack -> event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("\uD83D\uDC4C")).queue(null, e -> {}));
    }

    private void play(Guild guild, AudioChannelUnion channel, MessageChannel textChannel,
                      User requester, String query,
                      Consumer<String> reply, Consumer<MessageEmbed> replyEmbed,
                      Consumer<String> silentAck) {
        GuildAudio g = audio.get(guild);
        g.setTextChannel(textChannel);
        audio.connect(guild, channel);

        RequesterInfo info = new RequesterInfo(requester.getName(), requester.getEffectiveAvatarUrl());

        // Spotify URL? Route to the resolver, which itself talks back to
        // AudioService.enqueue() with a YouTube search per track.
        if (SpotifyUrlParser.looksLikeSpotify(query)) {
            handleSpotify(guild, requester, info, query, reply, replyEmbed, silentAck);
            return;
        }

        audio.enqueue(guild, query, info).whenComplete((result, err) -> {
            if (err != null) {
                reply.accept("Error procesando la peticion: " + err.getMessage());
                return;
            }
            switch (result) {
                case LoadResult.Single s -> handleSingle(g, requester, s, reply, replyEmbed, silentAck);
                case LoadResult.Playlist p -> {
                    int kept = p.playlist().getTracks().size() - p.skippedBlocked();
                    String msg = "Encoladas **" + kept + "** pistas de _" + p.playlist().getName() + "_.";
                    if (p.skippedBlocked() > 0) {
                        msg += " (Se omitieron " + p.skippedBlocked() + " bloqueada"
                                + (p.skippedBlocked() == 1 ? "" : "s") + ".)";
                    }
                    reply.accept(msg);
                }
                case LoadResult.Blocked b -> handleBlocked(b.track(), reply);
                case LoadResult.NoMatches n -> reply.accept("No encontre nada para: `" + query + "`");
                case LoadResult.Failed f -> reply.accept("No se pudo cargar la pista: "
                        + f.error().getMessage());
            }
        });
    }

    private void handleSpotify(Guild guild, User requester, RequesterInfo info, String query,
                                Consumer<String> reply, Consumer<MessageEmbed> replyEmbed,
                                Consumer<String> silentAck) {
        if (spotify == null) {
            // Defensive: Bootstrap now always provides a resolver (API or
            // scraper fallback), so this branch should be unreachable.
            reply.accept("Spotify no esta disponible en este momento.");
            return;
        }
        SpotifyRef ref = SpotifyUrlParser.parse(query).orElse(null);
        if (ref == null) {
            reply.accept("No reconozco esta URL de Spotify: `" + query + "`");
            return;
        }
        GuildAudio g = audio.get(guild);
        spotify.resolveAndEnqueue(guild, ref, info).whenComplete((outcome, err) -> {
            if (err != null) {
                reply.accept("Error consultando Spotify: " + err.getMessage());
                return;
            }
            switch (outcome) {
                case SpotifyResolver.Outcome.Single s -> handleSpotifySingle(g, requester, s, reply, replyEmbed, silentAck);
                case SpotifyResolver.Outcome.Bundle b -> handleSpotifyBundle(b, requester, replyEmbed);
                case SpotifyResolver.Outcome.EmptyBundle e -> reply.accept(
                        "El " + e.bundle().kindLabel() + " _" + e.bundle().name()
                                + "_ no tiene pistas reproducibles.");
                case SpotifyResolver.Outcome.Error e -> reply.accept(
                        "Spotify devolvio un error: " + e.error().getMessage());
            }
        });
    }

    private void handleSpotifySingle(GuildAudio g, User requester,
                                      SpotifyResolver.Outcome.Single single,
                                      Consumer<String> reply, Consumer<MessageEmbed> replyEmbed,
                                      Consumer<String> silentAck) {
        switch (single.loadResult()) {
            case LoadResult.Single s -> handleSingle(g, requester, s, reply, replyEmbed, silentAck);
            case LoadResult.Playlist p -> {
                if (!p.playlist().getTracks().isEmpty()) {
                    replyEmbed.accept(EmbedFactory.enqueued(p.playlist().getTracks().get(0),
                            g.scheduler().snapshot().size(),
                            requester.getName(), requester.getEffectiveAvatarUrl()));
                } else {
                    reply.accept("Spotify: no encontre nada para _"
                            + single.track().artistsDisplay() + " - " + single.track().title() + "_.");
                }
            }
            case LoadResult.Blocked b -> handleBlocked(b.track(), reply);
            case LoadResult.NoMatches n -> reply.accept("Spotify: no encontre nada en YouTube para _"
                    + single.track().artistsDisplay() + " - " + single.track().title() + "_.");
            case LoadResult.Failed f -> reply.accept("No se pudo reproducir esta pista de Spotify: "
                    + f.error().getMessage());
        }
    }

    private void handleSpotifyBundle(SpotifyResolver.Outcome.Bundle b, User requester,
                                      Consumer<MessageEmbed> replyEmbed) {
        // Immediate embed: we already started queuing.
        replyEmbed.accept(EmbedFactory.spotifyBundleStarted(b.bundle(), b.totalToTry(),
                requester.getName(), requester.getEffectiveAvatarUrl()));
        // When the background batch finishes, post the totals embed.
        b.completion().whenComplete((stats, err) -> {
            if (err != null || stats == null) return;
            replyEmbed.accept(EmbedFactory.spotifyBundleDone(
                    b.bundle(), stats.queued(), stats.failed(), stats.total()));
        });
    }

    private void handleSingle(GuildAudio g, User requester, LoadResult.Single single,
                              Consumer<String> reply, Consumer<MessageEmbed> replyEmbed,
                              Consumer<String> silentAck) {
        var track = single.track();
        boolean nowPlaying = g.scheduler().nowPlaying() == track;
        if (nowPlaying) {
            // The Scheduler's onTrackStart hook already published a rich
            // "Reproduciendo ahora" embed; avoid duplicating it. Give the
            // user a discreet ack instead.
            silentAck.accept("\u25B6 Sonando: **" + track.getInfo().title + "**");
            return;
        }
        replyEmbed.accept(EmbedFactory.enqueued(track, g.scheduler().snapshot().size(),
                requester.getName(), requester.getEffectiveAvatarUrl()));
    }

    private void handleBlocked(com.sedmelluq.discord.lavaplayer.track.AudioTrack track,
                               Consumer<String> reply) {
        reply.accept("\uD83D\uDEAB La cancion **" + track.getInfo().title + "** esta bloqueada.\n"
                + BLOCKED_GIF);
    }
}
