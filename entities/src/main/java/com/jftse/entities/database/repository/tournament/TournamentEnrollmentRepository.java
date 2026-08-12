package com.jftse.entities.database.repository.tournament;

import com.jftse.entities.database.model.tournament.TournamentEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TournamentEnrollmentRepository extends JpaRepository<TournamentEnrollment, Long> {
    long countByTournamentId(Long tournamentId);

    boolean existsByTournamentIdAndPlayerId(Long tournamentId, Long playerId);

    Optional<TournamentEnrollment> findByTournamentIdAndPlayerId(Long tournamentId, Long playerId);

    List<TournamentEnrollment> findAllByTournamentIdOrderBySeed(Long tournamentId);

    Optional<TournamentEnrollment> findFirstByTournamentIdOrderBySeedDesc(Long tournamentId);
}
