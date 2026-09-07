package com.jftse.emulator.server.core.tournament;

import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.tournament.TournamentDefinition;
import com.jftse.entities.database.model.tournament.TournamentMatch;
import com.jftse.entities.database.repository.tournament.TournamentDefinitionRepository;
import com.jftse.entities.database.repository.tournament.TournamentEnrollmentRepository;
import com.jftse.entities.database.repository.tournament.TournamentMatchRepository;
import com.jftse.entities.database.repository.tournament.TournamentSettlementRepository;
import com.jftse.server.core.service.InventoryService;
import com.jftse.server.core.service.impl.TournamentServiceImpl;
import com.jftse.server.core.tournament.TournamentMatchStatus;
import com.jftse.server.core.tournament.TournamentService;
import com.jftse.server.core.tournament.TournamentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.datasource.url=${tournament.test.datasource.url:jdbc:h2:mem:tournaments;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${tournament.test.datasource.username:sa}",
        "spring.datasource.password=${tournament.test.datasource.password:}",
        "spring.datasource.driver-class-name=${tournament.test.datasource.driver:org.h2.Driver}",
        "spring.jpa.database-platform=${tournament.test.dialect:org.hibernate.dialect.H2Dialect}",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TournamentServicePersistenceTest.PersistenceConfiguration.class)
class TournamentServicePersistenceTest {
    private static final Instant APPLICATION_START = Instant.parse("2035-01-01T10:00:00Z");
    private static final Instant APPLICATION_END = APPLICATION_START.plus(1, ChronoUnit.DAYS);
    private static final Instant QUALIFYING_START = APPLICATION_END.plus(1, ChronoUnit.DAYS);
    private static final Instant FINAL_START = QUALIFYING_START.plus(1, ChronoUnit.DAYS);
    private static final Instant TOURNAMENT_END = FINAL_START.plus(1, ChronoUnit.DAYS);

    @Configuration
    @EnableJpaRepositories(basePackageClasses = TournamentDefinitionRepository.class)
    @EntityScan(basePackageClasses = TournamentDefinition.class)
    @Import(TournamentServiceImpl.class)
    static class PersistenceConfiguration {
    }

    @Autowired
    private TournamentService tournamentService;
    @Autowired
    private TournamentDefinitionRepository tournamentRepository;
    @Autowired
    private TournamentEnrollmentRepository enrollmentRepository;
    @Autowired
    private TournamentMatchRepository matchRepository;
    @Autowired
    private TournamentSettlementRepository settlementRepository;
    @MockBean
    private InventoryService inventoryService;
    @Autowired
    private DataSource dataSource;

    private final AtomicInteger nextRoomId = new AtomicInteger(1);
    private final AtomicInteger nextSessionId = new AtomicInteger(1000);

    @Test
    void creationRequiresTheRecovered64To16ShapeAndIncreasingTimestamps() {
        TournamentService.CreateTournament invalidShape = command(uniqueTitle(), 32, 16);
        assertEquals(
                "The recovered tournament model requires 64 entrants and 16 finalists",
                assertThrows(IllegalArgumentException.class, () -> tournamentService.create(invalidShape)).getMessage());

        TournamentService.CreateTournament invalidTimes = new TournamentService.CreateTournament(
                uniqueTitle(), (byte) 1, (byte) 0, 64, 16, 287, 1,
                APPLICATION_START, APPLICATION_START, QUALIFYING_START, FINAL_START, TOURNAMENT_END);
        assertEquals(
                "Tournament timestamps must be present and strictly increasing",
                assertThrows(IllegalArgumentException.class, () -> tournamentService.create(invalidTimes)).getMessage());

        TournamentService.CreateTournament elapsed = new TournamentService.CreateTournament(
                uniqueTitle(), (byte) 1, (byte) 0, 64, 16, 287, 1,
                Instant.now().minus(5, ChronoUnit.DAYS),
                Instant.now().minus(4, ChronoUnit.DAYS),
                Instant.now().minus(3, ChronoUnit.DAYS),
                Instant.now().minus(2, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS));
        assertEquals(
                "Tournament must be created before qualifying begins",
                assertThrows(IllegalArgumentException.class, () -> tournamentService.create(elapsed)).getMessage());
    }

