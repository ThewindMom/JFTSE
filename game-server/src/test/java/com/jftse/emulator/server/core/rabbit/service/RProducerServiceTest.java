package com.jftse.emulator.server.core.rabbit.service;

import com.jftse.emulator.server.rabbit.RabbitMQConfig;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import com.jftse.server.core.thread.ThreadManager;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RProducerServiceTest {
    @Test
    void relayActorPolicyRequiresMatchingRelayAcknowledgement() {
        RabbitMQConfig config = mock(RabbitMQConfig.class);
        when(config.getExchangeName()).thenReturn("jftse");
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RProducerService producer = new RProducerService(
                config, rabbitTemplate, mock(ThreadManager.class));
        RelaySessionAuthorizationMessage message = RelaySessionAuthorizationMessage.builder()
                .gameSessionId(12345)
                .battlemon(true)
                .build();

        when(rabbitTemplate.convertSendAndReceive(
                "jftse", RelaySessionAuthorizationMessage.ROUTING_KEY, message))
                .thenReturn(12345);
        assertTrue(producer.sendRelayActorPolicy(message, "test"));
        assertNotNull(message.getCorrelationId());

        when(rabbitTemplate.convertSendAndReceive(
                "jftse", RelaySessionAuthorizationMessage.ROUTING_KEY, message))
                .thenReturn(null);
        assertFalse(producer.sendRelayActorPolicy(message, "test"));

        when(rabbitTemplate.convertSendAndReceive(
                "jftse", RelaySessionAuthorizationMessage.ROUTING_KEY, message))
                .thenReturn(54321);
        assertFalse(producer.sendRelayActorPolicy(message, "test"));
    }
}
