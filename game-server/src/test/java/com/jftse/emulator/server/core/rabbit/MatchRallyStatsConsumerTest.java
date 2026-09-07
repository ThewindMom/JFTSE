package com.jftse.emulator.server.core.rabbit;

import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.server.core.constants.BallHitAction;
import com.jftse.server.core.shared.rabbit.messages.MatchBallSyncMessage;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchRallyStatsConsumerTest {
    @Test
    void nullPlayerIdIsRejectedBeforeLookingUpOrAllocatingSessionState() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        MatchRallyStatsConsumer consumer = new MatchRallyStatsConsumer(sessionManager);
        MatchBallSyncMessage message = message(12345, null);

        consumer.receiveMessage(message);

        verify(sessionManager, never()).getGameSessionBySessionId(12345);
    }

    @Test
    void eventForSessionOwnedByAnotherGameServerIsRejected() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        MatchRallyStatsConsumer consumer = new MatchRallyStatsConsumer(sessionManager);
        MatchBallSyncMessage message = message(71234, 99);

        consumer.receiveMessage(message);

        verify(sessionManager).getGameSessionBySessionId(71234);
    }

    private static MatchBallSyncMessage message(int gameSessionId, Integer playerId) {
        MatchBallSyncMessage message = mock(MatchBallSyncMessage.class);
        when(message.getGameSessionId()).thenReturn(gameSessionId);
        when(message.getPlayerId()).thenReturn(playerId);
        when(message.getPlayerPos()).thenReturn(0);
        when(message.getHitAct()).thenReturn(BallHitAction.SERVE);
        return message;
    }
}
