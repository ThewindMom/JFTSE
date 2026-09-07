package com.jftse.emulator.server.core.packets.matchplay;

import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

public class S2CClubMatchWarfareInitializationPacket extends Packet {
    public S2CClubMatchWarfareInitializationPacket(int guildId, String guildName,
                                                    int warfareState, boolean guildMember) {
        super(PacketOperations.S2CClubMatchWarfareInitialization);
        write((byte) 0);
        write(guildId);
        write(guildName);
        write((byte) warfareState);
        write(guildMember);
    }
}
