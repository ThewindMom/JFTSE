package com.jftse.entities.database.repository.tournament;

import com.jftse.entities.database.model.tournament.TournamentMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TournamentMatchRepository extends JpaRepository<TournamentMatch, Long> {
    @Query("SELECT m.tournamentId FROM TournamentMatch m WHERE m.id = :id")
    Optional<Long> findTournamentIdById(@Param("id") Long id);

    List<TournamentMatch> findAllByTournamentIdOrderByStageAscRoundNumberAscSlotNumberAsc(Long tournamentId);

    List<TournamentMatch> findAllByTournamentIdAndStageAndRoundNumberOrderBySlotNumber(
            Long tournamentId, Byte stage, Integer roundNumber);

    Optional<TournamentMatch> findByTournamentIdAndStageAndRoundNumberAndSlotNumber(
            Long tournamentId, Byte stage, Integer roundNumber, Integer slotNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM TournamentMatch m WHERE m.tournamentId = :tournamentId " +
            "AND m.stage = :stage AND m.roundNumber = :roundNumber AND m.slotNumber = :slotNumber")
    Optional<TournamentMatch> findBySlotForUpdate(
            @Param("tournamentId") Long tournamentId,
            @Param("stage") Byte stage,
            @Param("roundNumber") Integer roundNumber,
            @Param("slotNumber") Integer slotNumber);

    List<TournamentMatch> findAllByStatusIn(Collection<Byte> statuses);

    Optional<TournamentMatch> findByRoomId(Short roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM TournamentMatch m WHERE m.roomId = :roomId")
    Optional<TournamentMatch> findByRoomIdForUpdate(@Param("roomId") Short roomId);

    Optional<TournamentMatch> findByGameSessionId(Integer gameSessionId);

    @Query("SELECT m FROM TournamentMatch m WHERE m.tournamentId = :tournamentId " +
            "AND m.status IN :statuses AND (m.playerOneId = :playerId OR m.playerTwoId = :playerId)")
    Optional<TournamentMatch> findPlayerMatch(
            @Param("tournamentId") Long tournamentId,
            @Param("playerId") Long playerId,
            @Param("statuses") Collection<Byte> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM TournamentMatch m WHERE m.id = :id")
    Optional<TournamentMatch> findByIdForUpdate(@Param("id") Long id);
}
