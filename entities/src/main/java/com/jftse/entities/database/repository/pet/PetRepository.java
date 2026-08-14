package com.jftse.entities.database.repository.pet;

import com.jftse.entities.database.model.pet.Pet;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PetRepository extends JpaRepository<Pet, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT p FROM Pet p JOIN FETCH p.petStatistic ps WHERE p.id = :petId")
    Optional<Pet> findByIdForUpdate(@Param("petId") Long petId);

    @Query(value = "SELECT p FROM Pet p JOIN FETCH p.petStatistic ps WHERE p.player.id = :playerId")
    List<Pet> findAllByPlayerId(@Param("playerId") Long playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT p FROM Pet p JOIN FETCH p.petStatistic ps WHERE p.player.id = :playerId")
    List<Pet> findAllByPlayerIdForUpdate(@Param("playerId") Long playerId);

    @Query(value = "SELECT p FROM Pet p JOIN FETCH p.petStatistic ps WHERE p.id = :petId AND p.player.id = :playerId")
    Optional<Pet> findByIdAndPlayerId(@Param("petId") Long petId, @Param("playerId") Long playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT p FROM Pet p JOIN FETCH p.petStatistic ps WHERE p.id = :petId AND p.player.id = :playerId")
    Optional<Pet> findByIdAndPlayerIdForUpdate(@Param("petId") Long petId, @Param("playerId") Long playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT p FROM Pet p JOIN FETCH p.petStatistic ps WHERE p.player.id = :playerId AND p.type = :type")
    List<Pet> findAllByPlayerIdAndTypeForUpdate(@Param("playerId") Long playerId, @Param("type") Byte type);
}
