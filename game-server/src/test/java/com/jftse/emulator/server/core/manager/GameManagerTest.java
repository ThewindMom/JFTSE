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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void abortCleanupRemovesOrdinarySessionButPreservesReplacement(boolean replaced) {
        var sessions = mock(com.jftse.emulator.server.core.matchplay.GameSessionManager.class);
        Object previous = org.springframework.test.util.ReflectionTestUtils.getField(
                com.jftse.emulator.server.core.matchplay.GameSessionManager.class, "instance");
        org.springframework.test.util.ReflectionTestUtils.setField(
                com.jftse.emulator.server.core.matchplay.GameSessionManager.class, "instance", sessions);
        try {
            var session = new com.jftse.emulator.server.core.life.room.GameSession();
            var game = mock(com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame.class);
            java.util.concurrent.ScheduledFuture<?> future = mock(java.util.concurrent.ScheduledFuture.class);
            when(game.getScheduledFutures()).thenReturn(new java.util.concurrent.ConcurrentLinkedDeque<>(List.of(future)));
            session.setMatchplayGame(game);
            var event = mock(com.jftse.emulator.server.core.matchplay.event.RunnableEvent.class);
            session.getFireables().add(event);
            when(sessions.getGameSessionBySessionId(7)).thenReturn(session);
            when(sessions.getGameSessionBySessionId(8)).thenReturn(new com.jftse.emulator.server.core.life.room.GameSession());
            when(sessions.removeGameSession(7, session)).thenReturn(true);
            var room = new Room();
            room.setStatus(com.jftse.emulator.server.core.constants.RoomStatus.Running);
            var seat = new RoomPlayer(mock(com.jftse.emulator.server.core.client.FTPlayer.class));
            seat.setReady(true);
            room.getRoomPlayerList().add(seat);
            var client = new com.jftse.emulator.server.net.FTClient();
            client.setActiveRoom(room);
            client.setActiveGameSession(replaced ? 8 : 7);
            session.getClients().add(client);
            var manager = new GameManager();
            org.springframework.test.util.ReflectionTestUtils.setField(manager, "gameSessionManager", sessions);
            org.springframework.test.util.ReflectionTestUtils.setField(manager, "matchRallyStatsConsumer",
                    mock(com.jftse.emulator.server.core.rabbit.MatchRallyStatsConsumer.class));
            org.springframework.test.util.ReflectionTestUtils.setField(manager, "clients",
                    new java.util.concurrent.ConcurrentLinkedDeque<>(List.of(client)));

            manager.cleanupGameSession(7, session, room);

            assertEquals(replaced ? Integer.valueOf(8) : null, client.getGameSessionId());
            assertEquals(replaced, seat.isReady());
            assertEquals(replaced ? com.jftse.emulator.server.core.constants.RoomStatus.Running :
                    com.jftse.emulator.server.core.constants.RoomStatus.NotRunning, room.getStatus());
            org.mockito.Mockito.verify(sessions).removeGameSession(7, session);
            org.mockito.Mockito.verify(event).setCancelled(true);
            org.mockito.Mockito.verify(future).cancel(false);
            assertEquals(0, session.getFireables().size());
            assertEquals(0, game.getScheduledFutures().size());
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(
                    com.jftse.emulator.server.core.matchplay.GameSessionManager.class, "instance", previous);
        }
    }

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
