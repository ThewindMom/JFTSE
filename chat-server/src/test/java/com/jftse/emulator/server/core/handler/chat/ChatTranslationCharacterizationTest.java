package com.jftse.emulator.server.core.handler.chat;

import com.jftse.emulator.server.net.FTClient;
import com.jftse.server.core.translation.LibreTranslateTranslationService;
import com.jftse.server.core.shared.packets.chat.SMSGChatMessageLobby;
import com.jftse.server.core.shared.packets.chat.SMSGChatMessageRoom;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatTranslationCharacterizationTest {
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
    void preservesLobbyAndRoomPacketMetadata() {
        SMSGChatMessageLobby lobby = SMSGChatMessageLobby.builder()
                .unk((char) 7)
                .sender("Somchai")
                .message("มีใครอยากเล่นคู่ไหม")
                .textColor(3)
                .build();
        SMSGChatMessageRoom room = SMSGChatMessageRoom.builder()
                .type((byte) 1)
                .sender("Somchai")
                .message("มีใครอยากเล่นคู่ไหม")
                .textColor(3)
                .build();

        assertEquals((char) 7, lobby.getUnk());
        assertEquals("Somchai", lobby.getSender());
        assertEquals("มีใครอยากเล่นคู่ไหม", lobby.getMessage());
        assertEquals(3, lobby.getTextColor());
        assertEquals((byte) 1, room.getType());
        assertEquals("Somchai", room.getSender());
        assertEquals("มีใครอยากเล่นคู่ไหม", room.getMessage());
        assertEquals(3, room.getTextColor());
    }

    @Test
    void preservesRoomTeamVisibilityRules() throws Exception {
        ChatMessageRoomPacketHandler handler = new ChatMessageRoomPacketHandler();
        Method areInSameTeam = ChatMessageRoomPacketHandler.class
                .getDeclaredMethod("areInSameTeam", int.class, int.class);
        areInSameTeam.setAccessible(true);

        assertTrue((boolean) areInSameTeam.invoke(handler, 0, 2));
        assertTrue((boolean) areInSameTeam.invoke(handler, 1, 3));
        assertFalse((boolean) areInSameTeam.invoke(handler, 0, 1));
        assertFalse((boolean) areInSameTeam.invoke(handler, 2, 3));
    }

    @Test
    void routesOriginalToSenderAndThaiRecipientsAndTranslationToEnglishRecipients() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/translate", exchange -> {
            providerCalls.incrementAndGet();
            byte[] response = ("{\"translatedText\":\"" + ENGLISH_MESSAGE + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        LibreTranslateTranslationService service = new LibreTranslateTranslationService(
                HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/translate"),
                Duration.ofMillis(500),
                true
        );
        FTClient sender = new FTClient();
        FTClient thaiRecipient = new FTClient();
        FTClient englishRecipientOne = new FTClient();
        FTClient englishRecipientTwo = new FTClient();
        Method setTranslation = FTClient.class.getMethod("setTranslateChatToEnglish", boolean.class);
        setTranslation.invoke(sender, true);
        setTranslation.invoke(englishRecipientOne, true);
        setTranslation.invoke(englishRecipientTwo, true);

        CompletableFuture<String> senderMessage = resolveMessage(
                ChatMessageLobbyPacketHandler.class, sender, sender, service
        );
        CompletableFuture<String> thaiMessage = resolveMessage(
                ChatMessageLobbyPacketHandler.class, sender, thaiRecipient, service
        );
        CompletableFuture<String> englishLobbyMessageOne = resolveMessage(
                ChatMessageLobbyPacketHandler.class, sender, englishRecipientOne, service
        );
        CompletableFuture<String> englishLobbyMessageTwo = resolveMessage(
                ChatMessageLobbyPacketHandler.class, sender, englishRecipientTwo, service
        );
        CompletableFuture<String> englishRoomMessage = resolveMessage(
                ChatMessageRoomPacketHandler.class, sender, englishRecipientOne, service
        );

        assertEquals(THAI_MESSAGE, senderMessage.get(1, TimeUnit.SECONDS));
        assertEquals(THAI_MESSAGE, thaiMessage.get(1, TimeUnit.SECONDS));
        assertEquals(ENGLISH_MESSAGE, englishLobbyMessageOne.get(1, TimeUnit.SECONDS));
        assertEquals(ENGLISH_MESSAGE, englishLobbyMessageTwo.get(1, TimeUnit.SECONDS));
        assertEquals(ENGLISH_MESSAGE, englishRoomMessage.get(1, TimeUnit.SECONDS));
        assertEquals(1, providerCalls.get());
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<String> resolveMessage(
            Class<?> handlerType,
            FTClient sender,
            FTClient recipient,
            LibreTranslateTranslationService service
    ) throws Exception {
        Method resolver = handlerType.getDeclaredMethod(
                "messageForRecipient",
                FTClient.class,
                FTClient.class,
                String.class,
                LibreTranslateTranslationService.class
        );
        resolver.setAccessible(true);
        return (CompletableFuture<String>) resolver.invoke(null, sender, recipient, THAI_MESSAGE, service);
    }
}
