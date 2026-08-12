package com.jftse.emulator.server.core.handler.emblem;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.EmblemQuestStatus;
import com.jftse.server.core.shared.packets.emblem.CMSGEmblemEquip;
import com.jftse.server.core.shared.packets.emblem.S2CEmblemEquipmentPacket;
import com.jftse.server.core.shared.packets.emblem.S2CEmblemListPacket;

import java.util.List;

@PacketId(CMSGEmblemEquip.PACKET_ID)
public class EmblemEquipPacketHandler implements PacketHandler<FTConnection, CMSGEmblemEquip> {
    @Override public void handle(FTConnection c, CMSGEmblemEquip p) {
        if (!c.getClient().hasPlayer()) { c.sendTCP(S2CEmblemListPacket.sentinel((short)-1)); return; }
        var player = c.getClient().getPlayer(); var service = ServiceManager.getInstance().getEmblemQuestService();
        if (service.equip(player.getPlayerRef(), List.of((int)p.getEmblem1(), (int)p.getEmblem2(), (int)p.getEmblem3(), (int)p.getEmblem4())) == EmblemQuestStatus.SUCCESS) {
            player.loadEmblemEquipment();
            c.sendTCP(new S2CEmblemEquipmentPacket(player.getEmblemEquipment()));
        }
        c.sendTCP(S2CEmblemListPacket.success(service.list(player.getId())));
    }
}
