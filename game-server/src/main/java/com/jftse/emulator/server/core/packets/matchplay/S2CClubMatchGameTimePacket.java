package com.jftse.emulator.server.core.packets.matchplay;

import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

public class S2CClubMatchGameTimePacket extends Packet {
    public S2CClubMatchGameTimePacket(int seconds) {
        super(PacketOperations.S2CClubMatchGameTime);
        write(seconds);
    }
}
