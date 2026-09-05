package com.jftse.emulator.server.core.rabbit;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.rabbit.RabbitMQConfig;
import com.jftse.server.core.shared.rabbit.messages.MatchBallSyncMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "JFTSE_AUDIT_RABBIT_HOST", matches = "jftse-audit-rabbit")
class MatchBallWireConsumerIT {
    @Test
    void realRelayMessagesAdvanceRalliesButCannotRecreateClearedSessions() throws Exception {
        var connection = new CachingConnectionFactory(System.getenv("JFTSE_AUDIT_RABBIT_HOST"));
        connection.setUsername(System.getenv("JFTSE_AUDIT_RABBIT_USER"));
        connection.setPassword(System.getenv("JFTSE_AUDIT_RABBIT_PASSWORD"));
        var listener = new SimpleMessageListenerContainer(connection);
        try {
            var config = new RabbitMQConfig();
            ReflectionTestUtils.setField(config, "exchangeName", "jftse-audit-ball");
            var template = config.rabbitTemplate(connection);
            var admin = new RabbitAdmin(connection);
            admin.declareExchange(config.exchange());
            for (String queue : new String[]{"jftse-audit-ball-ready", "jftse-audit-ball-ack", "match-queue"})
                admin.declareQueue(new Queue(queue, false));
            admin.declareBinding(new Binding("match-queue", Binding.DestinationType.QUEUE,
                    "jftse-audit-ball", "game.stats.match.rally", null));
            Map<Integer, GameSession> sessions = new ConcurrentHashMap<>();
            sessions.put(700, new GameSession(false));
            sessions.put(701, new GameSession(false));
            sessions.get(700).setMatchplayGame(mock(com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame.class));
            sessions.get(701).setMatchplayGame(mock(com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame.class));
            var manager = mock(GameSessionManager.class);
            when(manager.getGameSessionBySessionId(anyInt())).thenAnswer(call -> sessions.get(call.getArgument(0)));
            var received = new CountDownLatch(7);
            var sequence = new AtomicInteger();
            var failure = new AtomicReference<Throwable>();
            var consumer = new MatchRallyStatsConsumer(manager) {
                @Override
                public void receiveMessage(MatchBallSyncMessage message) {
                    int step = sequence.getAndIncrement();
                    try {
                        assertNotNull(message.getCorrelationId());
                        assertEquals("isolated-relay-audit", message.getSender());
                        super.receiveMessage(message);
                        int id = message.getGameSessionId();
                        if (step == 0 || step == 3) {
                            assertEquals(1, getPlayerStats(id, 101).getServe());
                        }
                        if (step == 2 || step == 4) {
                            assertEquals(step == 2 ? 2 : 1, getPlayerStats(id, 102).getStroke());
                            assertEquals(0, getPlayerStats(id, 101).getStroke());
                            assertTrue(sessions.get(id).tryHandleRallyPoint());
                            assertFalse(sessions.get(id).tryHandleRallyPoint());
                            var result = onPoint(id, false);
                            assertEquals(0, result.serverPosition());
                            assertEquals(1, result.lastHitterPosition());
                            assertEquals(1, result.rallyCount());
                            assertEquals(step == 2, result.returnAce());
                            clearSession(id);
                            sessions.remove(id);
                        }
                        if (step >= 5) {
                            assertEquals(0, getPlayerStats(id, 102).getStroke());
                            assertTrue(((Map<?, ?>) ReflectionTestUtils.getField(this, "rallyStateMap")).isEmpty());
                            assertTrue(((Map<?, ?>) ReflectionTestUtils.getField(this, "playerStatsMap")).isEmpty());
                        }
                    } catch (Throwable error) {
                        failure.compareAndSet(null, error);
                    } finally {
                        template.convertAndSend("jftse-audit-ball-ack", step);
                        received.countDown();
                    }
                }
            };
            var adapter = new MessageListenerAdapter(consumer, config.jsonMessageConverter());
            adapter.setDefaultListenerMethod("receiveMessage");
            listener.setQueueNames("match-queue");
            listener.setMessageListener(adapter);
            listener.start();
            template.convertAndSend("jftse-audit-ball-ready", "ready");
            assertTrue(received.await(60, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("relay/game ball contract", failure.get());
        } finally {
            listener.stop();
            connection.destroy();
        }
    }
}
