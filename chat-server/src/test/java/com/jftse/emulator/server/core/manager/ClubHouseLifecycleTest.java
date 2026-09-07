package com.jftse.emulator.server.core.manager;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClubHouseLifecycleTest {
    @Test
    void lastOccupantLeavingDoesNotDeletePermanentClubHouse() {
        long playerId = 22L;
        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(playerId);

        RoomPlayer roomPlayer = new RoomPlayer(player);
        roomPlayer.setPosition((short) 0);
        roomPlayer.setMaster(true);

        Room clubHouse = new Room();
        clubHouse.setRoomId((short) 7);
        clubHouse.setRoomType((byte) 1);
        clubHouse.setMode((byte) 3);
        clubHouse.setCastleGuildId(11L);
        clubHouse.getRoomPlayerList().add(roomPlayer);

        FTClient client = mock(FTClient.class);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getActiveRoom()).thenReturn(clubHouse);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);

        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);

        GameManager gameManager = new GameManager();
        ConcurrentLinkedDeque<Room> rooms = new ConcurrentLinkedDeque<>();
        rooms.add(clubHouse);
        gameManager.setRooms(rooms);

        gameManager.handleRoomPlayerChanges(connection, false);

        assertTrue(clubHouse.getRoomPlayerList().isEmpty());
        assertTrue(gameManager.getRooms().contains(clubHouse));
        verify(client).setActiveRoom(null);
    }
}
