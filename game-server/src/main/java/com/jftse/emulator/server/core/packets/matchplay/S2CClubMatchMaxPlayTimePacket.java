package com.jftse.emulator.server.core.packets.matchplay;

import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

public class S2CClubMatchMaxPlayTimePacket extends Packet {
    public S2CClubMatchMaxPlayTimePacket(int minutes) {
        super(PacketOperations.S2CClubMatchMaxPlayTime);
        write(minutes);
    }
}
