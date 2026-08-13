package com.jftse.emulator.server.core.item;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonalBoardRoomLifecycleTest {
    @Test
    void joinSnapshotMapsOnlyThisRoomsActiveBoardsToCurrentPlayerPositions() {
        Room room = new Room();
        RoomPlayer first = roomPlayer(11L, (short) 7);
        RoomPlayer second = roomPlayer(12L, (short) 3);
        room.getRoomPlayerList().add(first);
        room.getRoomPlayerList().add(second);
        room.getPersonalBoardMessages().put(11L, "SEVEN");
        room.getPersonalBoardMessages().put(12L, "THREE");
        room.getPersonalBoardMessages().put(99L, "LEFT ROOM");

        Map<Short, String> snapshot = room.getPersonalBoardMessagesByPosition();

        assertEquals(Map.of((short) 3, "THREE", (short) 7, "SEVEN"), snapshot);
    }

    @Test
    void boardsAreIsolatedPerRoom() {
        Room first = new Room();
        Room second = new Room();
        first.getPersonalBoardMessages().put(11L, "FIRST");

        assertEquals("FIRST", first.getPersonalBoardMessages().get(11L));
        assertTrue(second.getPersonalBoardMessages().isEmpty());
    }

    @Test
    void leavingKickingOrDisconnectingThroughRoomCleanupRemovesTheBoard() {
        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(11L);
        RoomPlayer roomPlayer = new RoomPlayer(player);
        roomPlayer.setPosition((short) 4);
        roomPlayer.setMaster(false);

        Room room = new Room();
        room.setMode((byte) 2);
        room.getRoomPlayerList().add(roomPlayer);
        room.getPersonalBoardMessages().put(11L, "LEAVING");

        FTClient client = mock(FTClient.class);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getActiveRoom()).thenReturn(room);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);

        new GameManager().handleRoomPlayerChanges(connection, false);

        assertTrue(room.getPersonalBoardMessages().isEmpty());
        assertFalse(room.getRoomPlayerList().contains(roomPlayer));
        verify(client).setActiveRoom(null);
    }

    private RoomPlayer roomPlayer(long playerId, short position) {
        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(playerId);
        RoomPlayer result = new RoomPlayer(player);
        result.setPosition(position);
        return result;
    }
}
