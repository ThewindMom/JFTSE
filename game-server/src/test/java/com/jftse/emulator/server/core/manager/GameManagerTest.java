package com.jftse.emulator.server.core.manager;

import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.server.core.constants.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameManagerTest {
    @Test
    void battlemonOwnerLeaveReopensPairForImmediateReplacement() {
        Room room = new Room();
        room.setRoomType((byte) RoomType.BATTLEMON);
        assertEquals(true, GameManager.tryClaimBattlemonOwnerPosition(room, 0));
        assertEquals(true, GameManager.tryClaimBattlemonOwnerPosition(room, 1));

        RoomPlayer owner = mock(RoomPlayer.class);
        when(owner.getPosition()).thenReturn((short) 1);
        when(owner.getPet()).thenReturn(new PetView(0, 0, "", 0, 0, 0, 0, 0, 0, 0, 0));

        List<Short> positionsToClear = GameManager.getRoomPositionsToClear(room, owner);
        assertEquals(List.of((short) 1, (short) 3), positionsToClear);
        GameManager.releaseRoomPositions(room, positionsToClear);

        assertEquals(List.of(
                        RoomPositionState.InUse,
                        RoomPositionState.Free,
                        RoomPositionState.InUse,
                        RoomPositionState.Free),
                room.getPositions().subList(0, 4));
        assertEquals(true, GameManager.tryClaimBattlemonOwnerPosition(room, 1));
        assertEquals(List.of(
                        RoomPositionState.InUse,
                        RoomPositionState.InUse,
                        RoomPositionState.InUse,
                        RoomPositionState.InUse),
                room.getPositions().subList(0, 4));
    }

    @Test
    void ordinaryPlayerLeaveClearsOnlyPlayerCard() {
        Room room = new Room();
        room.setRoomType((byte) RoomType.MATCH);
        room.setMode((byte) GameMode.BASIC);
        RoomPlayer player = mock(RoomPlayer.class);
        when(player.getPosition()).thenReturn((short) 1);

        assertEquals(List.of((short) 1), GameManager.getRoomPositionsToClear(room, player));
    }
}