    @Test
    void defaultTournamentSeriesCreatesOneSuccessorAfterFinishOrCancellation() {
        Instant firstSchedule = Instant.parse("2035-03-01T10:00:00Z");
        TournamentDefinition first = tournamentService.ensureDefaultTournament(firstSchedule);
        assertEquals("JFTSE Open Cup", first.getTitle());
        first.setStatus(TournamentStatus.CANCELED);
        tournamentRepository.saveAndFlush(first);

        Instant secondSchedule = firstSchedule.plus(1, ChronoUnit.DAYS);
        TournamentDefinition second = tournamentService.ensureDefaultTournament(secondSchedule);
        assertEquals("JFTSE Open Cup #2", second.getTitle());
        assertEquals(second.getId(), tournamentService.ensureDefaultTournament(secondSchedule).getId());
        second.setStatus(TournamentStatus.FINISHED);
        tournamentRepository.saveAndFlush(second);

        TournamentDefinition third = tournamentService.ensureDefaultTournament(
                secondSchedule.plus(1, ChronoUnit.DAYS));
        assertEquals("JFTSE Open Cup #3", third.getTitle());
        assertEquals(3, tournamentRepository.findAllByTitleStartingWithOrderByIdAsc("JFTSE Open Cup").size());
    }

    @Test
    void defaultTournamentSeriesIgnoresUnrelatedPrefixesAndUsesTheHighestSequence() {
        TournamentDefinition unrelated = tournamentService.create(command("JFTSE Open Cup Invitational", 64, 16));
        TournamentDefinition fourth = tournamentService.create(command("JFTSE Open Cup #4", 64, 16));
        unrelated.setStatus(TournamentStatus.PREPARE);
        fourth.setStatus(TournamentStatus.CANCELED);
        tournamentRepository.saveAllAndFlush(List.of(unrelated, fourth));

        TournamentDefinition successor = tournamentService.ensureDefaultTournament(APPLICATION_START);

        assertEquals("JFTSE Open Cup #5", successor.getTitle());
        assertEquals(TournamentStatus.APPLY, successor.getStatus());
    }

    @Test
    void cancelFreesCapacityAndReenrollmentReceivesANewDeterministicSeed() {
        TournamentDefinition tournament = createAndOpen();

        assertEquals(TournamentService.SUCCESS, tournamentService.apply(id(tournament), 10L, "P10"));
        assertEquals(TournamentService.SUCCESS, tournamentService.apply(id(tournament), 20L, "P20"));
        assertEquals(TournamentService.SUCCESS, tournamentService.cancel(id(tournament), 10L));
        assertEquals(TournamentService.SUCCESS, tournamentService.apply(id(tournament), 10L, "P10"));

        assertEquals(3, enrollmentRepository.findByTournamentIdAndPlayerId(tournament.getId(), 10L)
                .orElseThrow().getSeed());
        assertEquals(2, enrollmentRepository.findByTournamentIdAndPlayerId(tournament.getId(), 20L)
                .orElseThrow().getSeed());
    }

    @Test
    void enrollmentStopsAt64Players() {
        TournamentDefinition tournament = createAndOpen();
        for (long playerId = 1; playerId <= 64; playerId++) {
            assertEquals(TournamentService.SUCCESS,
                    tournamentService.apply(id(tournament), playerId, "P" + playerId));
        }
        assertEquals(TournamentService.REGISTRATION_FULL,
                tournamentService.apply(id(tournament), 65L, "P65"));
    }

