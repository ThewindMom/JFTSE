package com.jftse.emulator.server.core.packets.lobby.room;

import com.jftse.emulator.server.core.client.EquippedItemParts;
import com.jftse.emulator.server.core.client.GuildView;
import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.entities.database.model.player.EquippedItemStats;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S2CRoomPlayerListInformationPacketTest {
    @Test
    void absentPetOmitsTheOptionalPetPayload() {
        RoomPlayer roomPlayer = roomPlayer();
        S2CRoomPlayerListInformationPacket absentPetPacket = new S2CRoomPlayerListInformationPacket(List.of(roomPlayer));

        roomPlayer.setPet(new PetView(0, 0, "", 0, 0, 0, 0, 0, 0, 0, 0));
        S2CRoomPlayerListInformationPacket emptyPetPacket = new S2CRoomPlayerListInformationPacket(List.of(roomPlayer));

        assertEquals(absentPetPacket.getDataLength() + 20, emptyPetPacket.getDataLength());

        byte[] absentPetData = absentPetPacket.getData();
        byte[] emptyPetData = emptyPetPacket.getData();
        int presenceFlagOffset = -1;
        for (int i = 0; i < absentPetData.length; i++) {
            if (absentPetData[i] != emptyPetData[i]) {
                assertEquals(-1, presenceFlagOffset, "only the pet-presence flag may differ before the optional payload");
                presenceFlagOffset = i;
                assertEquals(0, absentPetData[i]);
                assertEquals(1, emptyPetData[i]);
            }
        }
        assertTrue(presenceFlagOffset >= 0, "the pet-presence flag must be encoded");
    }

    @Test
    void playerJoinInformationIncludesTheOptionalPetBeforePositionData() {
        RoomPlayer roomPlayer = roomPlayer();
        S2CRoomPlayerInformationPacket absentPetPacket = new S2CRoomPlayerInformationPacket(
                roomPlayer, 1.0f, 2.0f, 3.0f, 4.0f, 5);

        roomPlayer.setPet(new PetView(0, 0, "", 0, 0, 0, 0, 0, 0, 0, 0));
        S2CRoomPlayerInformationPacket emptyPetPacket = new S2CRoomPlayerInformationPacket(
                roomPlayer, 1.0f, 2.0f, 3.0f, 4.0f, 5);

        assertEquals(absentPetPacket.getDataLength() + 20, emptyPetPacket.getDataLength());

        byte[] absentPetData = absentPetPacket.getData();
        byte[] emptyPetData = emptyPetPacket.getData();
        int positionDataLength = 21;
        assertArrayEquals(
                Arrays.copyOfRange(absentPetData, absentPetData.length - positionDataLength, absentPetData.length),
                Arrays.copyOfRange(emptyPetData, emptyPetData.length - positionDataLength, emptyPetData.length));

        int presenceFlagOffset = -1;
        for (int i = 0; i < absentPetData.length - positionDataLength; i++) {
            if (absentPetData[i] != emptyPetData[i]) {
                presenceFlagOffset = i;
                break;
            }
        }
        assertTrue(presenceFlagOffset >= 0, "the pet-presence flag must be encoded");
        assertEquals(0, absentPetData[presenceFlagOffset]);
        assertEquals(1, emptyPetData[presenceFlagOffset]);
    }

    private RoomPlayer roomPlayer() {
        return new RoomPlayer(null) {
            @Override
            public short getPosition() {
                return 0;
            }

            @Override
            public String getName() {
                return "Player";
            }

            @Override
            public int getLevel() {
                return 1;
            }

            @Override
            public int getPlayerType() {
                return 0;
            }

            @Override
            public GuildView getGuild() {
                return null;
            }

            @Override
            public String getCoupleName() {
                return "";
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
            public EquippedItemParts getEquippedItemPartsIDX() {
                return EquippedItemParts.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            }

            @Override
            public EquippedItemStats getEquippedItemStats() {
                return new EquippedItemStats();
            }
        };
    }
}
