package com.jftse.server.core.service;

import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.player.Player;

import java.util.List;

public interface PetService {
    Pet findById(Long id);
    Pet findByIdAndPlayerId(Long id, Long playerId);
    List<Pet> findAllByPlayerId(Long playerId);
    Pet createPet(Integer itemIndex, Player player);
    Pet awardExperience(Long id, Long playerId, int experience);
}
