package com.main.audio;

import java.nio.ByteBuffer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;

import net.dv8tion.jda.api.audio.AudioSendHandler;

public class AudioPlayerSendHandler implements AudioSendHandler{
     private final AudioPlayer audioPlayer;
    private ByteBuffer buffer = ByteBuffer.allocate(1024);
    private final MutableAudioFrame frame = new MutableAudioFrame();

    public AudioPlayerSendHandler(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
        this.frame.setBuffer(buffer);
    }

    @Override
    public boolean canProvide() {
        // llena el buffer con datos de audio
        return audioPlayer.provide(frame);
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        buffer.flip();
        ByteBuffer slice = buffer.slice();
        buffer.clear();
        return slice;
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
