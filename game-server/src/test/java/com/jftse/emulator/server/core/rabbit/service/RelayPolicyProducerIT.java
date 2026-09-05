package com.jftse.emulator.server.core.rabbit.service;

import com.jftse.emulator.server.rabbit.RabbitMQConfig;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "JFTSE_AUDIT_RABBIT_HOST", matches = "jftse-audit-rabbit")
class RelayPolicyProducerIT {
    @Test
    void actualGameProducerRequiresRelayAcknowledgementForEveryModePolicyAndRemoval() {
        Object previous = ReflectionTestUtils.getField(RProducerService.class, "instance");
        var connection = new CachingConnectionFactory(System.getenv("JFTSE_AUDIT_RABBIT_HOST"));
        connection.setUsername(System.getenv("JFTSE_AUDIT_RABBIT_USER"));
        connection.setPassword(System.getenv("JFTSE_AUDIT_RABBIT_PASSWORD"));
        try {
            RabbitMQConfig config = new RabbitMQConfig();
            ReflectionTestUtils.setField(config, "exchangeName", "jftse-audit-policy");
            var template = config.rabbitTemplate(connection);
            template.setReplyTimeout(5000);
            assertEquals("ready", template.receiveAndConvert("jftse-audit-ready", 60000));
            RProducerService producer = new RProducerService(config, template, null);
            for (int mode = 0; mode < 3; mode++) {
                Map<Integer, List<Short>> actors = mode == 0
                        ? Map.of(101, List.of((short) 0), 102, List.of((short) 1))
                        : mode == 1 ? Map.of(101, List.of((short) 0, (short) 2), 102, List.of((short) 1))
                        : Map.of(101, List.of((short) 0, (short) 2), 102, List.of((short) 1, (short) 3));
                var message = RelaySessionAuthorizationMessage.builder().gameSessionId(100100 + mode)
                        .battlemon(mode == 2).ownedPetSession(mode > 0)
                        .actorPositionsByPlayerId(actors).battlemonControllerByPlayerId(Map.of(101, true, 102, false)).build();
                assertTrue(producer.sendRelayActorPolicy(message, "isolated-game-audit"));
                assertNotNull(message.getCorrelationId());
                message.setRemove(true);
                assertTrue(producer.sendRelayActorPolicy(message, "isolated-game-audit"));
            }
            assertEquals("verified", template.receiveAndConvert("jftse-audit-done", 10000));
        } finally {
            connection.destroy();
            ReflectionTestUtils.setField(RProducerService.class, "instance", previous);
        }
    }
}
