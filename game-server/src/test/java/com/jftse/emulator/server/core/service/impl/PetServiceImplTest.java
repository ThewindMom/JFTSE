package com.jftse.emulator.server.core.service.impl;

import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.repository.pet.PetRepository;
import com.jftse.entities.database.repository.pet.PetStatisticRepository;
import com.jftse.server.core.service.impl.PetServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PetServiceImplTest {
    private final PetRepository petRepository = mock(PetRepository.class);
    private final PetServiceImpl petService = new PetServiceImpl(
            petRepository, mock(PetStatisticRepository.class));

    @Test
    void awardExperiencePersistsForTheOwnedLivePet() {
        Pet pet = pet(20, true, new Date(System.currentTimeMillis() + 60_000));
        when(petRepository.findByIdAndPlayerIdForUpdate(7L, 11L)).thenReturn(Optional.of(pet));
        when(petRepository.save(pet)).thenReturn(pet);

        Pet result = petService.awardExperience(7L, 11L, 15);

        assertSame(pet, result);
        assertEquals(35, pet.getExpPoints());
        verify(petRepository).save(pet);
    }

    @Test
    void awardExperienceIgnoresMissingExpiredAndNonPositiveAwards() {
        Pet expired = pet(20, true, new Date(System.currentTimeMillis() - 60_000));
        when(petRepository.findByIdAndPlayerIdForUpdate(7L, 11L)).thenReturn(Optional.empty());
        when(petRepository.findByIdAndPlayerIdForUpdate(8L, 11L)).thenReturn(Optional.of(expired));

        assertNull(petService.awardExperience(7L, 11L, 15));
        assertNull(petService.awardExperience(8L, 11L, 15));
        assertNull(petService.awardExperience(8L, 11L, 0));

        verify(petRepository, never()).save(org.mockito.ArgumentMatchers.any(Pet.class));
    }

    private static Pet pet(int experience, boolean alive, Date validUntil) {
        Pet pet = new Pet();
        pet.setExpPoints(experience);
        pet.setAlive(alive);
        pet.setValidUntil(validUntil);
        return pet;
    }
}
