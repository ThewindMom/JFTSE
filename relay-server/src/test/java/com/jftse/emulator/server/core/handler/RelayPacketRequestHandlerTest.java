package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.server.core.manager.RelayManager;
import com.jftse.emulator.server.core.manager.RelaySessionAuthorizationStore;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.shared.packets.CMSGDefault;
import com.jftse.server.core.shared.packets.relay.CMSGRelay;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelayPacketRequestHandlerTest {
    private Object previousRelayManager;
    private RelaySessionAuthorizationStore authorizationStore;
    private FTConnection connection;
    private FTConnection peerConnection;
    private FTClient client;

    @BeforeEach
    void setUp() {
        authorizationStore = new RelaySessionAuthorizationStore();
        authorizationStore.init();
        putPolicy(true);

        previousRelayManager = ReflectionTestUtils.getField(RelayManager.class, "instance");
        RelayManager relayManager = new RelayManager();
        ReflectionTestUtils.setField(relayManager, "sessionMap",
                new ConcurrentHashMap<Integer, ConcurrentLinkedDeque<FTClient>>());
        ReflectionTestUtils.setField(RelayManager.class, "instance", relayManager);

        client = new FTClient();
        client.setGameSessionId(150);
        client.setPlayerId(1000);
        connection = mock(FTConnection.class);
        client.setConnection(connection);
        when(connection.getClient()).thenReturn(client);

        FTClient peer = new FTClient();
        peer.setGameSessionId(150);
        peer.setPlayerId(2000);
        peerConnection = mock(FTConnection.class);
        peer.setConnection(peerConnection);

        relayManager.addClientToSession(150, client);
        relayManager.addClientToSession(150, peer);
    }

    @AfterEach
    void restoreRelayManager() {
        ReflectionTestUtils.setField(RelayManager.class, "instance", previousRelayManager);
    }

    @Test
    void ordinarySessionForwardsWellFormedUnknownInnerPacket() {
        putPolicy(false);
        byte[] innerPacket = unknownInnerPacket();

        new RelayPacketRequestHandler().handle(
                connection, CMSGRelay.builder().packet(innerPacket).build());

        verify(connection).sendTCP(any(CMSGDefault.class));
        verify(peerConnection).sendTCP(any(CMSGDefault.class));
        verify(connection, never()).queuePacket(any());
    }

    private void putPolicy(boolean ownedPetSession) {
        authorizationStore.put(RelaySessionAuthorizationMessage.builder()
                .gameSessionId(150)
                .battlemon(false)
                .ownedPetSession(ownedPetSession)
                .actorPositionsByPlayerId(Map.of(1000, ownedPetSession
                        ? List.of((short) 0, (short) 2)
                        : List.of((short) 0)))
                .build());
    }

    private static byte[] unknownInnerPacket() {
        byte[] innerPacket = new byte[8];
        innerPacket[4] = (byte) 0xFE;
        innerPacket[5] = 0x7F;
        return innerPacket;
    }

    @Test
    void ownedPetSessionDropsInnerPacketsShorterThanTheProtocolHeader() {
        RelayPacketRequestHandler handler = new RelayPacketRequestHandler();

        handler.handle(connection, CMSGRelay.builder().packet(new byte[7]).build());

        verify(connection, never()).queuePacket(any());
    }

    @Test
    void ownedPetSessionForwardsWellFormedUnknownInnerPacket() {
        byte[] innerPacket = unknownInnerPacket();

        new RelayPacketRequestHandler().handle(
                connection, CMSGRelay.builder().packet(innerPacket).build());

        verify(connection).sendTCP(any(CMSGDefault.class));
        verify(peerConnection).sendTCP(any(CMSGDefault.class));
        verify(connection, never()).queuePacket(any());
    }

    @Test
    void ownedPetSessionForwardsExactTwentyFiveByte3332WithoutQueuing() {
        byte[] innerPacket = new byte[25];
        innerPacket[4] = 0x32;
        innerPacket[5] = 0x33;

        new RelayPacketRequestHandler().handle(
                connection, CMSGRelay.builder().packet(innerPacket).build());

        verify(connection).sendTCP(any(CMSGDefault.class));
        verify(peerConnection).sendTCP(any(CMSGDefault.class));
        verify(connection, never()).queuePacket(any());
    }

    @Test
    void spectatorCannotForwardOpaqueOwnedPetRelayPacket() {
        client.setSpectator(true);

        new RelayPacketRequestHandler().handle(
                connection, CMSGRelay.builder().packet(unknownInnerPacket()).build());

        verify(connection, never()).sendTCP(any(CMSGDefault.class));
        verify(peerConnection, never()).sendTCP(any(CMSGDefault.class));
        verify(connection, never()).queuePacket(any());
    }
}
