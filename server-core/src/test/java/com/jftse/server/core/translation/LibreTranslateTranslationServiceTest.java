package com.jftse.server.core.translation;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LibreTranslateTranslationServiceTest {
    private static final String THAI_MESSAGE = "มีใครอยากเล่นคู่ไหม";
    private static final String ENGLISH_MESSAGE = "Does anyone want to play doubles?";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void translatesThaiMessageThroughLibreTranslateContract() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/translate", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"translatedText\":\"" + ENGLISH_MESSAGE + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        Object service = createService(server.getAddress().getPort(), Duration.ofMillis(500), true);
        String translated = translate(service, THAI_MESSAGE).get(1, TimeUnit.SECONDS);

        assertEquals(ENGLISH_MESSAGE, translated);
        assertTrue(requestBody.get().contains("\"q\":\"" + THAI_MESSAGE + "\""));
        assertTrue(requestBody.get().contains("\"source\":\"th\""));
        assertTrue(requestBody.get().contains("\"target\":\"en\""));
    }

    @Test
    void skipsProviderWhenDisabledOrMessageContainsNoThai() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/translate", exchange -> {
            providerCalls.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();

        Object disabled = createService(server.getAddress().getPort(), Duration.ofMillis(500), false);
        Object enabled = createService(server.getAddress().getPort(), Duration.ofMillis(500), true);

        assertEquals(THAI_MESSAGE, translate(disabled, THAI_MESSAGE).get(1, TimeUnit.SECONDS));
        assertEquals("ready for doubles", translate(enabled, "ready for doubles").get(1, TimeUnit.SECONDS));
        assertEquals(0, providerCalls.get());
    }

    @Test
    void returnsOriginalMessageForMalformedProviderResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/translate", exchange -> {
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        Object service = createService(server.getAddress().getPort(), Duration.ofMillis(500), true);

        assertEquals(THAI_MESSAGE, translate(service, THAI_MESSAGE).get(1, TimeUnit.SECONDS));
    }

    @Test
    void returnsOriginalMessageWhenProviderExceedsTimeout() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/translate", exchange -> {
            exchange.getRequestBody().readAllBytes();
        });
        server.start();

        Object service = createService(server.getAddress().getPort(), Duration.ofMillis(50), true);

        assertEquals(THAI_MESSAGE, translate(service, THAI_MESSAGE).get(1, TimeUnit.SECONDS));
    }

    @Test
    void rejectsOversizedThaiBeforeCallingProvider() throws Exception {
        ControlledProvider provider = new ControlledProvider();
        LibreTranslateTranslationService service = new LibreTranslateTranslationService(
                provider.client,
                URI.create("http://provider.invalid/translate"),
                Duration.ofMillis(500),
                true
        );
        String oversized = "ก".repeat(257);

        CompletableFuture<String> result = service.translateToEnglish(oversized);
        try {
            assertEquals(0, provider.requestCount.get());
            assertEquals(oversized, result.get(1, TimeUnit.SECONDS));
        } finally {
            provider.completeAll("{\"translatedText\":\"unused\"}");
        }
    }

    @Test
    void capsConcurrentUniqueProviderRequests() {
        ControlledProvider provider = new ControlledProvider();
        LibreTranslateTranslationService service = new LibreTranslateTranslationService(
                provider.client,
                URI.create("http://provider.invalid/translate"),
                Duration.ofSeconds(5),
                true,
                LibreTranslateTranslationService.MAX_INPUT_CODE_POINTS,
                LibreTranslateTranslationService.MAX_TRANSLATED_CODE_POINTS,
                8
        );
        List<CompletableFuture<String>> results = new ArrayList<>();

        for (int index = 0; index < 12; index++) {
            results.add(service.translateToEnglish(THAI_MESSAGE + index));
        }

        try {
            assertEquals(8, provider.requestCount.get());
        } finally {
            provider.completeAll("{\"translatedText\":\"translated\"}");
        }
    }

    @Test
    void rejectsOversizedProviderResponse() throws Exception {
        ControlledProvider provider = new ControlledProvider();
        LibreTranslateTranslationService service = new LibreTranslateTranslationService(
                provider.client,
                URI.create("http://provider.invalid/translate"),
                Duration.ofMillis(500),
                true
        );

        CompletableFuture<String> result = service.translateToEnglish(THAI_MESSAGE);
        provider.completeNext("{\"translatedText\":\"" + "x".repeat(1025) + "\"}");

        assertEquals(THAI_MESSAGE, result.get(1, TimeUnit.SECONDS));
    }

    @Test
    void releasesConcurrencyPermitsAfterRequestsComplete() throws Exception {
        ControlledProvider provider = new ControlledProvider();
        LibreTranslateTranslationService service = new LibreTranslateTranslationService(
                provider.client,
                URI.create("http://provider.invalid/translate"),
                Duration.ofSeconds(5),
                true,
                LibreTranslateTranslationService.MAX_INPUT_CODE_POINTS,
                LibreTranslateTranslationService.MAX_TRANSLATED_CODE_POINTS,
                8
        );
        List<CompletableFuture<String>> firstBatch = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            firstBatch.add(service.translateToEnglish(THAI_MESSAGE + index));
        }
        provider.completeAll("{\"translatedText\":\"translated\"}");
        CompletableFuture.allOf(firstBatch.toArray(CompletableFuture[]::new))
                .get(1, TimeUnit.SECONDS);

        CompletableFuture<String> next = service.translateToEnglish(THAI_MESSAGE + "next");
        assertEquals(9, provider.requestCount.get());
        provider.completeNext("{\"translatedText\":\"next\"}");
        assertEquals("next", next.get(1, TimeUnit.SECONDS));
    }

    @Test
    void honorsConfiguredInputOutputAndConcurrencyBounds() throws Exception {
        ControlledProvider provider = new ControlledProvider();
        LibreTranslateTranslationService service = new LibreTranslateTranslationService(
                provider.client,
                URI.create("http://provider.invalid/translate"),
                Duration.ofMillis(500),
                true,
                1,
                3,
                1
        );

        assertEquals("กก", service.translateToEnglish("กก").get(1, TimeUnit.SECONDS));
        assertEquals(0, provider.requestCount.get());

        CompletableFuture<String> first = service.translateToEnglish("ก");
        assertEquals("ข", service.translateToEnglish("ข").get(1, TimeUnit.SECONDS));
        assertEquals(1, provider.requestCount.get());

        provider.completeNext("{\"translatedText\":\"four\"}");
        assertEquals("ก", first.get(1, TimeUnit.SECONDS));
    }

    @Test
    void defaultsToTwoConcurrentProviderRequests() throws Exception {
        ControlledProvider provider = new ControlledProvider();
        LibreTranslateTranslationService service = new LibreTranslateTranslationService(
                provider.client,
                URI.create("http://provider.invalid/translate"),
                Duration.ofMillis(500),
                true
        );
        String firstMessage = THAI_MESSAGE + " 1";
        String secondMessage = THAI_MESSAGE + " 2";
        String thirdMessage = THAI_MESSAGE + " 3";

        CompletableFuture<String> first = service.translateToEnglish(firstMessage);
        CompletableFuture<String> second = service.translateToEnglish(secondMessage);
        CompletableFuture<String> third = service.translateToEnglish(thirdMessage);

        try {
            assertEquals(2, provider.requestCount.get());
            assertEquals(thirdMessage, third.get(1, TimeUnit.SECONDS));
        } finally {
            provider.completeAll("{\"translatedText\":\"hello\"}");
        }
        assertEquals("hello", first.get(1, TimeUnit.SECONDS));
        assertEquals("hello", second.get(1, TimeUnit.SECONDS));
    }

    @Test
    void evictsLeastRecentlyUsedTranslationAfter512Entries() throws Exception {
        ControlledProvider provider = new ControlledProvider();
        LibreTranslateTranslationService service = new LibreTranslateTranslationService(
                provider.client,
                URI.create("http://provider.invalid/translate"),
                Duration.ofSeconds(5),
                true
        );

        for (int index = 0; index < 513; index++) {
            CompletableFuture<String> result = service.translateToEnglish(THAI_MESSAGE + index);
            provider.completeNext("{\"translatedText\":\"translation-" + index + "\"}");
            assertEquals("translation-" + index, result.get(1, TimeUnit.SECONDS));
        }

        CompletableFuture<String> evicted = service.translateToEnglish(THAI_MESSAGE + 0);
        assertEquals(514, provider.requestCount.get());
        provider.completeNext("{\"translatedText\":\"reloaded\"}");
        assertEquals("reloaded", evicted.get(1, TimeUnit.SECONDS));
    }

    private Object createService(int port, Duration timeout, boolean enabled) throws Exception {
        Class<?> serviceType = Class.forName(
                "com.jftse.server.core.translation.LibreTranslateTranslationService"
        );
        Constructor<?> constructor = serviceType.getConstructor(
                HttpClient.class,
                URI.class,
                Duration.class,
                boolean.class
        );
        return constructor.newInstance(
                HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + port + "/translate"),
                timeout,
                enabled
        );
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<String> translate(Object service, String message) throws Exception {
        Method translate = service.getClass().getMethod("translateToEnglish", String.class);
        return (CompletableFuture<String>) translate.invoke(service, message);
    }

    private static final class ControlledProvider {
        private final HttpClient client = mock(HttpClient.class);
        private final AtomicInteger requestCount = new AtomicInteger();
        private final List<CompletableFuture<HttpResponse<String>>> pending = new ArrayList<>();

        @SuppressWarnings({"rawtypes", "unchecked"})
        private ControlledProvider() {
            when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> {
                        requestCount.incrementAndGet();
                        CompletableFuture<HttpResponse<String>> response = new CompletableFuture<>();
                        pending.add(response);
                        return response;
                    });
        }

        private void completeNext(String body) {
            CompletableFuture<HttpResponse<String>> future = pending.remove(0);
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(body);
            future.complete(response);
        }

        private void completeAll(String body) {
            while (!pending.isEmpty()) {
                completeNext(body);
            }
        }
    }
}
