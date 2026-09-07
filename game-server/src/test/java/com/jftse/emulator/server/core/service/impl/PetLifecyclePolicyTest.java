package com.jftse.emulator.server.core.service.impl;

import com.jftse.entities.database.model.pet.Pet;
import com.jftse.server.core.service.impl.PetLifecyclePolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetLifecyclePolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    @Test
    void appliesWholeDayDecayAndPreservesPartialDayForTheNextRefresh() {
        Pet pet = livePet(10, 20);
        pet.setLifecycleUpdatedAt(Date.from(NOW.minus(49, ChronoUnit.HOURS)));

        assertTrue(PetLifecyclePolicy.refresh(pet, NOW));
        assertEquals(8, pet.getHunger());
        assertEquals(12, pet.getEnergy());
        assertEquals(NOW.minus(1, ChronoUnit.HOURS), pet.getLifecycleUpdatedAt().toInstant());

        assertFalse(PetLifecyclePolicy.refresh(pet, NOW.plus(22, ChronoUnit.HOURS)));
        assertTrue(PetLifecyclePolicy.refresh(pet, NOW.plus(23, ChronoUnit.HOURS)));
        assertEquals(7, pet.getHunger());
        assertEquals(8, pet.getEnergy());
    }

    @Test
    void initializesExistingPetsWithoutRetroactiveDecay() {
        Pet pet = livePet(10, 20);

        assertTrue(PetLifecyclePolicy.refresh(pet, NOW));
        assertEquals(NOW, pet.getLifecycleUpdatedAt().toInstant());
        assertEquals(10, pet.getHunger());
        assertEquals(20, pet.getEnergy());
    }

    @Test
    void hungerDepletionAndExpiryAreTerminalButZeroEnergyIsRecoverable() {
        Pet hungry = livePet(1, 20);
        hungry.setLifecycleUpdatedAt(Date.from(NOW.minus(1, ChronoUnit.DAYS)));
        Pet tired = livePet(10, 1);
        tired.setLifecycleUpdatedAt(Date.from(NOW.minus(1, ChronoUnit.DAYS)));
        Pet expired = livePet(10, 20);
        expired.setValidUntil(Date.from(NOW));

        PetLifecyclePolicy.refresh(hungry, NOW);
        PetLifecyclePolicy.refresh(tired, NOW);
        PetLifecyclePolicy.refresh(expired, NOW);

        assertFalse(hungry.getAlive());
        assertFalse(expired.getAlive());
        assertTrue(tired.getAlive());
        assertFalse(PetLifecyclePolicy.canParticipate(tired, NOW));
        assertTrue(PetLifecyclePolicy.isAlive(tired, NOW));
    }

    private static Pet livePet(int hunger, int energy) {
        Pet pet = new Pet();
        pet.setAlive(true);
        pet.setHunger(hunger);
        pet.setEnergy(energy);
        pet.setValidUntil(Date.from(NOW.plus(30, ChronoUnit.DAYS)));
        return pet;
    }
}
