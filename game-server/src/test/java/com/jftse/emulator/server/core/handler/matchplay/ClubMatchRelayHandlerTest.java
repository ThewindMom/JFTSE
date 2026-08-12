package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.client.GuildView;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.ClubMatchRules;
import com.jftse.emulator.server.core.life.room.ClubMatchState;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.shared.packets.matchplay.CMSGConnectedToRelay;
import com.jftse.server.core.shared.packets.matchplay.CMSGRelayServerProblem;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClubMatchRelayHandlerTest {
    private static final GuildView RED_GUILD = guild(10, "Red Club");
    private static final GuildView BLUE_GUILD = guild(20, "Blue Club");

    @Test
    void relayFailureAfterLastConnectionStillPreventsLaunchCommit() {
        Room room = new Room();
        RoomPlayer first = roomPlayer(1, 0, RED_GUILD);
        RoomPlayer last = roomPlayer(2, 1, BLUE_GUILD);
        first.getConnectedToRelay().set(true);
        room.getRoomPlayerList().add(first);
        room.getRoomPlayerList().add(last);
        room.setStatus(RoomStatus.StartingGame);
        FTConnection connection = connection(room, last);

        new ConnectedToRelayHandler().handle(connection, mock(CMSGConnectedToRelay.class));
        assertEquals(RoomStatus.RelayConnectionSuccess, room.getStatus());

        new RelayConnectionProblemHandler().handle(connection, mock(CMSGRelayServerProblem.class));
        assertEquals(RoomStatus.RelayConnectionFailed, room.getStatus());
    }

    @Test
    void clubSpectatorCannotSatisfyTheClaimedParticipantBarrier() {
        Room room = clubRoom();
        RoomPlayer red = roomPlayer(1, 0, RED_GUILD);
        RoomPlayer blue = roomPlayer(2, 1, BLUE_GUILD);
        RoomPlayer spectator = roomPlayer(3, 5, RED_GUILD);
        red.setReady(true);
        blue.setReady(true);
        room.getRoomPlayerList().add(red);
        room.getRoomPlayerList().add(blue);
        room.getRoomPlayerList().add(spectator);
        List<ClubMatchState.Participant> participants = List.of(
                participant(red), participant(blue));
        Instant startedAt = Instant.parse("2026-08-11T10:00:00Z");
        room.getClubMatchState().startCountdown(
                startedAt, Duration.ofSeconds(5), 2, participants);
        room.getClubMatchState().tryStart(2, startedAt.plusSeconds(5), participants);
        room.setStatus(RoomStatus.StartingGame);

        new ConnectedToRelayHandler().handle(
                connection(room, spectator), mock(CMSGConnectedToRelay.class));

        assertFalse(spectator.getConnectedToRelay().get());
        assertEquals(RoomStatus.StartingGame, room.getStatus());
    }

    @Test
    void staleRelayProblemAfterGameInitializationIsIgnored() {
        Room room = new Room();
        RoomPlayer player = roomPlayer(1, 0, RED_GUILD);
        room.getRoomPlayerList().add(player);
        room.setStatus(RoomStatus.InitializingGame);
        FTConnection connection = connection(room, player);

        new RelayConnectionProblemHandler().handle(
                connection, mock(CMSGRelayServerProblem.class));

        assertEquals(RoomStatus.InitializingGame, room.getStatus());
    }

    private static FTConnection connection(Room room, RoomPlayer roomPlayer) {
        FTConnection connection = mock(FTConnection.class);
        FTClient client = mock(FTClient.class);
        when(connection.getClient()).thenReturn(client);
        when(client.getActiveRoom()).thenReturn(room);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        when(client.getActiveGameSession()).thenReturn(new GameSession());
        return connection;
    }

    private static Room clubRoom() {
        Room room = new Room();
        room.setGameServerType(ClubMatchRules.CLUB_SERVER_TYPE);
        room.setRoomType(ClubMatchRules.CLUB_ROOM_TYPE);
        room.setMode((byte) GameMode.BASIC);
        room.setPlayers((byte) 2);
        return room;
    }

    private static ClubMatchState.Participant participant(RoomPlayer player) {
        return new ClubMatchState.Participant(
                player.getPlayerId(), player.getPosition(), player.getGuild().id());
    }

    private static RoomPlayer roomPlayer(long playerId, int position, GuildView guild) {
        RoomPlayer player = new RoomPlayer(null) {
            @Override
            public long getPlayerId() {
                return playerId;
            }

            @Override
            public GuildView getGuild() {
                return guild;
            }
        };
        player.setPosition((short) position);
        return player;
    }

    private static GuildView guild(long id, String name) {
        return new GuildView(id, name, 0, 0, 0, 0, 0, 0);
    }
}
