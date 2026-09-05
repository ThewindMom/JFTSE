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

    @Test
    void lateStrokeAfterSessionRemovalCannotRecreateStatistics() {
        GameSessionManager manager = mock(GameSessionManager.class);
        GameSession session = new GameSession();
        when(manager.getGameSessionBySessionId(42)).thenReturn(session);
        MatchRallyStatsConsumer consumer = new MatchRallyStatsConsumer(manager);
        var stroke = MatchBallSyncMessage.builder().gameSessionId(42).playerId(101)
                .playerPos(0).hitAct(BallHitAction.STROKE).build();
        consumer.receiveMessage(stroke);
        org.junit.jupiter.api.Assertions.assertEquals(1, consumer.getPlayerStats(42, 101).getStroke());
        consumer.clearSession(42);
        when(manager.getGameSessionBySessionId(42)).thenReturn(null);
        consumer.receiveMessage(stroke);
        org.junit.jupiter.api.Assertions.assertEquals(0, consumer.getPlayerStats(42, 101).getStroke());
        assertTrue(((java.util.Map<?, ?>) org.springframework.test.util.ReflectionTestUtils
                .getField(consumer, "rallyStateMap")).isEmpty());
    }

    @Test
    void cleanupCannotBeOvertakenByAnAlreadyAcceptedStroke() throws Exception {
        GameSessionManager manager = mock(GameSessionManager.class);
        GameSession session = new GameSession();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var lookups = new java.util.concurrent.atomic.AtomicInteger();
        var present = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(manager.getGameSessionBySessionId(42)).thenAnswer(call -> {
            if (lookups.incrementAndGet() == 2) {
                entered.countDown();
                assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
            }
            return present.get() ? session : null;
        });
        when(manager.getGameSessionBySessionId(43)).thenReturn(new GameSession());
        MatchRallyStatsConsumer consumer = new MatchRallyStatsConsumer(manager);
        var other = MatchBallSyncMessage.builder().gameSessionId(43).playerId(102)
                .playerPos(1).hitAct(BallHitAction.STROKE).build();
        consumer.receiveMessage(other);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var receive = executor.submit(() -> consumer.receiveMessage(MatchBallSyncMessage.builder()
                    .gameSessionId(42).playerId(101).playerPos(0).hitAct(BallHitAction.STROKE).build()));
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var cleanup = executor.submit(() -> { consumer.clearSession(42); present.set(false); });
            try {
                cleanup.get(200, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException expectedSerialization) {
                // A cleanup serialized behind the in-flight mutation must finish after release.
            } finally {
                release.countDown();
            }
            receive.get(5, java.util.concurrent.TimeUnit.SECONDS);
            cleanup.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        org.junit.jupiter.api.Assertions.assertEquals(0, consumer.getPlayerStats(42, 101).getStroke());
        org.junit.jupiter.api.Assertions.assertEquals(1, consumer.getPlayerStats(43, 102).getStroke());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(43),
                ((java.util.Map<?, ?>) org.springframework.test.util.ReflectionTestUtils
                        .getField(consumer, "rallyStateMap")).keySet());
    }

    @Test
    void realAbortCleanupRejectsStrokeBetweenClearAndRegistryRemoval() throws Exception {
        var manager = new GameSessionManager();
        var sessions = new java.util.concurrent.ConcurrentHashMap<Integer, GameSession>();
        sessions.put(42, new GameSession());
        sessions.put(43, new GameSession());
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "gameSessionList", sessions);
        var consumer = org.mockito.Mockito.spy(new MatchRallyStatsConsumer(manager));
        var gameManager = mock(com.jftse.emulator.server.core.manager.GameManager.class);
        org.springframework.test.util.ReflectionTestUtils.setField(gameManager, "gameSessionManager", manager);
        org.springframework.test.util.ReflectionTestUtils.setField(gameManager, "matchRallyStatsConsumer", consumer);
        var original = sessions.get(42);
        org.mockito.Mockito.doCallRealMethod().when(gameManager).cleanupGameSession(42, original, null);
        var cleared = new java.util.concurrent.CountDownLatch(1);
        var resume = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call -> {
            call.callRealMethod();
            cleared.countDown();
            assertTrue(resume.await(5, java.util.concurrent.TimeUnit.SECONDS));
            return null;
        }).when(consumer).clearSession(42);
        var stroke = MatchBallSyncMessage.builder().gameSessionId(42).playerId(101)
                .playerPos(0).hitAct(BallHitAction.STROKE).build();
        consumer.receiveMessage(stroke);
        consumer.receiveMessage(MatchBallSyncMessage.builder().gameSessionId(43).playerId(102)
                .playerPos(1).hitAct(BallHitAction.STROKE).build());
        assertFalse(original.getCompletionHandled().get(), "abort does not claim successful completion");
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var cleanup = executor.submit(() -> gameManager.cleanupGameSession(42, original, null));
            try {
                assertTrue(cleared.await(5, java.util.concurrent.TimeUnit.SECONDS));
                consumer.receiveMessage(stroke);
            } finally {
                resume.countDown();
            }
            cleanup.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        org.junit.jupiter.api.Assertions.assertNull(manager.getGameSessionBySessionId(42));
        org.junit.jupiter.api.Assertions.assertEquals(0, consumer.getPlayerStats(42, 101).getStroke());
        org.junit.jupiter.api.Assertions.assertEquals(1, consumer.getPlayerStats(43, 102).getStroke());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(43),
                ((java.util.Map<?, ?>) org.springframework.test.util.ReflectionTestUtils
                        .getField(consumer, "rallyStateMap")).keySet());
    }

    private static MatchBallSyncMessage message(BallHitAction action) {
        return MatchBallSyncMessage.builder()
                .gameSessionId(42)
                .playerPos(4)
                .hitAct(action)
                .build();
    }
}
