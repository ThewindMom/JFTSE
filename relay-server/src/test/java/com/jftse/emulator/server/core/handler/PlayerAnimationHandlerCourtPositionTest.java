package com.jftse.emulator.server.core.handler;

import com.jftse.server.core.shared.packets.relay.CMSGPlayerAnimation;
import com.jftse.server.core.shared.rabbit.messages.MatchCourtPositionMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerAnimationHandlerCourtPositionTest {
    @Test
    void animationAbsoluteXAndYBecomeCourtXAndZ() {
        CMSGPlayerAnimation packet = CMSGPlayerAnimation.builder()
                .playerPosition((char) 1)
                .absoluteXPositionOnMap((short) 40)
                .absoluteYPositionOnMap((short) -40)
                .relativeXMovement((short) 0)
                .relativeYMovement((short) 0)
                .animationType((byte) 0)
                .build();

        MatchCourtPositionMessage msg = PlayerAnimationHandler.toCourtPosition(77, 9, packet);
        assertEquals(77, msg.getGameSessionId());
        assertEquals(9, msg.getPlayerId());
        assertEquals(1, msg.getPlayerPosition());
        assertEquals(40, msg.getX());
        assertEquals(-40, msg.getZ());
    }

    @Test
    void routingKeyMatchesGameServerCourtQueueBinding() {
        assertEquals("game.stats.match.court", MatchCourtPositionMessage.ROUTING_KEY);
    }
}
