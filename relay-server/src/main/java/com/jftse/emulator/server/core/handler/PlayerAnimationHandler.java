package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.server.core.manager.RelayManager;
import com.jftse.emulator.server.core.manager.RelaySessionAuthorizationStore;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.item.BattlemonController;
import com.jftse.server.core.protocol.IPacketTranslator;
import com.jftse.server.core.shared.packets.relay.CMSGPlayerAnimation;
import com.jftse.server.core.shared.packets.relay.SMSGPlayerAnimation;
import com.jftse.server.core.shared.packets.translator.PlayerAnimationTranslator;

@PacketId(CMSGPlayerAnimation.PACKET_ID)
public class PlayerAnimationHandler implements PacketHandler<FTConnection, CMSGPlayerAnimation> {
    private static final IPacketTranslator<SMSGPlayerAnimation, CMSGPlayerAnimation> translator = new PlayerAnimationTranslator();

    @Override
    public void handle(FTConnection connection, CMSGPlayerAnimation packet) {
        boolean controllerCommand = BattlemonController.isPetActor(packet.getPlayerPosition()) &&
                BattlemonController.coverageArea(packet.getAnimationType()).isPresent();
        if (!RelaySessionAuthorizationStore.getInstance()
                .canAct(connection.getClient(), packet.getPlayerPosition(), controllerCommand)) {
            return;
        }
        SMSGPlayerAnimation relayPacket = packet.translate(translator);
        connection.getClient().getGameSessionId().ifPresent(sessionId -> RelayManager.getInstance()
                .getClientsInSession(sessionId).forEach(c -> {
                    if (c.getConnection() != null) c.getConnection().sendTCP(relayPacket);
                }));
    }
}
