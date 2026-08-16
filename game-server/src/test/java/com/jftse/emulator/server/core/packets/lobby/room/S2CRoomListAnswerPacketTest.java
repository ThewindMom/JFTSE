package com.jftse.emulator.server.core.packets.lobby.room;

import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.server.core.constants.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S2CRoomListAnswerPacketTest {
    @Test
    void guardianWithPetSlotsAdvertisesTwoHumanPlacesInLobby() {
        Room room = new Room();
        room.setRoomId((short) 1);
        room.setRoomName("Guardian");
        room.setMode((byte) GameMode.GUARDIAN);
        room.setAllowBattlemon((byte) 1);
        room.setPlayers((byte) 4);

        RoomPlayer host = mock(RoomPlayer.class);
        when(host.getPosition()).thenReturn((short) 0);
        room.getRoomPlayerList().add(host);

        assertEquals((byte) 2, readMaxPositions(room));
    }

    @Test
    void dedicatedBattlemonAdvertisesTwoHumanPlacesInLobby() {
        Room room = new Room();
        room.setRoomId((short) 2);
        room.setRoomName("Battlemon");
        room.setRoomType((byte) RoomType.BATTLEMON);
        room.setMode((byte) GameMode.BASIC);
        room.setAllowBattlemon((byte) 1);
        room.setPlayers((byte) 4);

        assertEquals((byte) 2, readMaxPositions(room));
    }

    @Test
    void ordinaryFourPlayerMatchStillAdvertisesFourHumanPlacesInLobby() {
        Room room = new Room();
        room.setRoomId((short) 3);
        room.setRoomName("Basic");
        room.setRoomType((byte) RoomType.MATCH);
        room.setMode((byte) GameMode.BASIC);
        room.setPlayers((byte) 4);

        assertEquals((byte) 4, readMaxPositions(room));
    }

    private static byte readMaxPositions(Room room) {
        S2CRoomListAnswerPacket packet = new S2CRoomListAnswerPacket(List.of(room));
        assertEquals(1, (int) packet.read(Character.class));
        packet.read(Short.class);
        packet.read(String.class);
        packet.read(Byte.class);
        packet.read(Byte.class);
        packet.read(Byte.class);
        packet.read(Byte.class);
        packet.read(Byte.class);
        packet.read(Integer.class);
        packet.read(Integer.class);
        return packet.read(Byte.class);
    }
}
