package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.server.core.manager.RelayManager;
import com.jftse.emulator.server.core.manager.RelaySessionAuthorizationStore;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.protocol.IPacketTranslator;
import com.jftse.server.core.shared.packets.relay.CMSGSpiderMineExplode;
import com.jftse.server.core.shared.packets.relay.SMSGSpiderMineExplode;
import com.jftse.server.core.shared.packets.translator.SpiderMineExplodeTranslator;

@PacketId(CMSGSpiderMineExplode.PACKET_ID)
public class SpiderMineExplodeHandler implements PacketHandler<FTConnection, CMSGSpiderMineExplode> {
    private static final IPacketTranslator<SMSGSpiderMineExplode, CMSGSpiderMineExplode> translator = new SpiderMineExplodeTranslator();

    @Override
    public void handle(FTConnection connection, CMSGSpiderMineExplode packet) {
        FTClient client = connection.getClient();
        if (client == null) {
            return;
        }
        RelaySessionAuthorizationStore authorizationStore = RelaySessionAuthorizationStore.getInstance();
        if (client.getGameSessionId().isEmpty() || !authorizationStore.canParticipate(client) ||
                !authorizationStore.isAuthorizedActor(client.getGameSessionId().get(), packet.getTargetPosition())) {
            return;
        }
        SMSGSpiderMineExplode relayPacket = packet.translate(translator);
        RelayManager.getInstance().getClientsInSession(client.getGameSessionId().get()).forEach(c -> {
            if (c.getConnection() != null) c.getConnection().sendTCP(relayPacket);
        });
    }
}