    @Test
    void underfilledQualifyingCancellationDurablyEliminatesEveryEnrollment() {
        TournamentDefinition tournament = createAndOpen();
        assertEquals(TournamentService.SUCCESS, tournamentService.apply(id(tournament), 1L, "P1"));

        tournamentService.advanceDueTournaments(QUALIFYING_START);

        assertEquals(TournamentStatus.CANCELED,
                tournamentRepository.findById(tournament.getId()).orElseThrow().getStatus());
        assertTrue(enrollmentRepository.findAllByTournamentIdOrderBySeed(tournament.getId()).stream()
                .allMatch(enrollment -> enrollment.getState() == 3 && enrollment.getEliminatedAt() != null));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentEnrollmentNeverExceeds64Players() throws Exception {
        assumeTrue(isMariaDb(), "MariaDB row-lock semantics are covered by the explicit integration run");
        TournamentDefinition tournament = createAndOpen();
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch ready = new CountDownLatch(12);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Byte>> results = java.util.stream.LongStream.rangeClosed(1, 65)
                    .mapToObj(playerId -> executor.submit(() -> {
                        ready.countDown();
                        start.await(10, TimeUnit.SECONDS);
                        return tournamentService.apply(id(tournament), playerId, "P" + playerId);
                    }))
                    .toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            long accepted = 0;
            long full = 0;
            for (Future<Byte> result : results) {
                byte status = result.get(20, TimeUnit.SECONDS);
                accepted += status == TournamentService.SUCCESS ? 1 : 0;
                full += status == TournamentService.REGISTRATION_FULL ? 1 : 0;
            }
            assertEquals(64, accepted);
            assertEquals(1, full);
            assertEquals(64, enrollmentRepository.countByTournamentId(tournament.getId()));
        } finally {
            start.countDown();
            executor.shutdownNow();
            matchRepository.deleteAll();
            settlementRepository.deleteAll();
            enrollmentRepository.deleteAll();
            tournamentRepository.deleteAll();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSchedulerAdvanceSeedsQualifyingOnceOnMariaDb() throws Exception {
        assumeTrue(isMariaDb(), "MariaDB row-lock semantics are covered by the explicit integration run");
        TournamentDefinition tournament = createAndOpen();
        for (long playerId = 1; playerId <= 64; playerId++) {
            assertEquals(TournamentService.SUCCESS,
                    tournamentService.apply(id(tournament), playerId, "P" + playerId));
        }
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Void>> results = java.util.stream.IntStream.range(0, 2)
                    .mapToObj(ignored -> executor.submit((Callable<Void>) () -> {
                        ready.countDown();
                        start.await(10, TimeUnit.SECONDS);
                        tournamentService.advanceDueTournaments(QUALIFYING_START);
                        return null;
                    }))
                    .toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> result : results) {
                result.get(20, TimeUnit.SECONDS);
            }

            assertEquals(TournamentStatus.QUALIFYING,
                    tournamentRepository.findById(tournament.getId()).orElseThrow().getStatus());
            assertEquals(48, matchRepository
                    .findAllByTournamentIdOrderByStageAscRoundNumberAscSlotNumberAsc(tournament.getId())
                    .size());
        } finally {
            start.countDown();
            executor.shutdownNow();
            deleteAllTournamentData();
        }
    }

    @Test
    void qualifyingBuildsDeterministicRoundsAndSixEntryPages() {
        TournamentDefinition tournament = createQualifyingTournament();

        List<TournamentMatch> firstRound = matches(tournament, TournamentService.STAGE_QUALIFYING, 0);
        List<TournamentMatch> secondRound = matches(tournament, TournamentService.STAGE_QUALIFYING, 1);
        assertEquals(32, firstRound.size());
        assertEquals(16, secondRound.size());
        assertEquals(1L, firstRound.get(0).getPlayerOneId());
        assertEquals(64L, firstRound.get(0).getPlayerTwoId());
        assertEquals(6, tournamentService.bracketEntries(
                id(tournament), TournamentService.STAGE_QUALIFYING, 0).size());
        assertEquals(6, tournamentService.bracketEntries(
                id(tournament), TournamentService.STAGE_QUALIFYING, 7).size());
        assertTrue(tournamentService.bracketEntries(
                id(tournament), TournamentService.STAGE_QUALIFYING, 8).isEmpty());
    }

