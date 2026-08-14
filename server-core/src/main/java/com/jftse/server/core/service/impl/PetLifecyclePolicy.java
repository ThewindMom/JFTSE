package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.pet.Pet;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Compatibility lifecycle used until retail decay constants are recovered.
 * Decay is applied lazily in whole UTC-duration days so repeated refreshes are idempotent.
 */
public final class PetLifecyclePolicy {
    public static final int HUNGER_DECAY_PER_DAY = 1;
    public static final int ENERGY_DECAY_PER_DAY = 4;

    private PetLifecyclePolicy() {
    }

    public static boolean refresh(Pet pet, Instant now) {
        if (pet == null) return false;

        boolean changed = false;
        Date updatedAt = pet.getLifecycleUpdatedAt();
        if (updatedAt == null || updatedAt.toInstant().isAfter(now)) {
            pet.setLifecycleUpdatedAt(Date.from(now));
            changed = true;
        } else {
            long elapsedDays = Duration.between(updatedAt.toInstant(), now).toDays();
            if (elapsedDays > 0) {
                int hunger = nonNegative(pet.getHunger());
                int energy = nonNegative(pet.getEnergy());
                pet.setHunger(decay(hunger, elapsedDays, HUNGER_DECAY_PER_DAY));
                pet.setEnergy(decay(energy, elapsedDays, ENERGY_DECAY_PER_DAY));
                pet.setLifecycleUpdatedAt(Date.from(updatedAt.toInstant().plus(elapsedDays, ChronoUnit.DAYS)));
                changed = true;
            }
        }

        boolean shouldBeAlive = Boolean.TRUE.equals(pet.getAlive()) &&
                pet.getValidUntil() != null && pet.getValidUntil().toInstant().isAfter(now) &&
                nonNegative(pet.getHunger()) > 0;
        if (Boolean.TRUE.equals(pet.getAlive()) != shouldBeAlive) {
            pet.setAlive(shouldBeAlive);
            changed = true;
        }
        return changed;
    }

    public static boolean canParticipate(Pet pet, Instant now) {
        return pet != null && Boolean.TRUE.equals(pet.getAlive()) &&
                pet.getValidUntil() != null && pet.getValidUntil().toInstant().isAfter(now) &&
                nonNegative(pet.getHunger()) > 0 && nonNegative(pet.getEnergy()) > 0;
    }

    public static boolean isAlive(Pet pet, Instant now) {
        return pet != null && Boolean.TRUE.equals(pet.getAlive()) &&
                pet.getValidUntil() != null && pet.getValidUntil().toInstant().isAfter(now) &&
                nonNegative(pet.getHunger()) > 0;
    }

    private static int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static int decay(int value, long elapsedDays, int perDay) {
        long amount = Math.min(Integer.MAX_VALUE, elapsedDays * perDay);
        return (int) Math.max(0, value - amount);
    }
}
