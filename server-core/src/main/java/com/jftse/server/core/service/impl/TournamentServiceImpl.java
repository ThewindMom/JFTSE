package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.tournament.TournamentDefinition;
import com.jftse.entities.database.model.tournament.TournamentEnrollment;
import com.jftse.entities.database.model.tournament.TournamentMatch;
import com.jftse.entities.database.model.tournament.TournamentSettlement;
import com.jftse.entities.database.repository.tournament.TournamentDefinitionRepository;
import com.jftse.entities.database.repository.tournament.TournamentEnrollmentRepository;
import com.jftse.entities.database.repository.tournament.TournamentMatchRepository;
import com.jftse.entities.database.repository.tournament.TournamentSettlementRepository;
import com.jftse.server.core.service.InventoryService;
import com.jftse.server.core.tournament.TournamentMatchStatus;
import com.jftse.server.core.tournament.TournamentService;
import com.jftse.server.core.tournament.TournamentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {
    private static final String DEFAULT_TITLE = "JFTSE Open Cup";
    private static final int PAGE_SIZE = 10;
    private static final int QUALIFYING_PAGE_SIZE = 6;
    private static final byte ENROLLMENT_APPLIED = 1;
    private static final byte ENROLLMENT_ACTIVE = 2;
    private static final byte ENROLLMENT_ELIMINATED = 3;
    private static final byte ENROLLMENT_WINNER = 4;

    private final TournamentDefinitionRepository tournamentRepository;
    private final TournamentEnrollmentRepository enrollmentRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentSettlementRepository settlementRepository;
    private final InventoryService inventoryService;

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TournamentDefinition create(CreateTournament command) {
        return create(command, Instant.now());
    }

    private TournamentDefinition create(CreateTournament command, Instant now) {
        validate(command, now);
        if (tournamentRepository.findByTitle(command.title().trim()).isPresent()) {
            throw new IllegalArgumentException("A tournament with this title already exists");
        }

        TournamentDefinition tournament = new TournamentDefinition();
        tournament.setTitle(command.title().trim());
        tournament.setEntryType(command.entryType());
        tournament.setGameMode(command.gameMode());
        tournament.setStatus(statusAt(command, now));
        tournament.setCapacity(command.capacity());
        tournament.setFinalSize(command.finalSize());
        tournament.setRewardProductIndex(command.rewardProductIndex());
        tournament.setRewardQuantity(command.rewardQuantity());
        tournament.setApplicationStart(Date.from(command.applicationStart()));
        tournament.setApplicationEnd(Date.from(command.applicationEnd()));
        tournament.setQualifyingStart(Date.from(command.qualifyingStart()));
        tournament.setFinalStart(Date.from(command.finalStart()));
        tournament.setTournamentEnd(Date.from(command.tournamentEnd()));
        return tournamentRepository.save(tournament);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TournamentDefinition ensureDefaultTournament(Instant now) {
        List<TournamentDefinition> series = tournamentRepository
                .findAllByTitleStartingWithOrderByIdAsc(DEFAULT_TITLE).stream()
                .filter(tournament -> defaultSequence(tournament.getTitle()) > 0)
                .toList();
        Optional<TournamentDefinition> active = series.stream()
                .filter(tournament -> tournament.getStatus() != TournamentStatus.FINISHED
                        && tournament.getStatus() != TournamentStatus.CANCELED)
                .max(Comparator.comparingInt(tournament -> defaultSequence(tournament.getTitle())));
        if (active.isPresent()) {
            return active.get();
        }

        int sequence = series.stream()
                .mapToInt(tournament -> defaultSequence(tournament.getTitle()))
                .max()
                .orElse(0) + 1;
        String title = sequence == 1 ? DEFAULT_TITLE : DEFAULT_TITLE + " #" + sequence;
        CreateTournament command = new CreateTournament(
                title,
                (byte) 1,
                (byte) 0,
                64,
                16,
                287,
                1,
                now.minus(1, ChronoUnit.DAYS),
                now.plus(7, ChronoUnit.DAYS),
                now.plus(8, ChronoUnit.DAYS),
                now.plus(9, ChronoUnit.DAYS),
                now.plus(10, ChronoUnit.DAYS)
        );
        return create(command, now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentDefinition> findPage(int page) {
        if (page < 0) {
            return List.of();
        }
        return tournamentRepository.findAllByOrderByApplicationStartDesc(PageRequest.of(page, PAGE_SIZE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentDefinition> findArchives(int page) {
        if (page < 0) {
            return List.of();
        }
        return tournamentRepository.findAllByStatusOrderByTournamentEndDesc(
                TournamentStatus.FINISHED, PageRequest.of(page, PAGE_SIZE));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TournamentDefinition> find(int tournamentId) {
        return tournamentRepository.findById((long) tournamentId);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public byte apply(int tournamentId, long playerId, String playerName) {
        Optional<TournamentDefinition> tournamentOptional = tournamentRepository.findByIdForUpdate((long) tournamentId);
        if (tournamentOptional.isEmpty()) {
            return NOT_FOUND;
        }
        TournamentDefinition tournament = tournamentOptional.get();
        if (tournament.getStatus() != TournamentStatus.APPLY) {
            return NOT_OPEN;
        }
        if (enrollmentRepository.existsByTournamentIdAndPlayerId(tournament.getId(), playerId)) {
            return ALREADY_APPLIED;
        }
        long count = enrollmentRepository.countByTournamentId(tournament.getId());
        if (count >= tournament.getCapacity()) {
            return REGISTRATION_FULL;
        }

        TournamentEnrollment enrollment = new TournamentEnrollment();
        enrollment.setTournamentId(tournament.getId());
        enrollment.setPlayerId(playerId);
        enrollment.setPlayerName(playerName);
        int nextSeed = enrollmentRepository.findFirstByTournamentIdOrderBySeedDesc(tournament.getId())
                .map(last -> last.getSeed() + 1)
                .orElse(1);
        enrollment.setSeed(nextSeed);
        enrollment.setState(ENROLLMENT_APPLIED);
        enrollmentRepository.save(enrollment);
        return SUCCESS;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public byte cancel(int tournamentId, long playerId) {
        Optional<TournamentDefinition> tournamentOptional = tournamentRepository.findByIdForUpdate((long) tournamentId);
        if (tournamentOptional.isEmpty()) {
            return NOT_FOUND;
        }
        if (tournamentOptional.get().getStatus() != TournamentStatus.APPLY) {
            return NOT_OPEN;
        }
        Optional<TournamentEnrollment> enrollment = enrollmentRepository.findByTournamentIdAndPlayerId(
                (long) tournamentId, playerId);
        if (enrollment.isEmpty()) {
            return NOT_APPLIED;
        }
        enrollmentRepository.delete(enrollment.get());
        return SUCCESS;
    }

    @Override
    @Transactional(readOnly = true)
    public byte playerState(int tournamentId, long playerId) {
        Optional<TournamentEnrollment> enrollment = enrollmentRepository.findByTournamentIdAndPlayerId(
                (long) tournamentId, playerId);
        if (enrollment.isEmpty()) {
            return 0;
        }
        return enrollment.get().getState();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BracketEntry> bracketEntries(int tournamentId, byte bracketType, int page) {
        Optional<TournamentDefinition> tournament = tournamentRepository.findById((long) tournamentId);
        if (tournament.isEmpty() || page < 0) {
            return List.of();
        }
        List<TournamentEnrollment> enrollments = enrollmentRepository.findAllByTournamentIdOrderBySeed((long) tournamentId);
        Map<Long, String> names = enrollments.stream().collect(Collectors.toMap(
                TournamentEnrollment::getPlayerId,
                TournamentEnrollment::getPlayerName,
                (first, ignored) -> first));

        if (bracketType == STAGE_FINAL) {
            if (page != 0) {
                return List.of();
            }
            List<TournamentEnrollment> finalists = enrollments.stream()
                    .filter(enrollment -> enrollment.getQualifiedAt() != null || enrollment.getState() == ENROLLMENT_WINNER)
                    .sorted(Comparator.comparing(TournamentEnrollment::getSeed))
                    .limit(tournament.get().getFinalSize())
                    .toList();
            if (finalists.isEmpty()) {
                return List.of();
            }
            return finalists.stream()
                    .map(enrollment -> new BracketEntry(enrollment.getPlayerName(), "", ""))
                    .toList();
        }

        if (bracketType != STAGE_QUALIFYING) {
            return List.of();
        }
        List<TournamentMatch> matches = matchRepository
                .findAllByTournamentIdOrderByStageAscRoundNumberAscSlotNumberAsc((long) tournamentId)
                .stream()
                .filter(match -> match.getStage() == STAGE_QUALIFYING)
                .toList();
        int from = page * QUALIFYING_PAGE_SIZE;
        if (from >= matches.size()) {
            return List.of();
        }
        return matches.subList(from, Math.min(from + QUALIFYING_PAGE_SIZE, matches.size())).stream()
                .map(match -> new BracketEntry(
                        names.getOrDefault(match.getPlayerOneId(), ""),
                        names.getOrDefault(match.getPlayerTwoId(), ""),
                        names.getOrDefault(match.getWinnerPlayerId(), "")))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BracketMatch> bracketMatches(int tournamentId, byte bracketType, int page) {
        List<TournamentMatch> all = matchRepository
                .findAllByTournamentIdOrderByStageAscRoundNumberAscSlotNumberAsc((long) tournamentId)
                .stream()
                .filter(match -> match.getStage() == bracketType)
                .toList();
        if (bracketType == STAGE_QUALIFYING) {
            int from = page * QUALIFYING_PAGE_SIZE;
            if (from >= all.size()) {
                return List.of();
            }
            all = all.subList(from, Math.min(from + QUALIFYING_PAGE_SIZE, all.size()));
        } else if (bracketType != STAGE_FINAL || page != 0) {
            return List.of();
        }
        Map<Long, Integer> entrantIndexes = entrantIndexes(tournamentId, bracketType);
        return all.stream().map(match -> toBracketMatch(match, entrantIndexes)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssignedMatch> assignedMatch(int tournamentId, long playerId) {
        return matchRepository.findPlayerMatch(
                        (long) tournamentId,
                        playerId,
                        List.of(TournamentMatchStatus.READY, TournamentMatchStatus.ACTIVE))
                .map(this::toAssignedMatch);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssignedMatch> matchForRoom(short roomId) {
        return matchRepository.findByRoomId(roomId).map(this::toAssignedMatch);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean bindRoom(long matchId, short roomId, long playerId) {
        Optional<Long> tournamentId = matchRepository.findTournamentIdById(matchId);
        if (tournamentId.isEmpty()) {
            return false;
        }
        TournamentDefinition tournament = tournamentRepository
                .findByIdForUpdate(tournamentId.get())
                .orElse(null);
        Optional<TournamentMatch> matchOptional = matchRepository.findByIdForUpdate(matchId);
        if (matchOptional.isEmpty()) {
            return false;
        }
        TournamentMatch match = matchOptional.get();
        if (!isActiveStage(tournament, match)
                || match.getStatus() != TournamentMatchStatus.READY
                || !contains(match, playerId)) {
            return false;
        }
        if (match.getRoomId() != null) {
            return match.getRoomId() == roomId;
        }
        match.setRoomId(roomId);
        matchRepository.save(match);
        return true;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean activateMatch(long matchId, short roomId, int gameSessionId, List<Long> activePlayerIds) {
        Optional<Long> tournamentId = matchRepository.findTournamentIdById(matchId);
        if (tournamentId.isEmpty()) {
            return false;
        }
        TournamentDefinition tournament = tournamentRepository
                .findByIdForUpdate(tournamentId.get())
                .orElse(null);
        Optional<TournamentMatch> matchOptional = matchRepository.findByIdForUpdate(matchId);
        if (matchOptional.isEmpty()) {
            return false;
        }
        TournamentMatch match = matchOptional.get();
        Set<Long> expected = Set.of(match.getPlayerOneId(), match.getPlayerTwoId());
        Set<Long> actual = Set.copyOf(activePlayerIds);
        if (!isActiveStage(tournament, match)
                || match.getStatus() != TournamentMatchStatus.READY
                || match.getRoomId() == null
                || match.getRoomId() != roomId
                || !actual.equals(expected)) {
            return false;
        }
        match.setStatus(TournamentMatchStatus.ACTIVE);
        match.setGameSessionId(gameSessionId);
        match.setStartedAt(new Date());
        enrollmentRepository.findByTournamentIdAndPlayerId(match.getTournamentId(), match.getPlayerOneId())
                .ifPresent(enrollment -> enrollment.setState(ENROLLMENT_ACTIVE));
        enrollmentRepository.findByTournamentIdAndPlayerId(match.getTournamentId(), match.getPlayerTwoId())
                .ifPresent(enrollment -> enrollment.setState(ENROLLMENT_ACTIVE));
        matchRepository.save(match);
        return true;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean deactivateMatch(short roomId, int gameSessionId) {
        Optional<TournamentMatch> matchOptional = matchRepository.findByRoomIdForUpdate(roomId);
        if (matchOptional.isEmpty()) {
            return false;
        }
        TournamentMatch match = matchOptional.get();
        if (match.getStatus() != TournamentMatchStatus.ACTIVE
                || match.getGameSessionId() == null
                || match.getGameSessionId() != gameSessionId) {
            return false;
        }
        match.setStatus(TournamentMatchStatus.READY);
        match.setGameSessionId(null);
        match.setStartedAt(null);
        matchRepository.save(match);
        return true;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CompletionResult completeMatch(
            long matchId,
            short roomId,
            int gameSessionId,
            long reporterPlayerId,
            long winnerPlayerId
    ) {
        Optional<Long> tournamentId = matchRepository.findTournamentIdById(matchId);
        if (tournamentId.isEmpty()) {
            return CompletionResult.NOT_FOUND;
        }
        TournamentDefinition tournament = tournamentRepository
                .findByIdForUpdate(tournamentId.get())
                .orElse(null);
        Optional<TournamentMatch> matchOptional = matchRepository.findByIdForUpdate(matchId);
        if (matchOptional.isEmpty()) {
            return CompletionResult.NOT_FOUND;
        }
        TournamentMatch match = matchOptional.get();
        if (match.getStatus() == TournamentMatchStatus.COMPLETED) {
            return CompletionResult.ALREADY_COMPLETED;
        }
        if (!isActiveStage(tournament, match)
                || match.getStatus() != TournamentMatchStatus.ACTIVE
                || match.getRoomId() == null
                || match.getRoomId() != roomId
                || match.getGameSessionId() == null
                || match.getGameSessionId() != gameSessionId
                || !contains(match, reporterPlayerId)
                || !contains(match, winnerPlayerId)) {
            return CompletionResult.UNAUTHORIZED;
        }

        Date now = new Date();
        match.setWinnerPlayerId(winnerPlayerId);
        match.setStatus(TournamentMatchStatus.COMPLETED);
        match.setCompletedAt(now);
        match.setRoomId(null);
        match.setGameSessionId(null);
        matchRepository.save(match);

        long loserPlayerId = match.getPlayerOneId() == winnerPlayerId
                ? match.getPlayerTwoId()
                : match.getPlayerOneId();
        enrollmentRepository.findByTournamentIdAndPlayerId(match.getTournamentId(), loserPlayerId)
                .ifPresent(enrollment -> {
                    enrollment.setState(ENROLLMENT_ELIMINATED);
                    enrollment.setEliminatedAt(now);
                });

        if (match.getStage() == STAGE_QUALIFYING) {
            completeQualifyingMatch(match, winnerPlayerId, now, tournament);
        } else {
            completeFinalMatch(match, winnerPlayerId, now, tournament);
        }
        return CompletionResult.COMPLETED;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void releaseRoom(short roomId) {
        matchRepository.findByRoomIdForUpdate(roomId).ifPresent(match -> {
            if (match.getStatus() != TournamentMatchStatus.COMPLETED) {
                match.setStatus(TournamentMatchStatus.READY);
                match.setRoomId(null);
                match.setGameSessionId(null);
                match.setStartedAt(null);
                matchRepository.save(match);
            }
        });
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void recoverRuntimeBindings() {
        Collection<Byte> runtimeStatuses = List.of(TournamentMatchStatus.READY, TournamentMatchStatus.ACTIVE);
        List<TournamentMatch> matches = matchRepository.findAllByStatusIn(runtimeStatuses);
        matches.forEach(match -> {
            match.setRoomId(null);
            match.setGameSessionId(null);
            match.setStartedAt(null);
            if (match.getPlayerOneId() != null && match.getPlayerTwoId() != null) {
                match.setStatus(TournamentMatchStatus.READY);
            } else {
                match.setStatus(TournamentMatchStatus.WAITING);
            }
        });
        matchRepository.saveAll(matches);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void advanceDueTournaments(Instant now) {
        List<Long> activeIds = tournamentRepository.findIdsByStatusIn(List.of(
                TournamentStatus.PREPARE,
                TournamentStatus.APPLY,
                TournamentStatus.PREPARE_QUALIFYING,
                TournamentStatus.QUALIFYING,
                TournamentStatus.PREPARE_FINAL,
                TournamentStatus.FINAL));
        for (Long tournamentId : activeIds) {
            tournamentRepository.findByIdForUpdate(tournamentId).ifPresent(tournament -> advance(tournament, now));
        }
    }

    private void advance(TournamentDefinition tournament, Instant now) {
        if ((tournament.getStatus() == TournamentStatus.QUALIFYING
                || tournament.getStatus() == TournamentStatus.PREPARE_FINAL
                || tournament.getStatus() == TournamentStatus.FINAL)
                && !now.isBefore(tournament.getTournamentEnd().toInstant())) {
            abortTournament(tournament);
            tournamentRepository.save(tournament);
            return;
        }
        if (tournament.getStatus() == TournamentStatus.PREPARE
                && !now.isBefore(tournament.getApplicationStart().toInstant())) {
            tournament.setStatus(TournamentStatus.APPLY);
        }
        if (tournament.getStatus() == TournamentStatus.APPLY
                && !now.isBefore(tournament.getApplicationEnd().toInstant())) {
            tournament.setStatus(TournamentStatus.PREPARE_QUALIFYING);
        }
        if (tournament.getStatus() == TournamentStatus.PREPARE_QUALIFYING
                && !now.isBefore(tournament.getQualifyingStart().toInstant())) {
            if (enrollmentRepository.countByTournamentId(tournament.getId()) == tournament.getCapacity()) {
                seedQualifying(tournament);
                tournament.setStatus(TournamentStatus.QUALIFYING);
            } else {
                abortTournament(tournament);
            }
        }
        if (tournament.getStatus() == TournamentStatus.PREPARE_FINAL
                && !now.isBefore(tournament.getFinalStart().toInstant())) {
            seedFinal(tournament);
            tournament.setStatus(TournamentStatus.FINAL);
        }
        tournamentRepository.save(tournament);
    }

    private void abortTournament(TournamentDefinition tournament) {
        Date now = new Date();
        tournament.setStatus(TournamentStatus.CANCELED);
        List<TournamentMatch> matches = matchRepository
                .findAllByTournamentIdOrderByStageAscRoundNumberAscSlotNumberAsc(tournament.getId());
        matches.stream()
                .filter(match -> match.getStatus() != TournamentMatchStatus.COMPLETED)
                .forEach(match -> {
                    match.setStatus(TournamentMatchStatus.ABORTED);
                    match.setRoomId(null);
                    match.setGameSessionId(null);
                    match.setStartedAt(null);
                });
        matchRepository.saveAll(matches);
        List<TournamentEnrollment> enrollments = enrollmentRepository
                .findAllByTournamentIdOrderBySeed(tournament.getId());
        enrollments.stream()
                .filter(enrollment -> enrollment.getState() != ENROLLMENT_WINNER)
                .forEach(enrollment -> {
                    enrollment.setState(ENROLLMENT_ELIMINATED);
                    if (enrollment.getEliminatedAt() == null) {
                        enrollment.setEliminatedAt(now);
                    }
                });
        enrollmentRepository.saveAll(enrollments);
    }

    private void seedQualifying(TournamentDefinition tournament) {
        if (!matchRepository.findAllByTournamentIdOrderByStageAscRoundNumberAscSlotNumberAsc(tournament.getId()).isEmpty()) {
            return;
        }
        List<TournamentEnrollment> entrants = enrollmentRepository.findAllByTournamentIdOrderBySeed(tournament.getId());
        entrants.forEach(enrollment -> enrollment.setState(ENROLLMENT_ACTIVE));
        enrollmentRepository.saveAll(entrants);
        int firstRoundMatches = tournament.getCapacity() / 2;
        List<TournamentMatch> matches = new ArrayList<>(firstRoundMatches + tournament.getFinalSize());
        for (int slot = 0; slot < firstRoundMatches; slot++) {
            TournamentMatch match = newMatch(tournament.getId(), STAGE_QUALIFYING, 0, slot);
            match.setPlayerOneId(entrants.get(slot).getPlayerId());
            match.setPlayerTwoId(entrants.get(entrants.size() - 1 - slot).getPlayerId());
            match.setStatus(TournamentMatchStatus.READY);
            matches.add(match);
        }
        for (int slot = 0; slot < tournament.getFinalSize(); slot++) {
            matches.add(newMatch(tournament.getId(), STAGE_QUALIFYING, 1, slot));
        }
        matchRepository.saveAll(matches);
    }

    private void seedFinal(TournamentDefinition tournament) {
        boolean alreadySeeded = matchRepository
                .findAllByTournamentIdOrderByStageAscRoundNumberAscSlotNumberAsc(tournament.getId())
                .stream()
                .anyMatch(match -> match.getStage() == STAGE_FINAL);
        if (alreadySeeded) {
            return;
        }
        List<TournamentEnrollment> finalists = enrollmentRepository.findAllByTournamentIdOrderBySeed(tournament.getId())
                .stream()
                .filter(enrollment -> enrollment.getQualifiedAt() != null)
                .toList();
        if (finalists.size() != tournament.getFinalSize()) {
            throw new IllegalStateException("Tournament final requires exactly " + tournament.getFinalSize() + " qualifiers");
        }

        List<TournamentMatch> matches = new ArrayList<>();
        int roundSize = tournament.getFinalSize() / 2;
        for (int round = 0; roundSize >= 1; round++, roundSize /= 2) {
            for (int slot = 0; slot < roundSize; slot++) {
                TournamentMatch match = newMatch(tournament.getId(), STAGE_FINAL, round, slot);
                if (round == 0) {
                    match.setPlayerOneId(finalists.get(slot).getPlayerId());
                    match.setPlayerTwoId(finalists.get(finalists.size() - 1 - slot).getPlayerId());
                    match.setStatus(TournamentMatchStatus.READY);
                }
                matches.add(match);
            }
        }
        matchRepository.saveAll(matches);
    }

    private void completeQualifyingMatch(
            TournamentMatch match,
            long winnerPlayerId,
            Date now,
            TournamentDefinition tournament
    ) {
        if (match.getRoundNumber() == 0) {
            advanceWinner(match, winnerPlayerId);
            return;
        }
        TournamentEnrollment winner = enrollmentRepository
                .findByTournamentIdAndPlayerId(match.getTournamentId(), winnerPlayerId)
                .orElseThrow();
        winner.setState(ENROLLMENT_ACTIVE);
        winner.setQualifiedAt(now);
        enrollmentRepository.save(winner);

        List<TournamentMatch> qualifyingFinals = matchRepository
                .findAllByTournamentIdAndStageAndRoundNumberOrderBySlotNumber(
                        match.getTournamentId(), STAGE_QUALIFYING, 1);
        if (qualifyingFinals.size() == tournament.getFinalSize()
                && qualifyingFinals.stream().allMatch(item -> item.getStatus() == TournamentMatchStatus.COMPLETED)) {
            tournament.setStatus(TournamentStatus.PREPARE_FINAL);
            tournamentRepository.save(tournament);
        }
    }

    private void completeFinalMatch(
            TournamentMatch match,
            long winnerPlayerId,
            Date now,
            TournamentDefinition tournament
    ) {
        List<TournamentMatch> currentRound = matchRepository.findAllByTournamentIdAndStageAndRoundNumberOrderBySlotNumber(
                match.getTournamentId(), STAGE_FINAL, match.getRoundNumber());
        if (currentRound.size() > 1) {
            advanceWinner(match, winnerPlayerId);
            return;
        }

        TournamentEnrollment winner = enrollmentRepository
                .findByTournamentIdAndPlayerId(match.getTournamentId(), winnerPlayerId)
                .orElseThrow();
        winner.setState(ENROLLMENT_WINNER);
        enrollmentRepository.save(winner);

        settleWinner(tournament, winnerPlayerId);
        tournament.setStatus(TournamentStatus.FINISHED);
        tournament.setTournamentEnd(now);
        tournamentRepository.save(tournament);
    }

    private void advanceWinner(TournamentMatch source, long winnerPlayerId) {
        int targetRound = source.getRoundNumber() + 1;
        int targetSlot = source.getSlotNumber() / 2;
        TournamentMatch target = matchRepository
                .findBySlotForUpdate(
                        source.getTournamentId(), source.getStage(), targetRound, targetSlot)
                .orElseThrow();
        if (source.getSlotNumber() % 2 == 0) {
            target.setPlayerOneId(winnerPlayerId);
        } else {
            target.setPlayerTwoId(winnerPlayerId);
        }
        if (target.getPlayerOneId() != null && target.getPlayerTwoId() != null) {
            target.setStatus(TournamentMatchStatus.READY);
        }
        matchRepository.save(target);
    }

    private void settleWinner(TournamentDefinition tournament, long winnerPlayerId) {
        boolean settled = settlementRepository.existsByTournamentIdAndPlayerIdAndPlaceNumberAndProductIndex(
                tournament.getId(), winnerPlayerId, 1, tournament.getRewardProductIndex());
        if (settled) {
            return;
        }

        TournamentSettlement settlement = new TournamentSettlement();
        settlement.setTournamentId(tournament.getId());
        settlement.setPlayerId(winnerPlayerId);
        settlement.setPlaceNumber(1);
        settlement.setProductIndex(tournament.getRewardProductIndex());
        settlement.setQuantity(tournament.getRewardQuantity());
        settlementRepository.saveAndFlush(settlement);

        if (inventoryService.addItem(
                winnerPlayerId,
                tournament.getRewardProductIndex(),
                tournament.getRewardQuantity(),
                List.of()).isEmpty()) {
            throw new IllegalStateException("Tournament prize product could not be granted");
        }
    }

    private TournamentMatch newMatch(long tournamentId, byte stage, int round, int slot) {
        TournamentMatch match = new TournamentMatch();
        match.setTournamentId(tournamentId);
        match.setStage(stage);
        match.setRoundNumber(round);
        match.setSlotNumber(slot);
        match.setStatus(TournamentMatchStatus.WAITING);
        return match;
    }

    private Map<Long, Integer> entrantIndexes(int tournamentId, byte bracketType) {
        List<TournamentEnrollment> entrants = enrollmentRepository.findAllByTournamentIdOrderBySeed((long) tournamentId);
        if (bracketType == STAGE_FINAL) {
            entrants = entrants.stream()
                    .filter(enrollment -> enrollment.getQualifiedAt() != null
                            || enrollment.getState() == ENROLLMENT_WINNER)
                    .toList();
        }
        List<TournamentEnrollment> selected = entrants;
        return java.util.stream.IntStream.range(0, selected.size()).boxed().collect(Collectors.toMap(
                index -> selected.get(index).getPlayerId(),
                index -> index));
    }

    private BracketMatch toBracketMatch(TournamentMatch match, Map<Long, Integer> entrantIndexes) {
        if (match.getStatus() != TournamentMatchStatus.COMPLETED || match.getWinnerPlayerId() == null) {
            return new BracketMatch((byte) -1, (byte) 0);
        }
        Integer winnerIndex = entrantIndexes.get(match.getWinnerPlayerId());
        return winnerIndex == null
                ? new BracketMatch((byte) -1, (byte) 0)
                : new BracketMatch(winnerIndex.byteValue(), (byte) 0);
    }

    private AssignedMatch toAssignedMatch(TournamentMatch match) {
        return new AssignedMatch(
                match.getId(),
                Math.toIntExact(match.getTournamentId()),
                match.getStage(),
                match.getRoundNumber(),
                match.getSlotNumber(),
                match.getPlayerOneId(),
                match.getPlayerTwoId(),
                match.getRoomId(),
                match.getGameSessionId(),
                match.getStatus());
    }

    private boolean contains(TournamentMatch match, long playerId) {
        return (match.getPlayerOneId() != null && match.getPlayerOneId() == playerId)
                || (match.getPlayerTwoId() != null && match.getPlayerTwoId() == playerId);
    }

    private boolean isActiveStage(TournamentDefinition tournament, TournamentMatch match) {
        if (tournament == null) {
            return false;
        }
        return (match.getStage() == STAGE_QUALIFYING && tournament.getStatus() == TournamentStatus.QUALIFYING)
                || (match.getStage() == STAGE_FINAL && tournament.getStatus() == TournamentStatus.FINAL);
    }

    private int defaultSequence(String title) {
        if (DEFAULT_TITLE.equals(title)) {
            return 1;
        }
        String prefix = DEFAULT_TITLE + " #";
        if (title == null || !title.startsWith(prefix)) {
            return 0;
        }
        try {
            int sequence = Integer.parseInt(title.substring(prefix.length()));
            return sequence > 1 ? sequence : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void validate(CreateTournament command, Instant now) {
        if (command.title() == null || command.title().isBlank() || command.title().length() > 100) {
            throw new IllegalArgumentException("Tournament title must contain 1 to 100 characters");
        }
        if (command.capacity() != 64 || command.finalSize() != 16) {
            throw new IllegalArgumentException("The recovered tournament model requires 64 entrants and 16 finalists");
        }
        if (command.rewardProductIndex() <= 0 || command.rewardQuantity() <= 0) {
            throw new IllegalArgumentException("Tournament reward must be a positive product and quantity");
        }
        if (command.applicationStart() == null
                || command.applicationEnd() == null
                || command.qualifyingStart() == null
                || command.finalStart() == null
                || command.tournamentEnd() == null
                || !command.applicationStart().isBefore(command.applicationEnd())
                || !command.applicationEnd().isBefore(command.qualifyingStart())
                || !command.qualifyingStart().isBefore(command.finalStart())
                || !command.finalStart().isBefore(command.tournamentEnd())) {
            throw new IllegalArgumentException("Tournament timestamps must be present and strictly increasing");
        }
        if (!now.isBefore(command.qualifyingStart())) {
            throw new IllegalArgumentException("Tournament must be created before qualifying begins");
        }
    }

    private byte statusAt(CreateTournament command, Instant now) {
        if (now.isBefore(command.applicationStart())) {
            return TournamentStatus.PREPARE;
        }
        if (now.isBefore(command.applicationEnd())) {
            return TournamentStatus.APPLY;
        }
        if (now.isBefore(command.qualifyingStart())) {
            return TournamentStatus.PREPARE_QUALIFYING;
        }
        return TournamentStatus.SUSPENDED;
    }
}
