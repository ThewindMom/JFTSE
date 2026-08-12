package com.jftse.emulator.server.core.rabbit;

import com.jftse.emulator.server.core.manager.RelaySessionAuthorizationStore;
import com.jftse.emulator.server.core.manager.RelayManager;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jftse.rabbitmq", name = "enabled", havingValue = "true")
public class RelaySessionAuthorizationConsumer {
    private final RelaySessionAuthorizationStore authorizationStore;
    private final RelayManager relayManager;

    @RabbitListener(queues = "relay-session-authorization")
    public void receiveMessage(RelaySessionAuthorizationMessage message) {
        authorizationStore.put(message);
        if (Boolean.TRUE.equals(message.getRevoked())) {
            relayManager.revokeSessionGeneration(message.getGameSessionId(), message.getGeneration());
        } else {
            relayManager.revokeOtherSessionGenerations(message.getGameSessionId(), message.getGeneration());
        }
    }
}
