package com.jftse.emulator.server.core.packets.lobby.room;

import com.jftse.emulator.server.core.life.room.Room;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClubHouseRoomListPacketTest {
    @Test
    void clubHouseEntryAddsGuildAccessAndFeeBeforeListStatusFields() {
        Room clubHouse = room((byte) 3);
        clubHouse.setCastleGuildId(11L);
        clubHouse.setCastleGuildName("CastleClub");
        clubHouse.setCastleAccessLimit((byte) 2);
        clubHouse.setCastleAdmissionFee(1000);

        byte[] castle = payload(new S2CRoomListAnswerPacket(List.of(clubHouse)));
        byte[] guildName = "CastleClub\0".getBytes(StandardCharsets.UTF_16LE);
        int extensionLength = guildName.length + 1 + Integer.BYTES;
        int castleExtensionOffset = castle.length - 3 - extensionLength;

        assertEquals(35, castleExtensionOffset);
        assertArrayEquals(guildName,
                Arrays.copyOfRange(castle, castleExtensionOffset, castleExtensionOffset + guildName.length));
        assertArrayEquals(new byte[]{2, (byte) 0xe8, 0x03, 0, 0},
                Arrays.copyOfRange(castle, castleExtensionOffset + guildName.length, castle.length - 3));
        assertArrayEquals(new byte[]{0, 0, 0}, Arrays.copyOfRange(castle, castle.length - 3, castle.length));
    }

    @Test
    void nonClubHouseEntryDoesNotAddCastleMetadata() {
        Room room = room((byte) 2);
        room.setCastleGuildName("MustNotLeak");
        room.setCastleAccessLimit((byte) 3);
        room.setCastleAdmissionFee(999);

        byte[] withMetadata = payload(new S2CRoomListAnswerPacket(List.of(room)));

        room.setCastleGuildName(null);
        room.setCastleAccessLimit((byte) 0);
        room.setCastleAdmissionFee(0);
        byte[] withoutMetadata = payload(new S2CRoomListAnswerPacket(List.of(room)));

        assertArrayEquals(withoutMetadata, withMetadata);
    }

    @Test
    void unmanagedModeThreeSocialRoomIsRejectedInsteadOfMisaligningTheList() {
        Room invalid = room((byte) 3);

        assertThrows(IllegalStateException.class,
                () -> new S2CRoomListAnswerPacket(List.of(invalid)));
    }

    @Test
    void mixedRoomEntriesRemainAligned() {
        Room normal = room((byte) 0);
        Room clubHouse = room((byte) 3);
        clubHouse.setCastleGuildId(11L);
        clubHouse.setCastleGuildName("CastleClub");
        clubHouse.setCastleAccessLimit((byte) 1);
        clubHouse.setCastleAdmissionFee(250);

        byte[] normalPayload = payload(new S2CRoomListAnswerPacket(List.of(normal)));
        byte[] castlePayload = payload(new S2CRoomListAnswerPacket(List.of(clubHouse)));
        byte[] mixedPayload = payload(new S2CRoomListAnswerPacket(List.of(normal, clubHouse, normal)));
        byte[] expected = new byte[2 + (normalPayload.length - 2) * 2 + castlePayload.length - 2];
        expected[0] = 3;
        int offset = 2;
        System.arraycopy(normalPayload, 2, expected, offset, normalPayload.length - 2);
        offset += normalPayload.length - 2;
        System.arraycopy(castlePayload, 2, expected, offset, castlePayload.length - 2);
        offset += castlePayload.length - 2;
        System.arraycopy(normalPayload, 2, expected, offset, normalPayload.length - 2);

        assertArrayEquals(expected, mixedPayload);
    }

    private Room room(byte mode) {
        Room room = new Room();
        room.setRoomId((short) 7);
        room.setRoomName("Room");
        room.setRoomType((byte) 1);
        room.setMode(mode);
        room.setRule((byte) 0);
        room.setBettingAmount(0);
        room.setBall(0);
        room.setPlayers((byte) 100);
        room.setPrivate(false);
        room.setLevel((byte) 0);
        room.setLevelRange((byte) 0);
        room.setAllowBattlemon((byte) 0);
        room.setMap((byte) 0);
        room.setSkillFree(false);
        room.setQuickSlot(true);
        return room;
    }

    private byte[] payload(S2CRoomListAnswerPacket packet) {
        return Arrays.copyOf(packet.getData(), packet.getDataLength());
    }
}
