package com.jftse.entities.database.repository.pet;

import com.jftse.entities.database.model.pet.Pet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PetRepository extends JpaRepository<Pet, Long> {
    @Query(value = "SELECT p FROM Pet p JOIN FETCH p.petStatistic ps WHERE p.player.id = :playerId")
    List<Pet> findAllByPlayerId(@Param("playerId") Long playerId);

    @Query(value = "SELECT p FROM Pet p JOIN FETCH p.petStatistic ps WHERE p.id = :petId AND p.player.id = :playerId")
    Optional<Pet> findByIdAndPlayerId(@Param("petId") Long petId, @Param("playerId") Long playerId);
}
