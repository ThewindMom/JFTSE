package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.server.core.manager.RelaySessionAuthorizationStore;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.shared.packets.relay.CMSGRelay;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelayPacketRequestHandlerTest {
    private FTConnection connection;

    @BeforeEach
    void setUp() {
        RelaySessionAuthorizationStore store = new RelaySessionAuthorizationStore();
        store.init();
        store.put(RelaySessionAuthorizationMessage.builder()
                .gameSessionId(150)
                .battlemon(false)
                .ownedPetSession(true)
                .actorPositionsByPlayerId(Map.of(1000, List.of((short) 0, (short) 2)))
                .build());

        FTClient client = new FTClient();
        client.setGameSessionId(150);
        client.setPlayerId(1000);
        connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
    }

    @Test
    void ownedPetSessionDropsInnerPacketsShorterThanTheProtocolHeader() {
        RelayPacketRequestHandler handler = new RelayPacketRequestHandler();

        handler.handle(connection, CMSGRelay.builder().packet(new byte[7]).build());

        verify(connection, never()).queuePacket(any());
    }
}
