package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.server.core.manager.RelayManager;
import com.jftse.emulator.server.core.manager.RelaySessionAuthorizationStore;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.protocol.IPacketTranslator;
import com.jftse.server.core.shared.packets.relay.CMSGSpiderMinePlaced;
import com.jftse.server.core.shared.packets.relay.SMSGSpiderMinePlaced;
import com.jftse.server.core.shared.packets.translator.SpiderMinePlacedTranslator;

@PacketId(CMSGSpiderMinePlaced.PACKET_ID)
public class SpiderMinePlacedHandler implements PacketHandler<FTConnection, CMSGSpiderMinePlaced> {
    private static final IPacketTranslator<SMSGSpiderMinePlaced, CMSGSpiderMinePlaced> translator = new SpiderMinePlacedTranslator();

    @Override
    public void handle(FTConnection connection, CMSGSpiderMinePlaced packet) {
        FTClient client = connection.getClient();
        if (client == null || client.getGameSessionId().isPresent() &&
                RelaySessionAuthorizationStore.getInstance().isBattlemon(client.getGameSessionId().get()) &&
                packet.getPosition() >= 2 ||
                !RelaySessionAuthorizationStore.getInstance().canAct(client, packet.getPosition())) {
            return;
        }
        SMSGSpiderMinePlaced relayPacket = packet.translate(translator);
        client.getGameSessionId().ifPresent(sessionId -> RelayManager.getInstance()
                .getClientsInSession(sessionId).forEach(c -> {
                    if (c.getConnection() != null) c.getConnection().sendTCP(relayPacket);
                }));
    }
}
