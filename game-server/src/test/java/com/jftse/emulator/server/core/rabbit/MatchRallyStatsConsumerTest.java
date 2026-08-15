package com.jftse.emulator.server.core.rabbit;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.server.core.constants.BallHitAction;
import com.jftse.server.core.shared.rabbit.messages.MatchBallSyncMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchRallyStatsConsumerTest {
    @Test
    void onlyRelayObservedServeOpensPointAcceptance() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        GameSession session = new GameSession(true);
        MatchRallyStatsConsumer consumer = new MatchRallyStatsConsumer(sessionManager);
        when(sessionManager.getGameSessionBySessionId(42)).thenReturn(session);

        consumer.receiveMessage(message(BallHitAction.STROKE));
        assertFalse(session.tryHandleRallyPoint());

        consumer.receiveMessage(message(BallHitAction.SERVE));
        assertTrue(session.tryHandleRallyPoint());
        assertFalse(session.tryHandleRallyPoint());
    }

    private static MatchBallSyncMessage message(BallHitAction action) {
        return MatchBallSyncMessage.builder()
                .gameSessionId(42)
                .playerPos(4)
                .hitAct(action)
                .build();
    }
}
