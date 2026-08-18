package com.jftse.emulator.server.core.rabbit;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.matchplay.guardian.GuardianShieldPadService;
import com.jftse.server.core.shared.rabbit.messages.MatchCourtPositionMessage;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "jftse.rabbitmq", name = "enabled", havingValue = "true")
@Log4j2
@AllArgsConstructor
public class CourtPositionConsumer {
    private final GameSessionManager gameSessionManager;

    @RabbitListener(queues = "court-position-queue")
    public void receiveMessage(MatchCourtPositionMessage message) {
        if (message == null
                || message.getGameSessionId() == null
                || message.getX() == null
                || message.getZ() == null) {
            return;
        }

        GameSession session = gameSessionManager.getGameSessionBySessionId(message.getGameSessionId());
        if (session == null || !session.isGuardianMode()) {
            return;
        }

        GuardianShieldPadService service = GuardianShieldPadService.getInstance();
        if (service == null) {
            return;
        }

        int playerId = message.getPlayerId() == null ? 0 : message.getPlayerId();
        int playerPosition = message.getPlayerPosition() == null ? 0 : message.getPlayerPosition();
        service.onCourtPosition(message.getGameSessionId(), playerId, playerPosition, message.getX(), message.getZ());
    }
}
