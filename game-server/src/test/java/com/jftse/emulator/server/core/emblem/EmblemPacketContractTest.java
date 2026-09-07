package com.jftse.emulator.server.core.emblem;

import com.jftse.emulator.server.core.packets.player.S2CPlayerLifetimeStatisticsPacket;
import com.jftse.entities.database.model.emblem.PlayerEmblemEquipment;
import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import com.jftse.server.core.service.EmblemCompletionResult;
import com.jftse.server.core.service.EmblemQuestState;
import com.jftse.server.core.service.EmblemQuestStatus;
import com.jftse.server.core.service.EmblemRewardItem;
import com.jftse.server.core.shared.packets.emblem.S2CEmblemCompletionPacket;
import com.jftse.server.core.shared.packets.emblem.S2CEmblemEquipmentPacket;
import com.jftse.server.core.shared.packets.emblem.S2CEmblemListPacket;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EmblemPacketContractTest {
    @Test
    void serializesTheNativeAuthoritativeEquipmentSnapshot() {
        PlayerEmblemEquipment equipment = new PlayerEmblemEquipment();
        equipment.setSlot1((short) 1000);
        equipment.setSlot2((short) 1001);
        equipment.setSlot3((short) 0);
        equipment.setSlot4((short) 1999);

        Packet packet = new S2CEmblemEquipmentPacket(equipment);

        assertEquals(PacketOperations.S2CEmblemEquipment.getValue(), packet.getPacketId());
        assertArrayEquals(new byte[]{
                (byte) 0xe8, 0x03,
                (byte) 0xe9, 0x03,
                0, 0,
                (byte) 0xcf, 0x07
        }, Arrays.copyOf(packet.getData(), packet.getDataLength()));
    }

    @Test
    void serializesTheNativeLifetimeStatisticAssignmentRecords() {
        PlayerStatistic statistic = new PlayerStatistic();
        statistic.setGuardBreakShot(1);
        statistic.setSmash(2);
        statistic.setSlice(3);
        statistic.setChargeShot(4);
        statistic.setLob(5);
        statistic.setSkillShot(6);
        statistic.setServiceAce(7);
        statistic.setReturnAce(8);
        statistic.setFishesCaught(9);
        statistic.setFruitsCollected(10);

        Packet packet = new S2CPlayerLifetimeStatisticsPacket(statistic);

        assertEquals(PacketOperations.S2CPlayerLifetimeStatistics.getValue(), packet.getPacketId());
        assertArrayEquals(new byte[]{
                10, 0,
                6, 1, 0, 0, 0,
                10, 2, 0, 0, 0,
                12, 3, 0, 0, 0,
                14, 4, 0, 0, 0,
                16, 5, 0, 0, 0,
                18, 6, 0, 0, 0,
                20, 7, 0, 0, 0,
                22, 8, 0, 0, 0,
                24, 9, 0, 0, 0,
                26, 10, 0, 0, 0
        }, Arrays.copyOf(packet.getData(), packet.getDataLength()));
    }

    @Test
    void serializesTheNativeSeventeenByteQuestRecord() {
        EmblemQuestState state = new EmblemQuestState(
                (short) 1000, true, (short) 2,
                true, (short) 100,
                false, (short) 7,
                true, (short) 8,
                false, (short) 9
        );

        Packet packet = S2CEmblemListPacket.success(List.of(state));

        assertArrayEquals(new byte[]{
                0, 0, 1, 0,
                (byte) 0xe8, 0x03, 1, 2, 0,
                1, 100, 0,
                0, 7, 0,
                1, 8, 0,
                0, 9, 0
        }, Arrays.copyOf(packet.getData(), packet.getDataLength()));
    }

    @Test
    void serializesFailureSentinelsWithoutATrailingCount() {
        Packet failure = S2CEmblemListPacket.sentinel((short) -1);
        Packet special = S2CEmblemListPacket.sentinel((short) -10);

        assertArrayEquals(new byte[]{(byte) 0xff, (byte) 0xff},
                Arrays.copyOf(failure.getData(), failure.getDataLength()));
        assertArrayEquals(new byte[]{(byte) 0xf6, (byte) 0xff},
                Arrays.copyOf(special.getData(), special.getDataLength()));
    }

    @Test
    void serializesCompletionAndTheStandardTwentyEightByteRewardRecord() {
        EmblemRewardItem reward = new EmblemRewardItem(0x01020304, (byte) 5, 0x05060708,
                (byte) 9, 0x0a0b0c0d, new Date(0), (byte) 10, (byte) 11, (byte) 12,
                (byte) 13, (byte) 14, (byte) 15);
        Packet packet = new S2CEmblemCompletionPacket(new EmblemCompletionResult(
                EmblemQuestStatus.SUCCESS, (byte) 7, 0x01020304, 0x05060708, List.of(reward)));

        assertArrayEquals(new byte[]{
                0, 7, 8, 7, 6, 5, 4, 3, 2, 1, 1, 0,
                4, 3, 2, 1, 5, 8, 7, 6, 5, 9, 13, 12, 11, 10,
                0, (byte) 0x80, 0x3e, (byte) 0xd5, (byte) 0xde, (byte) 0xb1, (byte) 0x9d, 1,
                10, 11, 12, 13, 14, 15
        }, Arrays.copyOf(packet.getData(), packet.getDataLength()));
    }

    @Test
    void serializesCompletionFailureWithoutSuccessPayload() {
        Packet packet = new S2CEmblemCompletionPacket(
                EmblemCompletionResult.failure(EmblemQuestStatus.INCOMPLETE));
        assertArrayEquals(new byte[]{8}, Arrays.copyOf(packet.getData(), packet.getDataLength()));
    }
}
