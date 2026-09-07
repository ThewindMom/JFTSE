package com.jftse.emulator.server.core.manager;

import com.jftse.emulator.server.core.rabbit.RelaySessionAuthorizationConsumer;
import com.jftse.emulator.server.rabbit.RabbitMQConfig;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "JFTSE_AUDIT_RABBIT_HOST", matches = "jftse-audit-rabbit")
class RelayPolicyConsumerIT {
    @Test
    void actualRelayConsumerInstallsAndRemovesGamePoliciesOverBroker() throws Exception {
        var connection = new CachingConnectionFactory(System.getenv("JFTSE_AUDIT_RABBIT_HOST"));
        connection.setUsername(System.getenv("JFTSE_AUDIT_RABBIT_USER"));
        connection.setPassword(System.getenv("JFTSE_AUDIT_RABBIT_PASSWORD"));
        SimpleMessageListenerContainer listener = new SimpleMessageListenerContainer(connection);
        try {
            RabbitMQConfig config = new RabbitMQConfig();
            ReflectionTestUtils.setField(config, "exchangeName", "jftse-audit-policy");
            ReflectionTestUtils.setField(config, "defaultQueueName", "jftse-audit-default");
            ReflectionTestUtils.setField(config, "defaultBindingKeys", List.of("audit.unused"));
            RabbitAdmin admin = new RabbitAdmin(connection);
            admin.declareExchange(config.exchange());
            for (var declarable : config.rabbitDeclarables().getDeclarables()) {
                if (declarable instanceof Queue queue) admin.declareQueue(queue);
                if (declarable instanceof org.springframework.amqp.core.Binding binding) admin.declareBinding(binding);
            }
            admin.declareQueue(new Queue("jftse-audit-ready", false));
            admin.declareQueue(new Queue("jftse-audit-done", false));
            var template = config.rabbitTemplate(connection);
            RelaySessionAuthorizationStore store = new RelaySessionAuthorizationStore();
            CountDownLatch received = new CountDownLatch(6);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            RelaySessionAuthorizationConsumer consumer = new RelaySessionAuthorizationConsumer(store) {
                @Override
                public Integer receiveMessage(RelaySessionAuthorizationMessage message) {
                    try {
                        Integer result = super.receiveMessage(message);
                        if (Boolean.TRUE.equals(message.getRemove())) {
                            assertTrue(store.find(result).isEmpty());
                            assertFalse(store.isAuthorizedActor(result, 0));
                        } else {
                            var installed = store.find(result).orElseThrow();
                            assertEquals(message.getBattlemon(), installed.battlemon());
                            assertEquals(message.getOwnedPetSession(), installed.ownedPetSession());
                            message.getActorPositionsByPlayerId().forEach((player, positions) ->
                                    assertEquals(java.util.Set.copyOf(positions), installed.actorPositionsByPlayerId().get(player)));
                            assertEquals(message.getBattlemonControllerByPlayerId(), installed.battlemonControllerByPlayerId());
                        }
                        return result;
                    } catch (Throwable error) {
                        failure.set(error);
                        throw error;
                    } finally {
                        received.countDown();
                    }
                }
            };
            listener.setQueueNames("relay-session-authorization");
            listener.setMessageListener(new MessageListenerAdapter(consumer, config.jsonMessageConverter()) {{
                setDefaultListenerMethod("receiveMessage");
            }});
            listener.start();
            template.convertAndSend("jftse-audit-ready", "ready");
            assertTrue(received.await(90, TimeUnit.SECONDS), "game producer did not complete six RPCs");
            assertNull(failure.get());
            template.convertAndSend("jftse-audit-done", "verified");
        } finally {
            listener.stop();
            connection.destroy();
        }
    }
}
