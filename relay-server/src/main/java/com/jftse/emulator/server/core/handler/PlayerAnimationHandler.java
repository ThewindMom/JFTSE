package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.server.core.manager.RelayManager;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.protocol.IPacketTranslator;
import com.jftse.server.core.shared.packets.relay.CMSGPlayerAnimation;
import com.jftse.server.core.shared.packets.relay.SMSGPlayerAnimation;
import com.jftse.server.core.shared.packets.translator.PlayerAnimationTranslator;
import com.jftse.server.core.shared.rabbit.messages.MatchCourtPositionMessage;

import java.util.List;

@PacketId(CMSGPlayerAnimation.PACKET_ID)
public class PlayerAnimationHandler implements PacketHandler<FTConnection, CMSGPlayerAnimation> {
    private static final IPacketTranslator<SMSGPlayerAnimation, CMSGPlayerAnimation> translator = new PlayerAnimationTranslator();

    @Override
    public void handle(FTConnection connection, CMSGPlayerAnimation packet) {
        SMSGPlayerAnimation relayPacket = packet.translate(translator);
        connection.getClient().getGameSessionId().ifPresent(gameSessionId -> {
            final List<FTClient> clients = RelayManager.getInstance().getClientsInSession(gameSessionId);
            clients.forEach(c -> {
                if (c.getConnection() != null)
                    c.getConnection().sendTCP(relayPacket);
            });

            publishCourtPosition(connection.getClient(), gameSessionId, packet);
        });
    }

    static MatchCourtPositionMessage toCourtPosition(Integer gameSessionId, int playerId, CMSGPlayerAnimation packet) {
        return MatchCourtPositionMessage.fromAnimation(
                gameSessionId,
                playerId,
                packet.getPlayerPosition(),
                packet.getAbsoluteXPositionOnMap(),
                packet.getAbsoluteYPositionOnMap());
    }

    private void publishCourtPosition(FTClient client, Integer gameSessionId, CMSGPlayerAnimation packet) {
        RProducerService producer = RProducerService.getInstance();
        if (producer == null) {
            return;
        }
        MatchCourtPositionMessage message = toCourtPosition(gameSessionId, client.getPlayerId(), packet);
        producer.send(message, MatchCourtPositionMessage.ROUTING_KEY, "GuardianShieldPads(RelayServer)");
    }
}
