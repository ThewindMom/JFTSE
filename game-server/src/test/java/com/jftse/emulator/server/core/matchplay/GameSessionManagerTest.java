package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.life.room.GameSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionManagerTest {
    @Test
    void relayActorPolicySessionsUseNamespaceDisjointFromOrdinarySessionsAcrossRestarts() {
        GameSessionManager firstProcess = new GameSessionManager();
        firstProcess.init();

        int stalePolicySessionId = firstProcess.addRelayActorPolicyGameSession(new GameSession());
        assertTrue(stalePolicySessionId >= 100_000 && stalePolicySessionId < 200_000);

        GameSessionManager restartedProcess = new GameSessionManager();
        restartedProcess.init();
        for (int i = 0; i < 100; i++) {
            int ordinarySessionId = restartedProcess.addGameSession(new GameSession());
            assertTrue(ordinarySessionId >= 0 && ordinarySessionId < 100_000);
        }
    }
}
