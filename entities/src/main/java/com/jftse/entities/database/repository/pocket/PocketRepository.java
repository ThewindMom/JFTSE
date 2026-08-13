package com.jftse.entities.database.repository.pocket;

import com.jftse.entities.database.model.pocket.Pocket;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PocketRepository extends JpaRepository<Pocket, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pocket p WHERE p.id = :pocketId")
    Optional<Pocket> findByIdForUpdate(@Param("pocketId") Long pocketId);
}
