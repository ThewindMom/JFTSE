package com.jftse.emulator.server.core.packets.matchplay;

import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import com.jftse.server.core.util.Time;

import java.time.Instant;

public class S2CClubMatchReadyPacket extends Packet {
    private S2CClubMatchReadyPacket(char result) {
        super(PacketOperations.S2CClubMatchReady);
        write(result);
    }

    public static S2CClubMatchReadyPacket countdown(boolean autoStartAtExpiry, Instant startedAt,
                                                     Instant endsAt, Instant currentTime) {
        S2CClubMatchReadyPacket packet = new S2CClubMatchReadyPacket((char) 0);
        packet.write(autoStartAtExpiry);
        packet.write(Time.toFileTimeUTC(startedAt.toEpochMilli()));
        packet.write(Time.toFileTimeUTC(endsAt.toEpochMilli()));
        packet.write(Time.toFileTimeUTC(currentTime.toEpochMilli()));
        return packet;
    }

    public static S2CClubMatchReadyPacket cancelled() {
        return new S2CClubMatchReadyPacket((char) 1);
    }
}
