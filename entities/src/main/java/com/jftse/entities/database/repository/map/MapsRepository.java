package com.jftse.entities.database.repository.map;

import com.jftse.entities.database.model.map.SMaps;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MapsRepository extends JpaRepository<SMaps, Long> {
    List<SMaps> findAllByNameLike(String name);
    List<SMaps> findAllByIsBossStage(Boolean isBossStage);
    Optional<SMaps> findByMap(Integer map);
    Optional<SMaps> findByMapAndIsBossStage(Integer map, Boolean isBossStage);

    @Query(value = """
            SELECT COUNT(*) FROM S_Maps m WHERE m.`map` = :map
              AND EXISTS (SELECT 1 FROM Map_2_Scenarios ms
                JOIN M_Scenarios s ON s.id = ms.scenario_id
                JOIN Guardian_2_Maps g ON g.scenario_id = s.id AND g.map_id = ms.map_id
                WHERE ms.map_id = m.id AND s.isDefault = 1 AND s.status_id = 1
                  AND s.gameMode = 'GUARDIAN' AND g.status_id = 1)
              AND (m.isBossStage = 0 OR EXISTS (SELECT 1 FROM Map_2_Scenarios ms
                JOIN M_Scenarios s ON s.id = ms.scenario_id
                JOIN Guardian_2_Maps g ON g.scenario_id = s.id AND g.map_id = ms.map_id
                WHERE ms.map_id = m.id AND s.isDefault = 1 AND s.status_id = 1
                  AND s.gameMode IN ('BOSS_BATTLE', 'BOSS_BATTLE_V2') AND g.status_id = 1))
            """, nativeQuery = true)
    long countAvailableGuardianMaps(@Param("map") Integer map);
}
