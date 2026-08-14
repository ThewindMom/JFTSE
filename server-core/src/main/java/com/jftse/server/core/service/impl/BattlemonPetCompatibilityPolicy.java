package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.pet.Pet;

/**
 * Native-client safety boundary for Battlemon pets.
 *
 * <p>The retail EXP table supports displayed levels 1-250, but the validated
 * client only contains established AI_Pet Level1-Level13 profile records.
 * A two-client level-14 initialization attempt failed, but did not isolate
 * level as the cause. Keep the full table for protocol/UI compatibility while
 * choosing the last statically evidenced AI profile as a fail-safe gameplay
 * boundary.</p>
 */
public final class BattlemonPetCompatibilityPolicy {
    public static final int MAX_NATIVE_AI_LEVEL = 13;

    private BattlemonPetCompatibilityPolicy() {
    }

    public static boolean canParticipate(Pet pet) {
        if (pet == null || pet.getLevel() == null) {
            return false;
        }
        int level = Byte.toUnsignedInt(pet.getLevel());
        return level >= 1 && level <= MAX_NATIVE_AI_LEVEL;
    }

    static int capExperience(int experience) {
        return Math.min(Math.max(0, experience),
                PetLevelTable.experienceBeforeLevel(MAX_NATIVE_AI_LEVEL + 1));
    }
}
