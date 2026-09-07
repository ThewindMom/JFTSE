package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.matchplay.event.Fireable;
import com.jftse.emulator.server.net.FTClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameSessionManagerTest {
    @Test
    void discardClearsEveryClientAssociationAndScheduledEvent() {
        GameSessionManager manager = new GameSessionManager();
        manager.init();
        GameSession session = new GameSession();
        int sessionId = manager.addGameSession(session);
        FTClient participant = client(sessionId);
        FTClient spectator = client(sessionId);
        Fireable fireable = mock(Fireable.class);
        session.getClients().add(participant);
        session.getClients().add(spectator);
        session.getFireables().add(fireable);

        assertTrue(manager.discardGameSession(sessionId, session));

        verify(participant).setActiveGameSession(null);
        verify(spectator).setActiveGameSession(null);
        verify(fireable).setCancelled(true);
        assertTrue(session.getClients().isEmpty());
        assertTrue(session.getFireables().isEmpty());
        assertNull(manager.getGameSessionBySessionId(sessionId));
    }

    private FTClient client(int sessionId) {
        FTClient client = mock(FTClient.class);
        when(client.getGameSessionId()).thenReturn(sessionId);
        return client;
    }
}
