package com.jftse.entities.database.repository.player;

import com.jftse.entities.database.model.player.CardSlotEquipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface CardSlotEquipmentRepository extends JpaRepository<CardSlotEquipment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM CardSlotEquipment e WHERE e.id = :id")
    Optional<CardSlotEquipment> findByIdForUpdate(@Param("id") Long id);
}
