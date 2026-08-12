package com.jftse.entities.database.repository.emblem;

import com.jftse.entities.database.model.emblem.PlayerEmblemEquipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerEmblemEquipmentRepository extends JpaRepository<PlayerEmblemEquipment, Long> {
    Optional<PlayerEmblemEquipment> findByPlayerId(Long playerId);
}
