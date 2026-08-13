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
        assertEquals((byte) 1, pet.getLevel());
        verify(petRepository).save(pet);
    }

    @Test
    void awardExperienceRaisesLevelFromTheClientPetTableWithoutChangingStats() {
        Pet pet = pet(0, true, new Date(System.currentTimeMillis() + 60_000));
        pet.setLevel((byte) 1);
        pet.setStrength((byte) 0);
        pet.setStamina((byte) 0);
        pet.setDexterity((byte) 0);
        pet.setWillpower((byte) 0);
        when(petRepository.findByIdAndPlayerIdForUpdate(7L, 11L)).thenReturn(Optional.of(pet));
        when(petRepository.save(pet)).thenReturn(pet);

        Pet result = petService.awardExperience(7L, 11L, 90);

        assertSame(pet, result);
        assertEquals(90, pet.getExpPoints());
        assertEquals((byte) 2, pet.getLevel());
        assertEquals((byte) 0, pet.getStrength());
        assertEquals((byte) 0, pet.getStamina());
        assertEquals((byte) 0, pet.getDexterity());
        assertEquals((byte) 0, pet.getWillpower());
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

    @Test
    void levelForExperienceFollowsTheDecryptedClientThresholds() {
        assertEquals(1, petService.levelForExperience(0));
        assertEquals(1, petService.levelForExperience(89));
        assertEquals(2, petService.levelForExperience(90));
        assertEquals(2, petService.levelForExperience(200));
        assertEquals(3, petService.levelForExperience(201));
        assertEquals(20, petService.levelForExperience(15623));
        assertEquals(50, petService.levelForExperience(109800));
        assertEquals(250, petService.levelForExperience(1_408_515));
        assertEquals(250, petService.levelForExperience(Integer.MAX_VALUE));
    }

    @Test
    void displayedLevelTwoFiftyFitsTheUnsignedPetLevelByteWithoutRaisingStats() {
        Pet pet = pet(0, true, new Date(System.currentTimeMillis() + 60_000));
        pet.setHp(180);
        pet.setStrength((byte) 0);
        pet.setStamina((byte) 0);
        pet.setDexterity((byte) 0);
        pet.setWillpower((byte) 0);
        when(petRepository.findByIdAndPlayerIdForUpdate(7L, 11L)).thenReturn(Optional.of(pet));
        when(petRepository.save(pet)).thenReturn(pet);

        Pet result = petService.awardExperience(7L, 11L, 1_408_515);

        assertEquals(1_408_515, result.getExpPoints());
        assertEquals(250, Byte.toUnsignedInt(result.getLevel()));
        assertEquals(180, result.getHp());
        assertEquals((byte) 0, result.getStrength());
        assertEquals((byte) 0, result.getStamina());
        assertEquals((byte) 0, result.getDexterity());
        assertEquals((byte) 0, result.getWillpower());
    }

    private static Pet pet(int experience, boolean alive, Date validUntil) {
        Pet pet = new Pet();
        pet.setExpPoints(experience);
        pet.setLevel((byte) 1);
        pet.setAlive(alive);
        pet.setValidUntil(validUntil);
        return pet;
    }
}
