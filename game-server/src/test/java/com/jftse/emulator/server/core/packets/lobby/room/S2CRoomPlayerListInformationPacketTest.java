package com.jftse.emulator.server.core.packets.lobby.room;

import com.jftse.emulator.server.core.client.EquippedItemParts;
import com.jftse.emulator.server.core.client.GuildView;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.entities.database.model.player.EquippedItemStats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class S2CRoomPlayerListInformationPacketTest {
    @Test
    void maxHealthOverrideChangesOnlyThePlayerHealthField() {
        RoomPlayer player = roomPlayer();
        byte[] regular = new S2CRoomPlayerListInformationPacket(List.of(player)).toBytes();
        byte[] overridden = new S2CRoomPlayerListInformationPacket(List.of(player), Map.of(player, 263)).toBytes();

        assertEquals(regular.length, overridden.length);
        int healthOffset = indexOf(regular, new byte[]{(byte) 251, 0, 0, 0});
        assertEquals(263, littleEndianInt(overridden, healthOffset));

        byte[] expected = regular.clone();
        expected[healthOffset] = 7;
        expected[healthOffset + 1] = 1;
        assertArrayEquals(expected, overridden);
    }

    private int indexOf(byte[] data, byte[] value) {
        outer:
        for (int i = 0; i <= data.length - value.length; i++) {
            for (int j = 0; j < value.length; j++) {
                if (data[i + j] != value[j]) {
                    continue outer;
                }
            }
            return i;
        }
        throw new AssertionError("health value not found in packet");
    }

    private int littleEndianInt(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset])
                | Byte.toUnsignedInt(data[offset + 1]) << 8
                | Byte.toUnsignedInt(data[offset + 2]) << 16
                | Byte.toUnsignedInt(data[offset + 3]) << 24;
    }

    private RoomPlayer roomPlayer() {
        return new RoomPlayer(null) {
            @Override
            public long getPlayerId() {
                return 101;
            }

            @Override
            public String getName() {
                return "player";
            }

            @Override
            public int getLevel() {
                return 10;
            }

            @Override
            public int getPlayerType() {
                return 0;
            }

            @Override
            public String getCoupleName() {
                return "partner";
            }

            @Override
            public GuildView getGuild() {
                return null;
            }

            @Override
            public int getStrength() {
                return 0;
            }

            @Override
            public int getStamina() {
                return 0;
            }

            @Override
            public int getDexterity() {
                return 0;
            }

            @Override
            public int getWillpower() {
                return 0;
            }

            @Override
            public EquippedItemStats getEquippedItemStats() {
                EquippedItemStats stats = new EquippedItemStats();
                stats.setAddHp(6);
                return stats;
            }

            @Override
            public EquippedItemParts getEquippedItemPartsIDX() {
                return EquippedItemParts.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            }
        };
    }
}
