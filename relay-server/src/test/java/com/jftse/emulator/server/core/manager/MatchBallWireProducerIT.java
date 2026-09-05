package com.jftse.emulator.server.core.manager;

import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.rabbit.RabbitMQConfig;
import com.jftse.server.core.constants.BallHitAction;
import com.jftse.server.core.shared.rabbit.messages.MatchBallSyncMessage;
import com.jftse.server.core.thread.ThreadManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "JFTSE_AUDIT_RABBIT_HOST", matches = "jftse-audit-rabbit")
class MatchBallWireProducerIT {
    @Test
    void actualAsyncRelayProducerDeliversServeStrokeDuplicatesAndLateMessages() {
        var connection = new CachingConnectionFactory(System.getenv("JFTSE_AUDIT_RABBIT_HOST"));
        connection.setUsername(System.getenv("JFTSE_AUDIT_RABBIT_USER"));
        connection.setPassword(System.getenv("JFTSE_AUDIT_RABBIT_PASSWORD"));
        var threads = new ThreadManager();
        threads.init();
        try {
            var config = new RabbitMQConfig();
            ReflectionTestUtils.setField(config, "exchangeName", "jftse-audit-ball");
            var template = config.rabbitTemplate(connection);
            var admin = new org.springframework.amqp.rabbit.core.RabbitAdmin(connection);
            admin.declareQueue(new org.springframework.amqp.core.Queue("jftse-audit-ball-ready", false));
            admin.declareQueue(new org.springframework.amqp.core.Queue("jftse-audit-ball-ack", false));
            assertEquals("ready", template.receiveAndConvert("jftse-audit-ball-ready", 60000));
            var producer = new RProducerService(config, template, threads);
            int[] sessions = {700, 700, 700, 701, 701, 700, 701};
            for (int step = 0; step < sessions.length; step++) {
                boolean serve = step == 0 || step == 3;
                var message = MatchBallSyncMessage.builder().gameSessionId(sessions[step])
                        .playerId(serve ? 101 : 102).playerPos(serve ? 0 : 1)
                        .hitAct(serve ? BallHitAction.SERVE : BallHitAction.STROKE).powerLevel(1).build();
                producer.send(message, "game.stats.match.rally", "isolated-relay-audit");
                assertEquals(step, template.receiveAndConvert("jftse-audit-ball-ack", 10000));
            }
        } finally {
            threads.onExit();
            connection.destroy();
        }
    }
}
