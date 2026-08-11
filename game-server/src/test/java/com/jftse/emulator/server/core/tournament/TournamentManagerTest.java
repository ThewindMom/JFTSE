package com.jftse.emulator.server.core.tournament;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TournamentManagerTest {
    private static final long PLAYER_ONE = 101L;
    private static final long PLAYER_TWO = 202L;

    @Test
    void applyAndCancelAreTrackedPerPlayer() {
        TournamentManager manager = new TournamentManager(
                Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC));
        int tournamentId = manager.getTournaments().get(0).tournamentId();

        assertEquals(TournamentManager.SUCCESS, manager.apply(tournamentId, PLAYER_ONE));
        assertTrue(manager.isApplied(tournamentId, PLAYER_ONE));
        assertFalse(manager.isApplied(tournamentId, PLAYER_TWO));
        assertEquals(TournamentManager.ALREADY_APPLIED, manager.apply(tournamentId, PLAYER_ONE));

        assertEquals(TournamentManager.SUCCESS, manager.cancel(tournamentId, PLAYER_ONE));
        assertFalse(manager.isApplied(tournamentId, PLAYER_ONE));
        assertEquals(TournamentManager.NOT_APPLIED, manager.cancel(tournamentId, PLAYER_ONE));
    }

    @Test
    void unknownTournamentIsRejectedWithoutCreatingState() {
        TournamentManager manager = new TournamentManager(
                Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC));

        assertEquals(TournamentManager.NOT_FOUND, manager.apply(9999, PLAYER_ONE));
        assertEquals(TournamentManager.NOT_FOUND, manager.cancel(9999, PLAYER_ONE));
        assertFalse(manager.isApplied(9999, PLAYER_ONE));
    }
}
