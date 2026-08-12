package com.jftse.emulator.server.core.handler.emblem;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.emblem.CMSGEmblemAccept;
import com.jftse.server.core.shared.packets.emblem.S2CEmblemListPacket;

@PacketId(CMSGEmblemAccept.PACKET_ID)
public class EmblemAcceptPacketHandler implements PacketHandler<FTConnection, CMSGEmblemAccept> {
    @Override public void handle(FTConnection c, CMSGEmblemAccept p) {
        if (!c.getClient().hasPlayer()) { c.sendTCP(S2CEmblemListPacket.sentinel((short)-1)); return; }
        var player = c.getClient().getPlayer(); var service = ServiceManager.getInstance().getEmblemQuestService();
        service.accept(player.getPlayerRef(), p.getQuestIndex());
        c.sendTCP(S2CEmblemListPacket.success(service.list(player.getId())));
    }
}
