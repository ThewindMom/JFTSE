package com.jftse.emulator.server.core.packets.player;

import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

public final class S2CPlayerLifetimeStatisticsPacket extends Packet {
    public S2CPlayerLifetimeStatisticsPacket(PlayerStatistic statistic) {
        super(PacketOperations.S2CPlayerLifetimeStatistics);

        write((char) 10);
        write((byte) 6, statistic.getGuardBreakShot());
        write((byte) 10, statistic.getSmash());
        write((byte) 12, statistic.getSlice());
        write((byte) 14, statistic.getChargeShot());
        write((byte) 16, statistic.getLob());
        write((byte) 18, statistic.getSkillShot());
        write((byte) 20, statistic.getServiceAce());
        write((byte) 22, statistic.getReturnAce());
        write((byte) 24, statistic.getFishesCaught());
        write((byte) 26, statistic.getFruitsCollected());
    }
}
