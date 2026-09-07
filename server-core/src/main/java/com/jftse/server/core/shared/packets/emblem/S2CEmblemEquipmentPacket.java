package com.jftse.server.core.shared.packets.emblem;

import com.jftse.entities.database.model.emblem.PlayerEmblemEquipment;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

public final class S2CEmblemEquipmentPacket extends Packet {
    public S2CEmblemEquipmentPacket(PlayerEmblemEquipment equipment) {
        super(PacketOperations.S2CEmblemEquipment);
        if (equipment == null) {
            write((short) 0, (short) 0, (short) 0, (short) 0);
            return;
        }
        write(
                equipment.getSlot1() == null ? (short) 0 : equipment.getSlot1(),
                equipment.getSlot2() == null ? (short) 0 : equipment.getSlot2(),
                equipment.getSlot3() == null ? (short) 0 : equipment.getSlot3(),
                equipment.getSlot4() == null ? (short) 0 : equipment.getSlot4()
        );
    }
}
