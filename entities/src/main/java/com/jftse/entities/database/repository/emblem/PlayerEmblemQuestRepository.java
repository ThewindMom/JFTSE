package com.jftse.entities.database.repository.emblem;

import com.jftse.entities.database.model.emblem.EmblemQuestDefinition;
import com.jftse.entities.database.model.emblem.PlayerEmblemQuest;
import com.jftse.entities.database.model.emblem.PlayerEmblemQuestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface PlayerEmblemQuestRepository extends JpaRepository<PlayerEmblemQuest, Long> {
    @Query("SELECT q FROM PlayerEmblemQuest q JOIN FETCH q.definition WHERE q.player.id = :playerId")
    List<PlayerEmblemQuest> findAllByPlayerId(@Param("playerId") Long playerId);

    @Query("SELECT q FROM PlayerEmblemQuest q JOIN FETCH q.definition WHERE q.player.id = :playerId AND q.definition = :definition")
    Optional<PlayerEmblemQuest> findByPlayerIdAndDefinition(
            @Param("playerId") Long playerId,
            @Param("definition") EmblemQuestDefinition definition
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM PlayerEmblemQuest q JOIN FETCH q.definition WHERE q.player.id = :playerId AND q.definition = :definition")
    Optional<PlayerEmblemQuest> findByPlayerIdAndDefinitionForUpdate(
            @Param("playerId") Long playerId,
            @Param("definition") EmblemQuestDefinition definition
    );

    @Query("SELECT COUNT(q) FROM PlayerEmblemQuest q WHERE q.player.id = :playerId AND q.status = :status " +
            "AND (q.definition.questIndex < 1000 OR q.definition.questIndex > 1999)")
    long countActiveManualQuests(@Param("playerId") Long playerId,
                                 @Param("status") PlayerEmblemQuestStatus status);
}
