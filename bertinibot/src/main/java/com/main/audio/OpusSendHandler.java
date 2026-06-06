package com.main.audio;

import java.nio.ByteBuffer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;

import net.dv8tion.jda.api.audio.AudioSendHandler;

/**
 * Bridges a Lavaplayer {@link AudioPlayer} to JDA's voice send pipeline.
 *
 * Reports {@code isOpus() == true} so JDA forwards Opus packets straight to
 * Discord without re-encoding (zero CPU transcoding when the source is Opus).
 */
public final class OpusSendHandler implements AudioSendHandler {

    private final AudioPlayer player;
    private final ByteBuffer buffer;
    private final MutableAudioFrame frame;

    public OpusSendHandler(AudioPlayer player) {
        this.player = player;
        this.buffer = ByteBuffer.allocate(1024);
        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(buffer);
    }

    @Override
    public boolean canProvide() {
        buffer.clear();
        return player.provide(frame);
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        buffer.flip();
        return buffer;
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
