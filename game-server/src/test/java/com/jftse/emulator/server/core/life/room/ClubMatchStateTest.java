package com.jftse.emulator.server.core.life.room;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClubMatchStateTest {
    private static final long DESIGNATED_PLAYER_ID = 42;
    private static final List<ClubMatchState.Participant> PARTICIPANTS = List.of(
            new ClubMatchState.Participant(41, (short) 0, 10L),
            new ClubMatchState.Participant(DESIGNATED_PLAYER_ID, (short) 1, 20L));

    @Test
    void designatedClientCanTriggerStartOnlyWhenCountdownExpires() {
        ClubMatchState state = new ClubMatchState();
        Instant startedAt = Instant.parse("2026-08-11T10:00:00Z");
        state.startCountdown(startedAt, Duration.ofSeconds(5), DESIGNATED_PLAYER_ID, PARTICIPANTS);

        assertFalse(state.tryStart(DESIGNATED_PLAYER_ID, startedAt.plusSeconds(4), PARTICIPANTS));
        assertTrue(state.tryStart(DESIGNATED_PLAYER_ID, startedAt.plusSeconds(5), PARTICIPANTS));
        assertFalse(state.tryStart(DESIGNATED_PLAYER_ID, startedAt.plusSeconds(6), PARTICIPANTS));
    }

    @Test
    void nonDesignatedClientCannotTriggerStart() {
        ClubMatchState state = new ClubMatchState();
        Instant startedAt = Instant.parse("2026-08-11T10:00:00Z");
        state.startCountdown(startedAt, Duration.ofSeconds(5), DESIGNATED_PLAYER_ID, PARTICIPANTS);

        assertFalse(state.tryStart(99, startedAt.plusSeconds(5), PARTICIPANTS));
        assertTrue(state.tryStart(DESIGNATED_PLAYER_ID, startedAt.plusSeconds(5), PARTICIPANTS));
    }

    @Test
    void cancellingCountdownRejectsTheOldExpiry() {
        ClubMatchState state = new ClubMatchState();
        Instant firstStart = Instant.parse("2026-08-11T10:00:00Z");
        state.startCountdown(firstStart, Duration.ofSeconds(5), DESIGNATED_PLAYER_ID, PARTICIPANTS);

        assertTrue(state.cancelCountdown());
        assertFalse(state.cancelCountdown());
        assertFalse(state.tryStart(DESIGNATED_PLAYER_ID, firstStart.plusSeconds(5), PARTICIPANTS));

        Instant secondStart = firstStart.plusSeconds(10);
        state.startCountdown(secondStart, Duration.ofSeconds(5), DESIGNATED_PLAYER_ID, PARTICIPANTS);
        assertFalse(state.tryStart(DESIGNATED_PLAYER_ID, secondStart.plusSeconds(4), PARTICIPANTS));
        assertTrue(state.tryStart(DESIGNATED_PLAYER_ID, secondStart.plusSeconds(5), PARTICIPANTS));
    }

    @Test
    void changedParticipantOrPositionCannotUseOldCountdown() {
        ClubMatchState state = new ClubMatchState();
        Instant startedAt = Instant.parse("2026-08-11T10:00:00Z");
        state.startCountdown(startedAt, Duration.ofSeconds(5), DESIGNATED_PLAYER_ID, PARTICIPANTS);
        List<ClubMatchState.Participant> changed = List.of(
                PARTICIPANTS.getFirst(),
                new ClubMatchState.Participant(DESIGNATED_PLAYER_ID, (short) 3, 20L));

        assertFalse(state.tryStart(DESIGNATED_PLAYER_ID, startedAt.plusSeconds(5), changed));
    }

    @Test
    void matchResultCanBeRecordedOnlyOnce() {
        ClubMatchState state = startedState();
        assertTrue(state.tryStart(DESIGNATED_PLAYER_ID,
                Instant.parse("2026-08-11T10:00:05Z"), PARTICIPANTS));
        long generation = state.getGeneration();
        assertTrue(state.markGameStarted(12345, Instant.parse("2026-08-11T10:00:05Z"),
                Duration.ofMinutes(5), generation));

        assertFalse(state.tryRecordResult(54321));
        assertTrue(state.tryRecordResult(12345));
        assertFalse(state.tryRecordResult(12345));
    }

    @Test
    void timerExpiryReportsMustBeAuthorizedAndAreIdempotent() {
        ClubMatchState state = startedState();
        Instant startedAt = Instant.parse("2026-08-11T10:00:05Z");

        assertTrue(state.tryStart(DESIGNATED_PLAYER_ID,
                startedAt, PARTICIPANTS));
        long generation = state.getGeneration();
        assertFalse(state.markGameStarted(12345, startedAt, Duration.ofMinutes(5), generation - 1));
        assertTrue(state.markGameStarted(12345, startedAt, Duration.ofMinutes(5), generation));
        assertFalse(state.tryExpire(54321, startedAt.plusSeconds(300)));
        assertFalse(state.tryExpire(12345, startedAt.plusSeconds(299)));
        assertTrue(state.tryExpire(12345, startedAt.plusSeconds(300)));
        assertFalse(state.tryRecordResult(12345));
        assertTrue(state.tryRecordExpiredResult(12345));
        assertFalse(state.tryExpire(12345, startedAt.plusSeconds(301)));
    }

    @Test
    void pointAtDeadlineClaimsExpiryBeforeScoreCanSettle() {
        ClubMatchState state = startedState();
        Instant startedAt = Instant.parse("2026-08-11T10:00:05Z");
        assertTrue(state.tryStart(DESIGNATED_PLAYER_ID, startedAt, PARTICIPANTS));
        assertTrue(state.markGameStarted(12345, startedAt, Duration.ofMinutes(5), state.getGeneration()));

        assertEquals(ClubMatchState.PointAction.APPLY,
                state.beforePoint(12345, startedAt.plusSeconds(299)));
        assertEquals(ClubMatchState.PointAction.EXPIRE,
                state.beforePoint(12345, startedAt.plusSeconds(300)));
        assertEquals(ClubMatchState.PointAction.REJECT,
                state.beforePoint(12345, startedAt.plusSeconds(301)));
        assertFalse(state.tryRecordResult(12345));
        assertTrue(state.tryRecordExpiredResult(12345));
    }

    @Test
    void queuedContinuationRequiresTheSameLiveGenerationAndSession() {
        ClubMatchState state = startedState();
        Instant startedAt = Instant.parse("2026-08-11T10:00:05Z");
        assertTrue(state.tryStart(DESIGNATED_PLAYER_ID, startedAt, PARTICIPANTS));
        long generation = state.getGeneration();
        assertTrue(state.markGameStarted(12345, startedAt, Duration.ofMinutes(5), generation));

        assertTrue(state.canSendContinuation(12345, generation, startedAt.plusSeconds(1)));
        assertFalse(state.canSendContinuation(54321, generation, startedAt.plusSeconds(1)));
        assertFalse(state.canSendContinuation(12345, generation - 1, startedAt.plusSeconds(1)));
        assertFalse(state.canSendContinuation(12345, generation, startedAt.plusSeconds(300)));

        assertTrue(state.tryAbort(12345));
        assertFalse(state.canSendContinuation(12345, generation, startedAt.plusSeconds(2)));
    }

    @Test
    void abortMustOwnCommittedSessionAndIsTerminalOnlyOnce() {
        ClubMatchState state = startedState();
        Instant startedAt = Instant.parse("2026-08-11T10:00:05Z");
        assertTrue(state.tryStart(DESIGNATED_PLAYER_ID, startedAt, PARTICIPANTS));
        assertTrue(state.markGameStarted(12345, startedAt, Duration.ofMinutes(5), state.getGeneration()));

        assertFalse(state.tryAbort(54321));
        assertTrue(state.tryAbort(12345));
        assertFalse(state.tryAbort(12345));
        assertTrue(state.isTerminal());
    }

    @Test
    void abortCanAtomicallyOwnAnUncommittedClaimedLaunch() {
        ClubMatchState state = startedState();
        Instant startedAt = Instant.parse("2026-08-11T10:00:05Z");
        assertTrue(state.tryStart(DESIGNATED_PLAYER_ID, startedAt, PARTICIPANTS));

        assertTrue(state.tryAbort(12345));
        assertTrue(state.ownsGameSession(12345));
        assertTrue(state.isTerminal());
        assertFalse(state.markGameStarted(12345, startedAt, Duration.ofMinutes(5), state.getGeneration()));
    }

    @Test
    void cancellingClaimedStartReportsCancellation() {
        ClubMatchState state = startedState();
        assertTrue(state.tryStart(DESIGNATED_PLAYER_ID,
                Instant.parse("2026-08-11T10:00:05Z"), PARTICIPANTS));

        assertTrue(state.cancelCountdown());
        assertFalse(state.cancelCountdown());
    }

    private static ClubMatchState startedState() {
        ClubMatchState state = new ClubMatchState();
        Instant startedAt = Instant.parse("2026-08-11T10:00:00Z");
        state.startCountdown(startedAt, Duration.ofSeconds(5), DESIGNATED_PLAYER_ID, PARTICIPANTS);
        return state;
    }
}
