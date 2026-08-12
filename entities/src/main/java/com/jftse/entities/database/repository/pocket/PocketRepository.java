package com.jftse.entities.database.repository.pocket;

import com.jftse.entities.database.model.pocket.Pocket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface PocketRepository extends JpaRepository<Pocket, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Pocket> findLockedById(Long id);
}
