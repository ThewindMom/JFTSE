package com.jftse.emulator.server.core.packets.matchplay;

import com.jftse.emulator.server.core.life.room.ClubMatchRules;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomCreateAnswerPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomInformationPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomListAnswerPacket;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.protocol.PacketOperations;
import com.jftse.server.core.util.Time;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClubMatchPacketTest {
    @Test
    void roomActivationPacketsExposeRecoveredWarfareRoomType() {
        Room room = new Room();
        room.setRoomId((short) 4);
        room.setRoomName("Club room");
        room.setRoomType(ClubMatchRules.CLUB_ROOM_TYPE);
        room.setMode((byte) GameMode.BASIC);
        room.setMap((byte) 2);

        S2CRoomCreateAnswerPacket create = new S2CRoomCreateAnswerPacket(
                (char) 0, room.getRoomType(), room.getMode(), room.getMap());
        ByteBuffer createPayload = payload(create.getData());
        assertEquals(0, createPayload.getChar());
        assertEquals(ClubMatchRules.CLUB_ROOM_TYPE, createPayload.get());
        assertEquals(GameMode.BASIC, createPayload.getInt());
        assertEquals(2, createPayload.get());

        S2CRoomInformationPacket information = new S2CRoomInformationPacket(room);
        ByteBuffer informationPayload = payload(information.getData());
        informationPayload.position(Short.BYTES + (room.getRoomName().length() + 1) * Character.BYTES);
        assertEquals(ClubMatchRules.CLUB_ROOM_TYPE, informationPayload.get());
        assertEquals(GameMode.BASIC, informationPayload.get());

        S2CRoomListAnswerPacket list = new S2CRoomListAnswerPacket(List.of(room));
        ByteBuffer listPayload = payload(list.getData());
        assertEquals(1, listPayload.getChar());
        assertEquals(room.getRoomId(), listPayload.getShort());
        listPayload.position(listPayload.position()
                + (room.getRoomName().length() + 1) * Character.BYTES);
        assertEquals(ClubMatchRules.CLUB_ROOM_TYPE, listPayload.get());
        assertEquals(GameMode.BASIC, listPayload.get());
    }

    @Test
    void readyCountdownMatchesRecoveredWireLayout() {
        Instant startedAt = Instant.parse("2026-08-11T10:00:00Z");
        Instant endsAt = startedAt.plusSeconds(5);
        Instant currentTime = startedAt.plusSeconds(1);

        S2CClubMatchReadyPacket packet = S2CClubMatchReadyPacket.countdown(
                false, startedAt, endsAt, currentTime);
        ByteBuffer payload = payload(packet.getData());

        assertEquals(PacketOperations.S2CClubMatchReady.getValue(), packet.getPacketId());
        assertEquals(27, packet.getDataLength());
        assertEquals(0, payload.getShort());
        assertEquals(0, payload.get());
        assertEquals(Time.toFileTimeUTC(startedAt.toEpochMilli()), payload.getLong());
        assertEquals(Time.toFileTimeUTC(endsAt.toEpochMilli()), payload.getLong());
        assertEquals(Time.toFileTimeUTC(currentTime.toEpochMilli()), payload.getLong());
    }

    @Test
    void readyCancellationContainsOnlyResultCode() {
        S2CClubMatchReadyPacket packet = S2CClubMatchReadyPacket.cancelled();

        assertEquals(2, packet.getDataLength());
        assertEquals(1, payload(packet.getData()).getShort());
    }

    @Test
    void gameTimeIsOneSignedInt32() {
        S2CClubMatchGameTimePacket packet = new S2CClubMatchGameTimePacket(-900);

        assertEquals(4, packet.getDataLength());
        assertEquals(-900, payload(packet.getData()).getInt());
    }

    @Test
    void maxPlayTimeIsOneInt32MinuteValue() {
        S2CClubMatchMaxPlayTimePacket packet = new S2CClubMatchMaxPlayTimePacket(15);

        assertEquals(PacketOperations.S2CClubMatchMaxPlayTime.getValue(), packet.getPacketId());
        assertEquals(4, packet.getDataLength());
        assertEquals(15, payload(packet.getData()).getInt());
    }

    @Test
    void resultContainsOneAbsoluteWinningSideByte() {
        S2CClubMatchResultPacket packet = new S2CClubMatchResultPacket((byte) 1);

        assertEquals(1, packet.getDataLength());
        assertEquals(1, payload(packet.getData()).get());
    }

    private static ByteBuffer payload(byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }
}
