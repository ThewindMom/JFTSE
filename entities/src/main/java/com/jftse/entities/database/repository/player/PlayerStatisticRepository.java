package com.jftse.entities.database.repository.player;

import com.jftse.entities.database.model.player.PlayerStatistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface PlayerStatisticRepository extends JpaRepository<PlayerStatistic, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM PlayerStatistic ps WHERE ps.id = :id")
    Optional<PlayerStatistic> findByIdForUpdate(@Param("id") Long id);

    List<PlayerStatistic> findAllByIdIn(List<Long> ids);
}
