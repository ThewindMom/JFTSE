package com.jftse.emulator.server.core.tournament;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.server.core.tournament.TournamentMatchStatus;
import com.jftse.server.core.tournament.TournamentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TournamentRoomCoordinatorTest {
    private static final long MATCH_ID = 15L;
    private static final long PLAYER_ONE = 101L;
    private static final long PLAYER_TWO = 202L;
    private static final short ROOM_ID = 7;

    private TournamentService tournamentService;
    private TournamentRoomCoordinator coordinator;
    private Room room;
    private TournamentService.AssignedMatch match;

    @BeforeEach
    void setUp() {
        tournamentService = mock(TournamentService.class);
        coordinator = new TournamentRoomCoordinator(tournamentService);
        room = new Room();
        room.setRoomId(ROOM_ID);
        room.setTournamentMatchId(MATCH_ID);
        room.setTournamentSpectatorsAllowed(true);
        room.getPositions().set(0, RoomPositionState.Free);
        room.getPositions().set(1, RoomPositionState.Free);
        room.getPositions().set(2, RoomPositionState.Locked);
        room.getPositions().set(3, RoomPositionState.Locked);
        match = new TournamentService.AssignedMatch(
                MATCH_ID,
                1,
                TournamentService.STAGE_QUALIFYING,
                0,
                0,
                PLAYER_ONE,
                PLAYER_TWO,
                ROOM_ID,
                null,
                TournamentMatchStatus.READY);
        when(tournamentService.matchForRoom(ROOM_ID)).thenReturn(Optional.of(match));
    }

    @Test
    void onlyAssignedPlayersCanOccupyPlayerSlotsAndOnlyOneBecomesMaster() {
        assertEquals(0, coordinator.joinPosition(room, PLAYER_ONE));
        assertTrue(coordinator.shouldBecomeMaster(room, PLAYER_ONE));
        addPlayer(room, PLAYER_ONE, 0, true, false);

        assertEquals(1, coordinator.joinPosition(room, PLAYER_TWO));
        assertFalse(coordinator.shouldBecomeMaster(room, PLAYER_TWO));
        addPlayer(room, PLAYER_TWO, 1, false, true);

        assertEquals(2, room.getRoomPlayerList().stream()
                .filter(player -> player.getPosition() < 4)
                .count());
        assertEquals(1, room.getRoomPlayerList().stream().filter(RoomPlayer::isMaster).count());
    }

    @Test
    void thirdClientCannotEnterUntilBothPlayersExistThenUsesOrdinarySpectatorPosition() {
        addPlayer(room, PLAYER_ONE, 0, true, false);
        room.getPositions().set(0, RoomPositionState.InUse);
        assertEquals(-1, coordinator.joinPosition(room, 303L));

        addPlayer(room, PLAYER_TWO, 1, false, true);
        room.getPositions().set(1, RoomPositionState.InUse);
        assertEquals(5, coordinator.joinPosition(room, 303L));
        assertEquals(RoomPositionState.InUse, room.getPositions().get(5));
    }

    @Test
    void startRequiresExactlyTheAssignedPlayersMasterAndReadyOpponent() {
        RoomPlayer master = addPlayer(room, PLAYER_ONE, 0, true, false);
        RoomPlayer opponent = addPlayer(room, PLAYER_TWO, 1, false, true);
        room.getPositions().set(0, RoomPositionState.InUse);
        room.getPositions().set(1, RoomPositionState.InUse);

        assertTrue(coordinator.canStart(room, master));
        opponent.setReady(false);
        assertFalse(coordinator.canStart(room, master));
        opponent.setReady(true);
        addPlayer(room, 303L, 5, false, false);
        assertTrue(coordinator.canStart(room, master));
        addPlayer(room, 404L, 2, false, true);
        assertFalse(coordinator.canStart(room, master));
    }

    @Test
    void relayStartupTracksAssignedParticipantsButNotSpectatorChurn() {
        RoomPlayer master = addPlayer(room, PLAYER_ONE, 0, true, false);
        RoomPlayer opponent = addPlayer(room, PLAYER_TWO, 1, false, true);
        RoomPlayer spectator = addPlayer(room, 303L, 5, false, false);
        Set<Long> participantIds = Set.of(PLAYER_ONE, PLAYER_TWO);

        assertTrue(coordinator.canContinueStart(room, participantIds));
        room.getRoomPlayerList().remove(spectator);
        assertTrue(coordinator.canContinueStart(room, participantIds));
        room.getRoomPlayerList().remove(opponent);
        assertFalse(coordinator.canContinueStart(room, participantIds));
        room.getRoomPlayerList().add(opponent);
        opponent.setReady(false);
        assertFalse(coordinator.canContinueStart(room, participantIds));
        assertTrue(master.isMaster());
    }

    @Test
    void relayReadinessRequiresOnlyTheImmutableTournamentParticipants() {
        RoomPlayer one = addPlayer(room, PLAYER_ONE, 0, true, false);
        RoomPlayer two = addPlayer(room, PLAYER_TWO, 1, false, true);
        RoomPlayer spectator = addPlayer(room, 303L, 5, false, false);
        GameSession gameSession = new GameSession();
        gameSession.setTournamentParticipantPositions(Map.of(PLAYER_ONE, (short) 0, PLAYER_TWO, (short) 1));
        gameSession.getClients().add(client(PLAYER_ONE, one));
        gameSession.getClients().add(client(PLAYER_TWO, two));
        gameSession.getClients().add(client(303L, spectator));

        one.getConnectedToRelay().set(true);
        assertFalse(gameSession.areTournamentParticipantsConnectedToRelay());
        two.getConnectedToRelay().set(true);
        assertTrue(gameSession.areTournamentParticipantsConnectedToRelay());
    }

    @Test
    void completionForwardsTheReportingParticipantNotTheCalculatedWinner() {
        addPlayer(room, PLAYER_ONE, 0, true, false);
        addPlayer(room, PLAYER_TWO, 1, false, true);
        GameSession gameSession = new GameSession();
        gameSession.setTournamentMatchId(MATCH_ID);
        gameSession.setTournamentParticipantPositions(Map.of(PLAYER_ONE, (short) 0, PLAYER_TWO, (short) 1));
        when(tournamentService.completeMatch(MATCH_ID, ROOM_ID, 88, PLAYER_TWO, PLAYER_ONE))
                .thenReturn(TournamentService.CompletionResult.COMPLETED);

        assertEquals(TournamentService.CompletionResult.COMPLETED,
                coordinator.completeBasicMatch(room, 88, gameSession, PLAYER_TWO, true));
        verify(tournamentService).completeMatch(MATCH_ID, ROOM_ID, 88, PLAYER_TWO, PLAYER_ONE);
    }

    @Test
    void completionUsesTheImmutableSessionPositionsAndRejectsAMismatchedSession() {
        RoomPlayer one = addPlayer(room, PLAYER_ONE, 1, true, false);
        RoomPlayer two = addPlayer(room, PLAYER_TWO, 0, false, true);
        GameSession gameSession = new GameSession();
        gameSession.setTournamentMatchId(MATCH_ID);
        gameSession.setTournamentParticipantPositions(Map.of(PLAYER_ONE, (short) 0, PLAYER_TWO, (short) 1));
        when(tournamentService.completeMatch(MATCH_ID, ROOM_ID, 88, PLAYER_TWO, PLAYER_ONE))
                .thenReturn(TournamentService.CompletionResult.COMPLETED);

        assertEquals(TournamentService.CompletionResult.COMPLETED,
                coordinator.completeBasicMatch(room, 88, gameSession, PLAYER_TWO, true));
        one.setPosition((short) 0);
        two.setPosition((short) 1);
        gameSession.setTournamentMatchId(MATCH_ID + 1);
        assertEquals(TournamentService.CompletionResult.NOT_FOUND,
                coordinator.completeBasicMatch(room, 88, gameSession, PLAYER_TWO, true));
    }

    @Test
    void participantLeaveDeactivatesActiveSessionButSpectatorLeaveDoesNot() {
        addPlayer(room, PLAYER_ONE, 0, true, false);
        addPlayer(room, PLAYER_TWO, 1, false, true);
        assertFalse(coordinator.onPlayerLeaving(room, PLAYER_ONE, 99, false));
        verify(tournamentService).deactivateMatch(ROOM_ID, 99);
        verify(tournamentService, never()).releaseRoom(ROOM_ID);
        assertFalse(coordinator.onPlayerLeaving(room, 303L, 99, false));
    }

    @Test
    void rejectedCompletionCanResetTheActiveMatchWithoutDroppingItsRoomBinding() {
        when(tournamentService.deactivateMatch(ROOM_ID, 99)).thenReturn(true);

        assertTrue(coordinator.deactivate(room, 99));

        verify(tournamentService).deactivateMatch(ROOM_ID, 99);
        verify(tournamentService, never()).releaseRoom(ROOM_ID);
    }

    @Test
    void lastParticipantLeavingReleasesTheDurableRoomBinding() {
        addPlayer(room, PLAYER_ONE, 0, true, false);

        assertTrue(coordinator.onPlayerLeaving(room, PLAYER_ONE, null, false));
        verify(tournamentService).releaseRoom(ROOM_ID);
    }

    @Test
    void completionInProgressKeepsTheDurableBindingWhileClosingTheLastRuntimeParticipant() {
        addPlayer(room, PLAYER_ONE, 0, true, false);

        assertTrue(coordinator.onPlayerLeaving(room, PLAYER_ONE, 99, true));
        verify(tournamentService, never()).deactivateMatch(ROOM_ID, 99);
        verify(tournamentService, never()).releaseRoom(ROOM_ID);
    }

    private static RoomPlayer addPlayer(
            Room room,
            long playerId,
            int position,
            boolean master,
            boolean ready
    ) {
        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(playerId);
        RoomPlayer roomPlayer = new RoomPlayer(player);
        roomPlayer.setPosition((short) position);
        roomPlayer.setMaster(master);
        roomPlayer.setReady(ready);
        room.getRoomPlayerList().add(roomPlayer);
        return roomPlayer;
    }

    private static FTClient client(long playerId, RoomPlayer roomPlayer) {
        FTClient client = mock(FTClient.class);
        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(playerId);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        return client;
    }
}