    @Test
    void roomAndSessionBindingRejectsDuplicatesAndRecoversAfterAbortOrRestart() {
        TournamentDefinition tournament = createQualifyingTournament();
        TournamentMatch match = matches(tournament, TournamentService.STAGE_QUALIFYING, 0).get(0);
        short roomId = 41;
        int sessionId = 501;

        assertTrue(tournamentService.bindRoom(match.getId(), roomId, match.getPlayerOneId()));
        assertFalse(tournamentService.bindRoom(match.getId(), (short) 42, match.getPlayerTwoId()));
        assertFalse(tournamentService.activateMatch(
                match.getId(), roomId, sessionId, List.of(match.getPlayerOneId(), 999L)));
        assertTrue(tournamentService.activateMatch(
                match.getId(), roomId, sessionId, List.of(match.getPlayerOneId(), match.getPlayerTwoId())));
        assertFalse(tournamentService.activateMatch(
                match.getId(), roomId, sessionId + 1, List.of(match.getPlayerOneId(), match.getPlayerTwoId())));

        assertTrue(tournamentService.deactivateMatch(roomId, sessionId));
        TournamentMatch deactivated = matchRepository.findById(match.getId()).orElseThrow();
        assertEquals(TournamentMatchStatus.READY, deactivated.getStatus());
        assertEquals(roomId, deactivated.getRoomId());
        assertEquals(null, deactivated.getGameSessionId());

        tournamentService.releaseRoom(roomId);
        assertEquals(TournamentMatchStatus.READY,
                matchRepository.findById(match.getId()).orElseThrow().getStatus());

        assertTrue(tournamentService.bindRoom(match.getId(), roomId, match.getPlayerTwoId()));
        assertTrue(tournamentService.activateMatch(
                match.getId(), roomId, sessionId, List.of(match.getPlayerOneId(), match.getPlayerTwoId())));
        tournamentService.recoverRuntimeBindings();
        TournamentMatch recovered = matchRepository.findById(match.getId()).orElseThrow();
        assertEquals(TournamentMatchStatus.READY, recovered.getStatus());
        assertEquals(null, recovered.getRoomId());
        assertEquals(null, recovered.getGameSessionId());
    }

    @Test
    void completionRequiresTheBoundSessionAndParticipantReporterThenAdvancesTheWinner() {
        TournamentDefinition tournament = createQualifyingTournament();
        TournamentMatch first = matches(tournament, TournamentService.STAGE_QUALIFYING, 0).get(0);
        TournamentMatch second = matches(tournament, TournamentService.STAGE_QUALIFYING, 0).get(1);

        BoundMatch firstBinding = activate(first);
        assertEquals(TournamentService.CompletionResult.UNAUTHORIZED, tournamentService.completeMatch(
                first.getId(), firstBinding.roomId(), firstBinding.sessionId() + 1,
                first.getPlayerOneId(), first.getPlayerOneId()));
        assertEquals(TournamentService.CompletionResult.UNAUTHORIZED, tournamentService.completeMatch(
                first.getId(), firstBinding.roomId(), firstBinding.sessionId(),
                999L, first.getPlayerOneId()));
        assertEquals(TournamentService.CompletionResult.COMPLETED, tournamentService.completeMatch(
                first.getId(), firstBinding.roomId(), firstBinding.sessionId(),
                first.getPlayerTwoId(), first.getPlayerOneId()));
        assertEquals(TournamentService.CompletionResult.ALREADY_COMPLETED, tournamentService.completeMatch(
                first.getId(), firstBinding.roomId(), firstBinding.sessionId(),
                first.getPlayerTwoId(), first.getPlayerOneId()));
        assertEquals(0, tournamentService.bracketMatches(
                id(tournament), TournamentService.STAGE_QUALIFYING, 0).get(0).result());

        complete(second, second.getPlayerTwoId());
        TournamentMatch next = matchRepository.findByTournamentIdAndStageAndRoundNumberAndSlotNumber(
                tournament.getId(), TournamentService.STAGE_QUALIFYING, 1, 0).orElseThrow();
        assertEquals(first.getPlayerOneId(), next.getPlayerOneId());
        assertEquals(second.getPlayerTwoId(), next.getPlayerTwoId());
        assertEquals(TournamentMatchStatus.READY, next.getStatus());
    }

