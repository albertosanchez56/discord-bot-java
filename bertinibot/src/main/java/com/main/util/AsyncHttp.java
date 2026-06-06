package com.main.util;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * Shared {@link HttpClient} for all outgoing HTTP from the bot.
 *
 * Uses virtual threads as the executor so concurrent downloads (e.g. /build
 * fetching many icons) don't pin platform threads.
 */
public final class AsyncHttp {

    private static final HttpClient INSTANCE = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    private AsyncHttp() {}

    public static HttpClient client() {
        return INSTANCE;
    }
}
