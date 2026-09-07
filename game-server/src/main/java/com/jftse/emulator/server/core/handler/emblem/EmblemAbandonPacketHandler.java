package com.jftse.emulator.server.core.handler.emblem;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.emblem.CMSGEmblemAbandon;
import com.jftse.server.core.shared.packets.emblem.S2CEmblemListPacket;

@PacketId(CMSGEmblemAbandon.PACKET_ID)
public class EmblemAbandonPacketHandler implements PacketHandler<FTConnection, CMSGEmblemAbandon> {
    @Override public void handle(FTConnection c, CMSGEmblemAbandon p) {
        if (!c.getClient().hasPlayer()) { c.sendTCP(S2CEmblemListPacket.sentinel((short)-1)); return; }
        var player = c.getClient().getPlayer(); var service = ServiceManager.getInstance().getEmblemQuestService();
        service.abandon(player.getId(), p.getQuestIndex());
        c.sendTCP(S2CEmblemListPacket.success(service.list(player.getId())));
    }
}
