package com.jftse.emulator.server.core.handler.chat;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.command.CommandManager;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.shared.packets.chat.CMSGChatMessageLobby;
import com.jftse.server.core.shared.packets.chat.CMSGChatMessageRoom;
import com.jftse.server.core.shared.packets.chat.SMSGChatMessageLobby;
import com.jftse.server.core.shared.packets.chat.SMSGChatMessageRoom;
import com.jftse.server.core.translation.ChatTranslationServices;
import com.jftse.server.core.translation.LibreTranslateTranslationService;
import com.jftse.server.core.translation.OrderedChatDelivery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatTranslationHandlerIntegrationTest {
    private static final String THAI_FIRST = "ก ข้อความแรก";
    private static final String THAI_SECOND = "ก ข้อความที่สอง";

    private Object previousGameManager;
    private Object previousCommandManager;
    private LibreTranslateTranslationService previousTranslationService;

    private GameManager gameManager;
    private CommandManager commandManager;
    private ControlledProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        gameManager = mock(GameManager.class);
        commandManager = mock(CommandManager.class);
        when(commandManager.isCommand(anyString())).thenReturn(false);

        previousGameManager = replaceStatic(GameManager.class, "instance", gameManager);
        previousCommandManager = replaceStatic(CommandManager.class, "instance", commandManager);

        previousTranslationService = ChatTranslationServices.get();
        provider = new ControlledProvider();
        ChatTranslationServices.configure(provider.service());
    }

    @AfterEach
    void tearDown() throws Exception {
        provider.completeAll("{\"translatedText\":\"cleanup\"}");
        ChatTranslationServices.configure(previousTranslationService);
        replaceStatic(GameManager.class, "instance", previousGameManager);
        replaceStatic(CommandManager.class, "instance", previousCommandManager);
    }

    @Test
    void preservesPerRecipientOrderWhenTranslationsCompleteInReverse() {
        Room room = runningRoom(7);
        ClientFixture sender = client("Sender", 1L, (short) 0, room, false, false);
        ClientFixture recipient = client("English", 2L, (short) 2, room, false, true);
        when(gameManager.getClientsInRoom((short) 7)).thenReturn(List.of(recipient.client));

        ChatMessageRoomPacketHandler handler = new ChatMessageRoomPacketHandler();
        handler.handle(sender.connection, roomRequest((byte) 0, THAI_FIRST));
        handler.handle(sender.connection, roomRequest((byte) 0, THAI_SECOND));

        assertEquals(2, provider.pendingCount());
        provider.complete(1, "{\"translatedText\":\"second\"}");
        provider.complete(0, "{\"translatedText\":\"first\"}");

        ArgumentCaptor<IPacket> packets = ArgumentCaptor.forClass(IPacket.class);
        verify(recipient.connection, times(2)).sendTCP(packets.capture());
        assertEquals(List.of("first", "second"), packets.getAllValues().stream()
                .map(ChatTranslationHandlerIntegrationTest::roomMessage)
                .toList());
    }

    @Test
    void dropsDelayedLobbyMessageAfterRecipientLeavesLobby() {
        AtomicBoolean recipientInLobby = new AtomicBoolean(true);
        ClientFixture sender = client("Sender", 1L, (short) 0, null, true, false);
        ClientFixture recipient = client("English", 2L, (short) 2, null, true, true);
        when(recipient.client.isInLobby()).thenAnswer(invocation -> recipientInLobby.get());
        when(gameManager.getClients()).thenReturn(
                new ConcurrentLinkedDeque<>(List.of(recipient.client)));

        new ChatMessageLobbyPacketHandler().handle(
                sender.connection,
                CMSGChatMessageLobby.builder().unk((char) 1).message(THAI_FIRST).build()
        );
        recipientInLobby.set(false);
        provider.complete(0, "{\"translatedText\":\"first\"}");

        verify(recipient.connection, never()).sendTCP(any(IPacket.class));
    }

    @Test
    void dropsDelayedLobbyMessageAfterRecipientLeavesAndRejoins() {
        ClientFixture sender = client("Sender", 1L, (short) 0, null, true, false);
        ClientFixture recipient = client("English", 2L, (short) 2, null, true, true);
        when(recipient.client.getLobbyMembershipGeneration()).thenReturn(1L, 3L);
        when(gameManager.getClients()).thenReturn(
                new ConcurrentLinkedDeque<>(List.of(recipient.client)));

        new ChatMessageLobbyPacketHandler().handle(
                sender.connection,
                CMSGChatMessageLobby.builder().unk((char) 1).message(THAI_FIRST).build()
        );
        provider.complete(0, "{\"translatedText\":\"first\"}");

        verify(recipient.connection, never()).sendTCP(any(IPacket.class));
    }

    @Test
    void dropsDelayedRoomMessageAfterRecipientLeavesAndRejoins() {
        Room room = runningRoom(8);
        ClientFixture sender = client("Sender", 1L, (short) 0, room, false, false);
        ClientFixture recipient = client("English", 2L, (short) 2, room, false, true);
        when(recipient.client.getRoomMembershipGeneration()).thenReturn(1L, 3L);
        when(gameManager.getClientsInRoom((short) 8)).thenReturn(List.of(recipient.client));

        new ChatMessageRoomPacketHandler().handle(
                sender.connection,
                roomRequest((byte) 0, THAI_FIRST)
        );
        provider.complete(0, "{\"translatedText\":\"first\"}");

        verify(recipient.connection, never()).sendTCP(any(IPacket.class));
    }

    @Test
    void runningTeamChatUsesActualHandlerAndPreservesVisibility() {
        Room room = runningRoom(9);
        ClientFixture sender = client("Sender", 1L, (short) 0, room, false, false);
        ClientFixture teammate = client("Teammate", 2L, (short) 2, room, false, true);
        ClientFixture opponent = client("Opponent", 3L, (short) 1, room, false, true);
        when(gameManager.getClientsInRoom((short) 9))
                .thenReturn(List.of(sender.client, teammate.client, opponent.client));

        new ChatMessageRoomPacketHandler().handle(
                sender.connection,
                roomRequest((byte) 1, THAI_FIRST)
        );
        provider.complete(0, "{\"translatedText\":\"team message\"}");

        assertEquals(RoomStatus.Running, room.getStatus());
        verify(sender.connection).sendTCP(any(IPacket.class));
        ArgumentCaptor<IPacket> packet = ArgumentCaptor.forClass(IPacket.class);
        verify(teammate.connection).sendTCP(packet.capture());
        assertEquals("team message", roomMessage(packet.getValue()));
        verify(opponent.connection, never()).sendTCP(any(IPacket.class));
    }

    @Test
    void lobbyHandlerSendsOriginalAndTranslatedPackets() {
        ClientFixture sender = client("Sender", 1L, (short) 0, null, true, false);
        ClientFixture thaiRecipient = client("Thai", 2L, (short) 2, null, true, false);
        ClientFixture englishRecipient = client("English", 3L, (short) 1, null, true, true);
        when(gameManager.getClients()).thenReturn(new ConcurrentLinkedDeque<>(
                List.of(sender.client, thaiRecipient.client, englishRecipient.client)));

        new ChatMessageLobbyPacketHandler().handle(
                sender.connection,
                CMSGChatMessageLobby.builder().unk((char) 1).message(THAI_FIRST).build()
        );
        provider.complete(0, "{\"translatedText\":\"translated\"}");

        ArgumentCaptor<IPacket> senderPacket = ArgumentCaptor.forClass(IPacket.class);
        verify(sender.connection).sendTCP(senderPacket.capture());
        assertEquals(THAI_FIRST, lobbyMessage(senderPacket.getValue()));

        ArgumentCaptor<IPacket> thaiPacket = ArgumentCaptor.forClass(IPacket.class);
        verify(thaiRecipient.connection).sendTCP(thaiPacket.capture());
        assertEquals(THAI_FIRST, lobbyMessage(thaiPacket.getValue()));

        ArgumentCaptor<IPacket> englishPacket = ArgumentCaptor.forClass(IPacket.class);
        verify(englishRecipient.connection).sendTCP(englishPacket.capture());
        assertEquals("translated", lobbyMessage(englishPacket.getValue()));
    }

    @Test
    void commandShortCircuitsProviderAndBroadcast() {
        ClientFixture sender = client("Sender", 1L, (short) 0, null, true, false);
        ClientFixture other = client("Other", 2L, (short) 2, null, true, true);
        when(gameManager.getClients()).thenReturn(
                new ConcurrentLinkedDeque<>(List.of(sender.client, other.client)));
        when(commandManager.isCommand("-translate on")).thenReturn(true);

        new ChatMessageLobbyPacketHandler().handle(
                sender.connection,
                CMSGChatMessageLobby.builder().unk((char) 1).message("-translate on").build()
        );

        verify(commandManager).handle(sender.connection, "-translate on");
        verify(sender.connection).sendTCP(any(IPacket.class));
        verify(other.connection, never()).sendTCP(any(IPacket.class));
        assertEquals(0, provider.requestCount);
    }

    private static Room runningRoom(int roomId) {
        Room room = new Room();
        room.setRoomId((short) roomId);
        room.setStatus(RoomStatus.Running);
        return room;
    }

    private static CMSGChatMessageRoom roomRequest(byte type, String message) {
        return CMSGChatMessageRoom.builder().type(type).message(message).build();
    }

    private static ClientFixture client(
            String name,
            long id,
            short position,
            Room room,
            boolean inLobby,
            boolean translate
    ) {
        FTClient client = mock(FTClient.class);
        FTConnection connection = mock(FTConnection.class);
        FTPlayer player = mock(FTPlayer.class);
        RoomPlayer roomPlayer = mock(RoomPlayer.class);

        when(connection.getClient()).thenReturn(client);
        when(client.getConnection()).thenReturn(connection);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        when(client.getActiveRoom()).thenReturn(room);
        when(client.isInLobby()).thenReturn(inLobby);
        when(client.isTranslateChatToEnglish()).thenReturn(translate);
        when(client.getChatDelivery()).thenReturn(new OrderedChatDelivery());
        when(player.getName()).thenReturn(name);
        when(player.getId()).thenReturn(id);
        when(roomPlayer.getPlayerId()).thenReturn(id);
        when(roomPlayer.getPosition()).thenReturn(position);
        return new ClientFixture(client, connection);
    }

    private static String lobbyMessage(IPacket packet) {
        return ((SMSGChatMessageLobby) packet).getMessage();
    }

    private static String roomMessage(IPacket packet) {
        return ((SMSGChatMessageRoom) packet).getMessage();
    }

    private static Object replaceStatic(Class<?> type, String fieldName, Object value) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, value);
        return previous;
    }

    private record ClientFixture(FTClient client, FTConnection connection) {
    }

    private static final class ControlledProvider {
        private final HttpClient client = mock(HttpClient.class);
        private final List<CompletableFuture<HttpResponse<String>>> pending = new ArrayList<>();
        private int requestCount;

        @SuppressWarnings({"rawtypes", "unchecked"})
        private ControlledProvider() {
            when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> {
                        requestCount++;
                        CompletableFuture<HttpResponse<String>> response = new CompletableFuture<>();
                        pending.add(response);
                        return response;
                    });
        }

        private LibreTranslateTranslationService service() {
            return new LibreTranslateTranslationService(
                    client,
                    URI.create("http://provider.invalid/translate"),
                    Duration.ofSeconds(5),
                    true
            );
        }

        private int pendingCount() {
            return pending.size();
        }

        private void complete(int index, String body) {
            CompletableFuture<HttpResponse<String>> future = pending.get(index);
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(body);
            future.complete(response);
        }

        private void completeAll(String body) {
            for (int index = 0; index < pending.size(); index++) {
                if (!pending.get(index).isDone()) {
                    complete(index, body);
                }
            }
        }
    }
}
