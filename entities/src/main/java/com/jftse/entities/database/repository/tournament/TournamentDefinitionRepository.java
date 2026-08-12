package com.jftse.entities.database.repository.tournament;

import com.jftse.entities.database.model.tournament.TournamentDefinition;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TournamentDefinitionRepository extends JpaRepository<TournamentDefinition, Long> {
    Optional<TournamentDefinition> findByTitle(String title);

    List<TournamentDefinition> findAllByTitleStartingWithOrderByIdAsc(String titlePrefix);

    List<TournamentDefinition> findAllByOrderByApplicationStartDesc(Pageable pageable);

    List<TournamentDefinition> findAllByStatusOrderByTournamentEndDesc(Byte status, Pageable pageable);

    List<TournamentDefinition> findAllByStatusIn(Collection<Byte> statuses);

    @Query("SELECT t.id FROM TournamentDefinition t WHERE t.status IN :statuses")
    List<Long> findIdsByStatusIn(@Param("statuses") Collection<Byte> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TournamentDefinition t WHERE t.id = :id")
    Optional<TournamentDefinition> findByIdForUpdate(@Param("id") Long id);
}