    @Test
    void tournamentDeadlineAbortsBindingsAndRejectsLateMatchCompletion() {
        TournamentDefinition tournament = createQualifyingTournament();
        TournamentMatch match = matches(tournament, TournamentService.STAGE_QUALIFYING, 0).get(0);
        BoundMatch binding = activate(match);

        tournamentService.advanceDueTournaments(TOURNAMENT_END);

        TournamentMatch aborted = matchRepository.findById(match.getId()).orElseThrow();
        assertEquals(TournamentStatus.CANCELED,
                tournamentRepository.findById(tournament.getId()).orElseThrow().getStatus());
        assertEquals(TournamentMatchStatus.ABORTED, aborted.getStatus());
        assertEquals(null, aborted.getRoomId());
        assertEquals(null, aborted.getGameSessionId());
        assertTrue(enrollmentRepository.findAllByTournamentIdOrderBySeed(tournament.getId()).stream()
                .allMatch(enrollment -> enrollment.getState() == 3 && enrollment.getEliminatedAt() != null));
        assertEquals(TournamentService.CompletionResult.UNAUTHORIZED, tournamentService.completeMatch(
                match.getId(), binding.roomId(), binding.sessionId(),
                match.getPlayerOneId(), match.getPlayerOneId()));
        assertEquals(0, settlementRepository.count());
        verify(inventoryService, never()).addItem(anyLong(), anyInt(), anyInt(), anyList());
    }

