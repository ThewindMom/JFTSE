package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.tournament.TournamentRoomCoordinator;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreate;
import com.jftse.server.core.tournament.TournamentMatchStatus;
import com.jftse.server.core.tournament.TournamentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomCreateRequestPacketHandlerTest {
    @Test
    void failedRoomCreationReleasesTournamentBindingAndJoiningGuard() {
        long playerId = 101L;
        short roomId = 7;
        TournamentService.AssignedMatch match = new TournamentService.AssignedMatch(
                15L,
                1,
                TournamentService.STAGE_QUALIFYING,
                0,
                0,
                playerId,
                202L,
                null,
                null,
                TournamentMatchStatus.READY);

        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(playerId);
        when(player.getLevel()).thenReturn(1);
        FTClient client = mock(FTClient.class);
        AtomicBoolean joiningOrLeaving = new AtomicBoolean();
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getIsJoiningOrLeavingRoom()).thenReturn(joiningOrLeaving);
        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        when(client.getConnection()).thenReturn(connection);

        CMSGRoomCreate packet = mock(CMSGRoomCreate.class);
        when(packet.getRoomName()).thenReturn("T#1");
        GameManager gameManager = mock(GameManager.class);
        when(gameManager.getRoomId()).thenReturn(roomId);
        doThrow(new IllegalStateException("room creation failed"))
                .when(gameManager).internalHandleRoomCreate(any(), any());
        TournamentRoomCoordinator coordinator = mock(TournamentRoomCoordinator.class);
        when(coordinator.requestedMatch("T#1", playerId)).thenReturn(Optional.of(match));
        when(coordinator.isTournamentRoomRequest("T#1")).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<Room>getArgument(0).setTournamentMatchId(match.matchId());
            return null;
        }).when(coordinator).configureRoom(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        when(coordinator.bindRoom(any(), any(), org.mockito.ArgumentMatchers.eq(playerId))).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> new RoomCreateRequestPacketHandler().handle(
                connection, packet, gameManager, coordinator));

        ArgumentCaptor<Room> room = ArgumentCaptor.forClass(Room.class);
        verify(coordinator).release(room.capture());
        assertTrue(room.getValue().isTournamentRoom());
        assertFalse(joiningOrLeaving.get());
    }
}
