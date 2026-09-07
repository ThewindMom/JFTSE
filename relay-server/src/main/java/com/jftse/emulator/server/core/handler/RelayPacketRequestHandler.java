package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.common.utilities.BitKit;
import com.jftse.emulator.server.core.manager.RelayManager;
import com.jftse.emulator.server.core.manager.RelaySessionAuthorizationStore;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.protocol.PacketRegistry;
import com.jftse.server.core.shared.packets.CMSGDefault;
import com.jftse.server.core.shared.packets.relay.CMSGRelay;
import lombok.extern.log4j.Log4j2;

@PacketId(CMSGRelay.PACKET_ID)
@Log4j2
public class RelayPacketRequestHandler implements PacketHandler<FTConnection, CMSGRelay> {
    @Override
    public void handle(FTConnection connection, CMSGRelay relay) {
        FTClient client = connection.getClient();
        if (client.getGameSessionId().isPresent()) {
            if (!RelaySessionAuthorizationStore.getInstance().canParticipate(client)) {
                return;
            }
            byte[] innerPacket = relay.getPacket();
            if (innerPacket == null || innerPacket.length < 8) {
                return;
            }
            int packetId = BitKit.bytesToShort(innerPacket, 4);
            IPacket relayPacket = PacketRegistry.decode(packetId, innerPacket);
            if (relayPacket instanceof CMSGDefault defaultPacket) {
                if (RelaySessionAuthorizationStore.getInstance()
                        .isBattlemon(client.getGameSessionId().get())) {
                    log.warn("Dropping unknown Battlemon relay packet id: 0x{}",
                            Integer.toHexString(packetId));
                    return;
                }
                // not found so its wrapped in CMSGDefault and prob not reversed yet
                // we must still relay it because the client expects the packet to function properly
                RelayManager.getInstance().getClientsInSession(client.getGameSessionId().get()).forEach(c -> {
                    if (c.getConnection() != null) c.getConnection().sendTCP(defaultPacket);
                });

                log.warn("Unknown packet id: 0x{} ({})", Integer.toHexString(defaultPacket.getPacketId()), (int) defaultPacket.getPacketId());
            } else {
                // just queue the packet for processing since we know about it
                connection.queuePacket(relayPacket);
            }
        }
    }
}
