package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.client.GuildView;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.ClubMatchRules;
import com.jftse.emulator.server.core.life.room.ClubMatchState;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.server.core.constants.GameMode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClubMatchCoordinatorTest {
    private static final Instant STARTED_AT = Instant.parse("2026-08-11T10:00:00Z");
    private static final Instant ENDS_AT = STARTED_AT.plusSeconds(5);
    private static final long DESIGNATED_PLAYER_ID = 2;
    private static final GuildView RED_GUILD = guild(10, "Red Club");
    private static final GuildView BLUE_GUILD = guild(20, "Blue Club");

    @Test
    void designatedClientCanLaunchOnlyOnce() {
        Fixture fixture = fixture();

        assertTrue(fixture.coordinator.startFromClient(fixture.room, DESIGNATED_PLAYER_ID));
        assertFalse(fixture.coordinator.startFromClient(fixture.room, DESIGNATED_PLAYER_ID));

        assertEquals(1, fixture.launches.get());
        assertEquals(RoomStatus.StartingGame, fixture.room.getStatus());
    }

    @Test
    void roomMasterDoesNotNeedReadyStateToLaunch() {
        Fixture fixture = fixture();
        RoomPlayer master = fixture.room.getRoomPlayerList().getFirst();
        master.setMaster(true);
        master.setReady(false);

        assertTrue(fixture.coordinator.startFromClient(fixture.room, DESIGNATED_PLAYER_ID));

        assertEquals(1, fixture.launches.get());
        assertEquals(RoomStatus.StartingGame, fixture.room.getStatus());
    }

    @Test
    void compositionChangeMovesClaimedLaunchIntoTerminalCleanupState() {
        Fixture fixture = fixture();
        assertTrue(fixture.coordinator.startFromClient(fixture.room, DESIGNATED_PLAYER_ID));

        assertTrue(fixture.coordinator.cancelForCompositionChange(fixture.room));

        assertEquals(RoomStatus.StartCancelled, fixture.room.getStatus());
        assertFalse(fixture.room.getClubMatchState().isStartTriggered());
    }

    @Test
    void compositionChangeCannotCancelCommittedGame() {
        Fixture fixture = fixture();
        assertTrue(fixture.coordinator.startFromClient(fixture.room, DESIGNATED_PLAYER_ID));
        long generation = fixture.room.getClubMatchState().getGeneration();
        assertTrue(fixture.room.getClubMatchState().markGameStarted(71234, ENDS_AT,
                Duration.ofMinutes(5), generation));

        assertFalse(fixture.coordinator.cancelForCompositionChange(fixture.room));

        assertTrue(fixture.room.getClubMatchState().ownsGameSession(71234));
        assertEquals(generation, fixture.room.getClubMatchState().getGeneration());
    }

    @Test
    void compositionChangeIsAllowedAfterTerminalCleanupWithoutClearingTerminalState() {
        Fixture fixture = fixture();
        assertTrue(fixture.coordinator.startFromClient(fixture.room, DESIGNATED_PLAYER_ID));
        long generation = fixture.room.getClubMatchState().getGeneration();
        assertTrue(fixture.room.getClubMatchState().markGameStarted(71234, ENDS_AT,
                Duration.ofMinutes(5), generation));
        assertTrue(fixture.room.getClubMatchState().tryRecordResult(71234));
        fixture.room.setStatus(RoomStatus.NotRunning);

        assertTrue(fixture.coordinator.cancelForCompositionChange(fixture.room));

        assertTrue(fixture.room.getClubMatchState().isTerminal());
        assertTrue(fixture.room.getClubMatchState().ownsGameSession(71234));
        assertEquals(generation, fixture.room.getClubMatchState().getGeneration());
    }

    @Test
    void countdownExpiryWithoutDesignatedClientReportDoesNotLaunch() {
        Fixture fixture = fixture();

        assertEquals(0, fixture.launches.get());
        assertEquals(RoomStatus.NotRunning, fixture.room.getStatus());
        assertTrue(fixture.room.getClubMatchState().isCountdownActive());
    }

    @Test
    void nonDesignatedAndDuplicateClientReportsAreRejected() {
        Fixture fixture = fixture();

        assertFalse(fixture.coordinator.startFromClient(fixture.room, 1));
        assertTrue(fixture.coordinator.startFromClient(fixture.room, DESIGNATED_PLAYER_ID));
        assertFalse(fixture.coordinator.startFromClient(fixture.room, DESIGNATED_PLAYER_ID));

        assertEquals(1, fixture.launches.get());
    }

    @Test
    void cancellationInvalidatesDesignatedClientReport() {
        Fixture fixture = fixture();
        fixture.room.getClubMatchState().cancelCountdown();

        assertFalse(fixture.coordinator.startFromClient(fixture.room, DESIGNATED_PLAYER_ID));

        assertEquals(0, fixture.launches.get());
        assertEquals(RoomStatus.NotRunning, fixture.room.getStatus());
    }

    @Test
    void designatedClientCannotLaunchChangedComposition() {
        Fixture fixture = fixture();
        fixture.room.getRoomPlayerList().getFirst().setReady(false);

        assertFalse(fixture.coordinator.startFromClient(fixture.room, DESIGNATED_PLAYER_ID));

        assertEquals(0, fixture.launches.get());
    }

    private static Fixture fixture() {
        Room room = new Room();
        room.setGameServerType(ClubMatchRules.CLUB_SERVER_TYPE);
        room.setRoomType(ClubMatchRules.CLUB_ROOM_TYPE);
        room.setMode((byte) GameMode.BASIC);
        room.setPlayers((byte) 2);
        room.getRoomPlayerList().add(player(1, RED_GUILD, 0));
        room.getRoomPlayerList().add(player(DESIGNATED_PLAYER_ID, BLUE_GUILD, 1));
        room.getPositions().set(0, RoomPositionState.InUse);
        room.getPositions().set(1, RoomPositionState.InUse);
        room.getClubMatchState().startCountdown(STARTED_AT, Duration.ofSeconds(5), DESIGNATED_PLAYER_ID,
                List.of(
                        new ClubMatchState.Participant(1, (short) 0, RED_GUILD.id()),
                        new ClubMatchState.Participant(DESIGNATED_PLAYER_ID, (short) 1, BLUE_GUILD.id())));

        AtomicInteger launches = new AtomicInteger();
        ClubMatchCoordinator coordinator = new ClubMatchCoordinator(
                Clock.fixed(ENDS_AT, ZoneOffset.UTC),
                ignored -> launches.incrementAndGet(),
                ignored -> true,
                ignored -> List.of());
        return new Fixture(room, coordinator, launches);
    }

    private static RoomPlayer player(long playerId, GuildView guild, int position) {
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
        player.setReady(true);
        return player;
    }

    private static GuildView guild(long id, String name) {
        return new GuildView(id, name, 0, 0, 0, 0, 0, 0);
    }

    private record Fixture(Room room, ClubMatchCoordinator coordinator,
                           AtomicInteger launches) {
    }
}
