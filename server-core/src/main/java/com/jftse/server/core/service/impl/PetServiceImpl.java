package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.pet.PetStatistic;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.repository.pet.PetRepository;
import com.jftse.entities.database.repository.pet.PetStatisticRepository;
import com.jftse.server.core.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PetServiceImpl implements PetService {
    private final PetRepository petRepository;
    private final PetStatisticRepository petStatisticRepository;

    @Override
    @Transactional(readOnly = true)
    public Pet findById(Long id) {
        return petRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Pet findByIdAndPlayerId(Long id, Long playerId) {
        return petRepository.findByIdAndPlayerId(id, playerId).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pet> findAllByPlayerId(Long playerId) {
        return petRepository.findAllByPlayerId(playerId);
    }

    @Override
    @Transactional
    public Pet createPet(Integer itemIndex, Player player) {
        PetStatistic petStatistic = new PetStatistic();
        petStatistic = petStatisticRepository.save(petStatistic);

        return switch (itemIndex) {
            case 1 -> createPet("Pikaro", 0, 0, 0, 0, 180, 50, 100, 30, 60, 1, 0, petStatistic, player);
            case 2 -> createPet("Poteko", 0, 0, 0, 0, 200, 100, 150, 60, 120, 1, 1, petStatistic, player);
            case 3 -> createPet("Boonga", 0, 0, 0, 0, 200, 100, 150, 60, 120, 1, 2, petStatistic, player);
            case 4 -> createPet("Goliath", 0, 0, 0, 0, 280, 100, 150, 60, 120, 1, 3, petStatistic, player);
            case 5 -> createPet("Blood", 0, 0, 0, 0, 200, 100, 150, 60, 120, 1, 4, petStatistic, player);
            case 6 -> createPet("Goddess", 0, 0, 0, 0, 200, 100, 150, 60, 120, 1, 5, petStatistic, player);
            case 7 -> createPet("Lizard", 0, 0, 0, 0, 200, 100, 150, 60, 120, 1, 6, petStatistic, player);
            case 8 -> createPet("Tossakan", 0, 0, 0, 0, 280, 100, 150, 60, 120, 1, 7, petStatistic, player);
            case 9 -> createPet("Ninkaro", 0, 0, 0, 0, 280, 100, 150, 60, 120, 1, 8, petStatistic, player);
            default -> null;
        };
    }

    @Override
    @Transactional
    public Pet awardExperience(Long id, Long playerId, int experience) {
        if (experience <= 0) {
            return null;
        }
        Pet pet = petRepository.findByIdAndPlayerIdForUpdate(id, playerId).orElse(null);
        if (pet == null || !Boolean.TRUE.equals(pet.getAlive()) || pet.getValidUntil() == null ||
                !pet.getValidUntil().after(new Date())) {
            return null;
        }
        int currentExperience = pet.getExpPoints() == null ? 0 : Math.max(0, pet.getExpPoints());
        int newExperience = (int) Math.min(Integer.MAX_VALUE,
                (long) currentExperience + experience);
        pet.setExpPoints(newExperience);
        // LevelExp_Pet.xml is a cumulative EXP table only. Item_PetChar.xml has
        // no STR/STA/DEX/WIL columns, so a level change does not allocate stats.
        pet.setLevel(PetLevelTable.toStoredLevel(levelForExperience(newExperience)));
        return petRepository.save(pet);
    }

    @Override
    public int levelForExperience(int experience) {
        return PetLevelTable.levelForExperience(experience);
    }

    private Pet createPet(String nameLabel, int strength, int stamina, int dexterity, int willpower,
                           int hp, int energy, int hunger, int life, int lifeMax, int level, int model,
                           PetStatistic petStatistic, Player player) {
        Pet pet = new Pet();
        pet.setPetStatistic(petStatistic);
        pet.setPlayer(player);
        pet.setName(nameLabel);
        pet.setType((byte) model);
        pet.setLevel((byte) level);
        pet.setExpPoints(0);
        pet.setHp(hp);
        pet.setStrength((byte) strength);
        pet.setStamina((byte) stamina);
        pet.setDexterity((byte) dexterity);
        pet.setWillpower((byte) willpower);
        pet.setHunger(hunger);
        pet.setEnergy(energy);
        pet.setLifeMax(lifeMax);
        pet.setAlive(true);
        pet.setValidUntil(calculateValidUntil(life));

        return petRepository.save(pet);
    }

    private Date calculateValidUntil(int life) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, life);
        return calendar.getTime();
    }
}
