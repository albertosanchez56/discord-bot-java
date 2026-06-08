package com.main.spotify;

/**
 * Anything that goes wrong while talking to the Spotify Web API: a network
 * hiccup, an invalid id, expired credentials, or an unexpected response
 * shape. Used so callers can react with a friendly Discord message instead
 * of leaking raw stack traces.
 */
public class SpotifyException extends Exception {
    public SpotifyException(String message) { super(message); }
    public SpotifyException(String message, Throwable cause) { super(message, cause); }
}
