package com.jftse.emulator.server.core.handler.emblem;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.emblem.CMSGEmblemList;
import com.jftse.server.core.shared.packets.emblem.S2CEmblemListPacket;

@PacketId(CMSGEmblemList.PACKET_ID)
public class EmblemListRequestPacketHandler implements PacketHandler<FTConnection, CMSGEmblemList> {
    @Override
    public void handle(FTConnection connection, CMSGEmblemList packet) {
        boolean hasPlayer = connection.getClient().hasPlayer();
        if (!hasPlayer) {
            connection.sendTCP(S2CEmblemListPacket.sentinel((short)-1));
            return;
        }
        long playerId = connection.getClient().getPlayer().getId();
        connection.sendTCP(S2CEmblemListPacket.success(
                ServiceManager.getInstance().getEmblemQuestService().list(playerId)));
    }
}
