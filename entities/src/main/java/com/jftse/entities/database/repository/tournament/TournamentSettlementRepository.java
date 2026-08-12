package com.jftse.entities.database.repository.tournament;

import com.jftse.entities.database.model.tournament.TournamentSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournamentSettlementRepository extends JpaRepository<TournamentSettlement, Long> {
    boolean existsByTournamentIdAndPlayerIdAndPlaceNumberAndProductIndex(
            Long tournamentId, Long playerId, Integer placeNumber, Integer productIndex);

    List<TournamentSettlement> findAllByTournamentId(Long tournamentId);
}
