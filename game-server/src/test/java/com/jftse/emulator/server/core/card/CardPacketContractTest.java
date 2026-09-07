package com.jftse.emulator.server.core.card;

import com.jftse.emulator.server.core.client.EquippedCardSlots;
import com.jftse.emulator.server.core.client.EquippedSpecialSlots;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryWearCardAnswerPacket;
import com.jftse.emulator.server.core.packets.matchplay.S2CGameEndLevelUpPlayerStatsPacket;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplaySetExperienceGainInfoData;
import com.jftse.entities.database.model.player.EquippedItemStats;
import com.jftse.server.core.item.CardStats;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CardPacketContractTest {
    private static final CardStats CARD_STATS = new CardStats(
            500, 1, 2, 3, 4,
            List.of(5, 6, 7, 8, 9, 10, 11, 12),
            List.of(13, 14, 15, 16, 17, 18, 19, 20));

    @Test
    void wearAnswerIsFourLittleEndianPocketIds() {
        Packet packet = new S2CInventoryWearCardAnswerPacket(List.of(11, 12, 13, 14));

        assertEquals(PacketOperations.S2CInventoryWearCardAnswer.getValue(), packet.getPacketId());
        assertEquals(16, packet.getDataLength());
        assertArrayEquals(new byte[]{
                11, 0, 0, 0,
                12, 0, 0, 0,
                13, 0, 0, 0,
                14, 0, 0, 0
        }, Arrays.copyOf(packet.getData(), packet.getDataLength()));
    }

    @Test
    void levelUpStatsIncludeCardStatusAndElementFields() {
        FTPlayer player = mock(FTPlayer.class);
        when(player.getItemStats()).thenReturn(new EquippedItemStats());
        when(player.getCardStats()).thenReturn(CARD_STATS);

        Packet packet = new S2CGameEndLevelUpPlayerStatsPacket((short) 1, player);

        assertArrayEquals(fullCardStats(), tail(packet, 24));
    }

    @Test
    void experienceGainInfoIncludesCardStatusFieldsWithoutChangingItsContract() {
        RoomPlayer player = mock(RoomPlayer.class);
        when(player.getEquippedSpecialSlots()).thenReturn(new EquippedSpecialSlots(1, 0, 0, 0, 0));
        when(player.getEquippedCardSlots()).thenReturn(new EquippedCardSlots(2, 11, 12, 13, 14));
        when(player.getCardStats()).thenReturn(CARD_STATS);

        Packet packet = new S2CMatchplaySetExperienceGainInfoData((byte) 1, 10, null, (byte) 1, player);

        assertArrayEquals(new byte[]{-12, 1, 0, 0, 1, 2, 3, 4}, tail(packet, 8));
    }

    private static byte[] fullCardStats() {
        return new byte[]{
                -12, 1, 0, 0, 1, 2, 3, 4,
                5, 6, 7, 8, 9, 10, 11, 12,
                13, 14, 15, 16, 17, 18, 19, 20
        };
    }

    private static byte[] tail(Packet packet, int length) {
        return Arrays.copyOfRange(packet.getData(), packet.getDataLength() - length, packet.getDataLength());
    }
}
