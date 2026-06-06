package com.main.audio;

/**
 * Minimal slice of a {@link net.dv8tion.jda.api.entities.User} that we want to
 * propagate from {@code /play} / {@code !play} to the auto-published
 * "Reproduciendo ahora" embed when a track actually starts playing.
 *
 * Attached to {@link com.sedmelluq.discord.lavaplayer.track.AudioTrack#setUserData(Object)}
 * so that the {@link Scheduler}'s {@code onTrackStart} hook can read it without
 * having to plumb the requester through the entire pipeline.
 */
public record RequesterInfo(String name, String avatarUrl) {}
