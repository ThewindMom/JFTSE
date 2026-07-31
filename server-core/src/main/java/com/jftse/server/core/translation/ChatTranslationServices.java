package com.jftse.server.core.translation;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

public final class ChatTranslationServices {
    private static volatile LibreTranslateTranslationService service =
            new LibreTranslateTranslationService(
                    HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:5000/translate"),
                    Duration.ofMillis(450),
                    false
            );

    private ChatTranslationServices() {
    }

    public static LibreTranslateTranslationService get() {
        return service;
    }

    public static void configure(LibreTranslateTranslationService translationService) {
        service = Objects.requireNonNull(translationService);
    }
}