    @Test
    void completedBracketSettlesPrizeOnceAndRemainsAvailableAsAnArchive() {
        when(inventoryService.addItem(anyLong(), anyInt(), anyInt(), anyList()))
                .thenReturn(List.of(new PlayerPocket()));
        TournamentDefinition tournament = createQualifyingTournament();

        completeRound(tournament, TournamentService.STAGE_QUALIFYING, 0);
        completeRound(tournament, TournamentService.STAGE_QUALIFYING, 1);
        assertEquals(TournamentStatus.PREPARE_FINAL,
                tournamentRepository.findById(tournament.getId()).orElseThrow().getStatus());

        tournamentService.advanceDueTournaments(FINAL_START);
        assertEquals(TournamentStatus.FINAL,
                tournamentRepository.findById(tournament.getId()).orElseThrow().getStatus());
        completeRound(tournament, TournamentService.STAGE_FINAL, 0);
        completeRound(tournament, TournamentService.STAGE_FINAL, 1);
        completeRound(tournament, TournamentService.STAGE_FINAL, 2);
        TournamentMatch finalMatch = matches(tournament, TournamentService.STAGE_FINAL, 3).get(0);
        BoundMatch finalBinding = activate(finalMatch);
        long winnerId = finalMatch.getPlayerOneId();

        assertEquals(TournamentService.CompletionResult.COMPLETED, tournamentService.completeMatch(
                finalMatch.getId(), finalBinding.roomId(), finalBinding.sessionId(), winnerId, winnerId));
        assertEquals(TournamentService.CompletionResult.ALREADY_COMPLETED, tournamentService.completeMatch(
                finalMatch.getId(), finalBinding.roomId(), finalBinding.sessionId(), winnerId, winnerId));

        TournamentDefinition finished = tournamentRepository.findById(tournament.getId()).orElseThrow();
        assertEquals(TournamentStatus.FINISHED, finished.getStatus());
        assertEquals(1, settlementRepository.count());
        verify(inventoryService, times(1)).addItem(winnerId, 287, 1, List.of());
        assertTrue(tournamentService.findArchives(0).stream()
                .anyMatch(archive -> archive.getId().equals(tournament.getId())));

        List<TournamentService.BracketMatch> finalBracket = tournamentService.bracketMatches(
                id(tournament), TournamentService.STAGE_FINAL, 0);
        assertEquals(15, finalBracket.size());
        assertEquals(0, finalBracket.get(14).result());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFinalCompletionSettlesExactlyOnceOnMariaDb() throws Exception {
        assumeTrue(isMariaDb(), "MariaDB transaction semantics are covered by the explicit integration run");
        when(inventoryService.addItem(anyLong(), anyInt(), anyInt(), anyList()))
                .thenReturn(List.of(new PlayerPocket()));
        TournamentDefinition tournament = createFinalTournament();
        TournamentMatch finalMatch = matches(tournament, TournamentService.STAGE_FINAL, 3).get(0);
        BoundMatch binding = activate(finalMatch);
        long winnerId = finalMatch.getPlayerOneId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<TournamentService.CompletionResult>> results = java.util.stream.IntStream.range(0, 2)
                    .mapToObj(ignored -> executor.submit(() -> {
                        ready.countDown();
                        start.await(10, TimeUnit.SECONDS);
                        return tournamentService.completeMatch(
                                finalMatch.getId(), binding.roomId(), binding.sessionId(), winnerId, winnerId);
                    }))
                    .toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            Set<TournamentService.CompletionResult> outcomes = Set.of(
                    results.get(0).get(20, TimeUnit.SECONDS),
                    results.get(1).get(20, TimeUnit.SECONDS));
            assertEquals(Set.of(
                    TournamentService.CompletionResult.COMPLETED,
                    TournamentService.CompletionResult.ALREADY_COMPLETED), outcomes);
            assertEquals(1, settlementRepository.count());
            assertEquals(TournamentStatus.FINISHED,
                    tournamentRepository.findById(tournament.getId()).orElseThrow().getStatus());
            verify(inventoryService, times(1)).addItem(winnerId, 287, 1, List.of());
        } finally {
            start.countDown();
            executor.shutdownNow();
            deleteAllTournamentData();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedPrizeGrantRollsBackFinalCompletionOnMariaDb() throws Exception {
        assumeTrue(isMariaDb(), "MariaDB transaction semantics are covered by the explicit integration run");
        when(inventoryService.addItem(anyLong(), anyInt(), anyInt(), anyList())).thenReturn(List.of());
        TournamentDefinition tournament = createFinalTournament();
        TournamentMatch finalMatch = matches(tournament, TournamentService.STAGE_FINAL, 3).get(0);
        BoundMatch binding = activate(finalMatch);
        long winnerId = finalMatch.getPlayerOneId();
        try {
            assertEquals("Tournament prize product could not be granted", assertThrows(
                    IllegalStateException.class,
                    () -> tournamentService.completeMatch(
                            finalMatch.getId(), binding.roomId(), binding.sessionId(), winnerId, winnerId)).getMessage());

            TournamentMatch persistedMatch = matchRepository.findById(finalMatch.getId()).orElseThrow();
            assertEquals(TournamentMatchStatus.ACTIVE, persistedMatch.getStatus());
            assertEquals(null, persistedMatch.getWinnerPlayerId());
            assertEquals(binding.roomId(), persistedMatch.getRoomId());
            assertEquals(binding.sessionId(), persistedMatch.getGameSessionId());
            assertEquals(TournamentStatus.FINAL,
                    tournamentRepository.findById(tournament.getId()).orElseThrow().getStatus());
            assertEquals(0, settlementRepository.count());
        } finally {
            deleteAllTournamentData();
        }
    }

    private TournamentDefinition createAndOpen() {
        TournamentDefinition tournament = tournamentService.create(command(uniqueTitle(), 64, 16));
        tournamentService.advanceDueTournaments(APPLICATION_START);
        assertEquals(TournamentStatus.APPLY,
                tournamentRepository.findById(tournament.getId()).orElseThrow().getStatus());
        return tournament;
    }

    private TournamentDefinition createQualifyingTournament() {
        TournamentDefinition tournament = createAndOpen();
        for (long playerId = 1; playerId <= 64; playerId++) {
            assertEquals(TournamentService.SUCCESS,
                    tournamentService.apply(id(tournament), playerId, "P" + playerId));
        }
        tournamentService.advanceDueTournaments(QUALIFYING_START);
        assertEquals(TournamentStatus.QUALIFYING,
                tournamentRepository.findById(tournament.getId()).orElseThrow().getStatus());
        return tournament;
    }

    private TournamentDefinition createFinalTournament() {
        TournamentDefinition tournament = createQualifyingTournament();
        completeRound(tournament, TournamentService.STAGE_QUALIFYING, 0);
        completeRound(tournament, TournamentService.STAGE_QUALIFYING, 1);
        tournamentService.advanceDueTournaments(FINAL_START);
        completeRound(tournament, TournamentService.STAGE_FINAL, 0);
        completeRound(tournament, TournamentService.STAGE_FINAL, 1);
        completeRound(tournament, TournamentService.STAGE_FINAL, 2);
        return tournament;
    }

    private void deleteAllTournamentData() {
        matchRepository.deleteAll();
        settlementRepository.deleteAll();
        enrollmentRepository.deleteAll();
        tournamentRepository.deleteAll();
    }

    private void completeRound(TournamentDefinition tournament, byte stage, int round) {
        for (TournamentMatch match : matches(tournament, stage, round)) {
            complete(match, match.getPlayerOneId());
        }
    }

    private void complete(TournamentMatch match, long winnerId) {
        BoundMatch binding = activate(match);
        assertEquals(TournamentService.CompletionResult.COMPLETED, tournamentService.completeMatch(
                match.getId(), binding.roomId(), binding.sessionId(), winnerId, winnerId));
    }

    private BoundMatch activate(TournamentMatch match) {
        short roomId = (short) nextRoomId.getAndIncrement();
        int sessionId = nextSessionId.getAndIncrement();
        assertNotNull(match.getPlayerOneId());
        assertNotNull(match.getPlayerTwoId());
        assertTrue(tournamentService.bindRoom(match.getId(), roomId, match.getPlayerOneId()));
        assertTrue(tournamentService.activateMatch(
                match.getId(), roomId, sessionId, List.of(match.getPlayerOneId(), match.getPlayerTwoId())));
        return new BoundMatch(roomId, sessionId);
    }

    private List<TournamentMatch> matches(TournamentDefinition tournament, byte stage, int round) {
        return matchRepository.findAllByTournamentIdAndStageAndRoundNumberOrderBySlotNumber(
                tournament.getId(), stage, round);
    }

    private TournamentService.CreateTournament command(String title, int capacity, int finalSize) {
        return new TournamentService.CreateTournament(
                title,
                (byte) 1,
                (byte) 0,
                capacity,
                finalSize,
                287,
                1,
                APPLICATION_START,
                APPLICATION_END,
                QUALIFYING_START,
                FINAL_START,
                TOURNAMENT_END);
    }

    private static int id(TournamentDefinition tournament) {
        return Math.toIntExact(tournament.getId());
    }

    private static String uniqueTitle() {
        return "Cup-" + UUID.randomUUID();
    }

    private boolean isMariaDb() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product.contains("MariaDB") || product.contains("MySQL");
        }
    }

    private record BoundMatch(short roomId, int sessionId) {
    }
}
