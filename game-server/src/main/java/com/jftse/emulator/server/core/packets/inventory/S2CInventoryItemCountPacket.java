package com.jftse.emulator.server.core.packets.inventory;

import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

public class S2CInventoryItemCountPacket extends Packet {
    private static final long MAX_WIRE_ITEM_ID = 0xFFFF_FFFFL;

    public S2CInventoryItemCountPacket(PlayerPocket playerPocket) {
        this(playerPocket.getId(), playerPocket.getItemCount());
    }

    public S2CInventoryItemCountPacket(long playerPocketId, int itemCount) {
        super(PacketOperations.S2CInventoryItemCount);

        if (playerPocketId < 0 || playerPocketId > MAX_WIRE_ITEM_ID) {
            throw new IllegalArgumentException("player pocket ID does not fit the native uint32 field");
        }
        this.write((int) playerPocketId);
        this.write(itemCount);
    }
}
