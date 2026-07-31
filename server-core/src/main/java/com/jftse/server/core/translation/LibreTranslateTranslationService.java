package com.jftse.server.core.translation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;

@Log4j2
public final class LibreTranslateTranslationService {
    static final int MAX_INPUT_CODE_POINTS = 256;
    static final int MAX_INPUT_BYTES = 1024;
    static final int MAX_TRANSLATED_CODE_POINTS = 1024;
    static final int MAX_RESPONSE_BYTES = 16_384;
    public static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 2;

    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration timeout;
    private final boolean enabled;
    private final int maximumInputCodePoints;
    private final int maximumTranslatedCodePoints;
    private final Semaphore requestPermits;
    private final Map<String, String> translations = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > 512;
                }
            }
    );
    private final ConcurrentMap<String, CompletableFuture<String>> inFlightTranslations = new ConcurrentHashMap<>();

    public LibreTranslateTranslationService(
            HttpClient httpClient,
            URI endpoint,
            Duration timeout,
            boolean enabled
    ) {
        this(
                httpClient,
                endpoint,
                timeout,
                enabled,
                MAX_INPUT_CODE_POINTS,
                MAX_TRANSLATED_CODE_POINTS,
                DEFAULT_MAX_CONCURRENT_REQUESTS
        );
    }

    public LibreTranslateTranslationService(
            HttpClient httpClient,
            URI endpoint,
            Duration timeout,
            boolean enabled,
            int maximumInputCodePoints,
            int maximumTranslatedCodePoints,
            int maximumConcurrentRequests
    ) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.timeout = timeout;
        this.enabled = enabled;
        this.maximumInputCodePoints = requirePositive(
                maximumInputCodePoints,
                "maximumInputCodePoints"
        );
        this.maximumTranslatedCodePoints = requirePositive(
                maximumTranslatedCodePoints,
                "maximumTranslatedCodePoints"
        );
        this.requestPermits = new Semaphore(requirePositive(
                maximumConcurrentRequests,
                "maximumConcurrentRequests"
        ));
    }

    public CompletableFuture<String> translateToEnglish(String message) {
        if (!enabled || !containsThai(message) || exceedsInputLimit(message)) {
            return CompletableFuture.completedFuture(message);
        }

        String cachedTranslation = translations.get(message);
        if (cachedTranslation != null) {
            return CompletableFuture.completedFuture(cachedTranslation);
        }

        CompletableFuture<String> existing = inFlightTranslations.get(message);
        if (existing != null) {
            return existing;
        }
        if (!requestPermits.tryAcquire()) {
            return CompletableFuture.completedFuture(message);
        }

        CompletableFuture<String> result = new CompletableFuture<>();
        existing = inFlightTranslations.putIfAbsent(message, result);
        if (existing != null) {
            requestPermits.release();
            return existing;
        }

        try {
            requestTranslation(message).whenComplete((translation, failure) ->
                    completeTranslation(message, result, translation, failure)
            );
        } catch (RuntimeException failure) {
            completeTranslation(message, result, null, failure);
        }
        return result;
    }

    private CompletableFuture<String> requestTranslation(String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("q", message);
        payload.addProperty("source", "th");
        payload.addProperty("target", "en");
        payload.addProperty("format", "text");

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return httpClient.sendAsync(request, limitedStringBodyHandler())
                .thenApply(this::readTranslation);
    }

    private void completeTranslation(
            String message,
            CompletableFuture<String> result,
            String translation,
            Throwable failure
    ) {
        try {
            if (failure != null) {
                log.warn("Chat translation failed; sending original message: {}", failure.getMessage());
                result.complete(message);
                return;
            }
            translations.put(message, translation);
            result.complete(translation);
        } finally {
            inFlightTranslations.remove(message, result);
            requestPermits.release();
        }
    }

    private String readTranslation(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new CompletionException(
                    new IOException("LibreTranslate returned HTTP " + response.statusCode())
            );
        }

        try {
            JsonElement translatedText = JsonParser.parseString(response.body())
                    .getAsJsonObject()
                    .get("translatedText");
            if (translatedText == null || !translatedText.isJsonPrimitive()) {
                throw new IOException("LibreTranslate response is missing translatedText");
            }
            String translation = translatedText.getAsString();
            if (translation.isBlank()
                    || translation.codePointCount(0, translation.length()) > maximumTranslatedCodePoints) {
                throw new IOException("LibreTranslate response exceeds translated-text limit");
            }
            return translation;
        } catch (RuntimeException | IOException exception) {
            throw new CompletionException(exception);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private boolean containsThai(String message) {
        return message.codePoints().anyMatch(codePoint -> codePoint >= 0x0E00 && codePoint <= 0x0E7F);
    }

    private boolean exceedsInputLimit(String message) {
        return message.codePointCount(0, message.length()) > maximumInputCodePoints
                || message.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static HttpResponse.BodyHandler<String> limitedStringBodyHandler() {
        return responseInfo -> new LimitedStringSubscriber(MAX_RESPONSE_BYTES);
    }

    private static final class LimitedStringSubscriber implements HttpResponse.BodySubscriber<String> {
        private final int maximumBytes;
        private final ByteArrayOutputStream body;
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedStringSubscriber(int maximumBytes) {
            this.maximumBytes = maximumBytes;
            this.body = new ByteArrayOutputStream(Math.min(maximumBytes, 1024));
        }

        @Override
        public CompletableFuture<String> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (body.size() + buffer.remaining() > maximumBytes) {
                    subscription.cancel();
                    result.completeExceptionally(
                            new IOException("LibreTranslate response exceeds body limit")
                    );
                    return;
                }
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                body.writeBytes(bytes);
            }
        }

        @Override
        public void onError(Throwable failure) {
            result.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            result.complete(body.toString(StandardCharsets.UTF_8));
        }
    }
}
