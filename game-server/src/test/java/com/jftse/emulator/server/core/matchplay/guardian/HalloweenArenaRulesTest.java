package com.jftse.emulator.server.core.matchplay.guardian;

import com.jftse.entities.database.model.battle.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HalloweenArenaRulesTest {
    private static final long NOW = 1_000_000L;

    @Test
    void stockClientHealAndShieldItemsAreHiddenButAttacksAndReviveStayEquipped() {
        for (int index : new int[]{1, 3, 4, 5, 7, 13, 19, 20, 21, 22, 23, 25, 26, 27, 28, 29, 50}) {
            assertTrue(HalloweenArenaRules.isSealedQuickItem(index), "quick item " + index);
        }
        for (int index : new int[]{0, 2, 6, 8, 9, 10, 11, 12, 14, 15, 16, 17, 18, 24, 30, 31, 49, 51, 62}) {
            assertFalse(HalloweenArenaRules.isSealedQuickItem(index), "quick item " + index);
        }
    }

    @Test
    void crystalPoolWithoutDeadTeammateIsSeventyHealThirtyShield() {
        assertEquals(0, HalloweenArenaRules.crystalSkillIndex(false, 0, 0));
        assertEquals(0, HalloweenArenaRules.crystalSkillIndex(false, 0, 69));
        assertEquals(9, HalloweenArenaRules.crystalSkillIndex(false, 0, 70));
        assertEquals(9, HalloweenArenaRules.crystalSkillIndex(false, 0, 99));
    }

    @Test
    void crystalPoolWithDeadTeammateKeepsThirtyFivePercentRevive() {
        assertEquals(4, HalloweenArenaRules.crystalSkillIndex(true, 0, 99));
        assertEquals(4, HalloweenArenaRules.crystalSkillIndex(true, 34, 0));
        assertEquals(0, HalloweenArenaRules.crystalSkillIndex(true, 35, 69));
        assertEquals(9, HalloweenArenaRules.crystalSkillIndex(true, 35, 70));
    }

    @Test
    void crystalPoolDistributionOverEveryRollPair() {
        int[] alive = new int[10];
        int[] dead = new int[10];
        for (int reviveRoll = 0; reviveRoll < 100; reviveRoll++) {
            for (int poolRoll = 0; poolRoll < 100; poolRoll++) {
                alive[HalloweenArenaRules.crystalSkillIndex(false, reviveRoll, poolRoll)]++;
                dead[HalloweenArenaRules.crystalSkillIndex(true, reviveRoll, poolRoll)]++;
            }
        }
        assertEquals(7000, alive[0]);
        assertEquals(3000, alive[9]);
        assertEquals(0, alive[4]);
        assertEquals(3500, dead[4]);
        assertEquals(4550, dead[0]);
        assertEquals(1950, dead[9]);
    }

    @Test
    void onlyHealAndShieldAreSealedWhenEquipped() {
        for (long id : new long[]{1, 2, 10, 16, 17, 18, 19, 20, 23, 24, 30, 31, 33, 39}) {
            assertTrue(HalloweenArenaRules.isSealedWhenEquipped(skill(id)), "skill " + id);
        }
        // Revive, RebirthOne, Sandglass, Crab Trap / attack skills stay usable.
        for (long id : new long[]{3, 4, 5, 6, 7, 8, 9, 11, 21, 22, 25, 29, 35, 38, 46}) {
            assertFalse(HalloweenArenaRules.isSealedWhenEquipped(skill(id)), "skill " + id);
        }
        assertFalse(HalloweenArenaRules.isSealedWhenEquipped(null));
    }

    @Test
    void sealedEquippedHealIgnoresItsHitsForTheWindowOnly() {
        HalloweenArenaRules.SealedEffects sealed = new HalloweenArenaRules.SealedEffects();
        sealed.seal(skill(19), 0, NOW);

        assertTrue(sealed.isHealHitSealed(skill(19), 0, 2, 0, NOW + 150));
        assertTrue(sealed.isHealHitSealed(skill(19), 0, 2, 2, NOW + 150));
        assertTrue(sealed.isHealHitSealed(skill(19), 4, 0, 0, NOW + 150));
        assertFalse(sealed.isHealHitSealed(skill(19), 4, 2, 2, NOW + 150));
        assertFalse(sealed.isHealHitSealed(skill(19), 0, 0, 10, NOW + 150));
        assertFalse(sealed.isHealHitSealed(skill(1), 0, 0, 0, NOW + 150));
        assertFalse(sealed.isHealHitSealed(skill(19), 0, 0, 0, NOW + HalloweenArenaRules.SEALED_EFFECT_MS));
        assertFalse(sealed.isShieldSealed(0, NOW + 150));
    }

    @Test
    void crystalHealLiftsOnlyItsCastersSealOnTheSameSkill() {
        HalloweenArenaRules.SealedEffects sealed = new HalloweenArenaRules.SealedEffects();
        sealed.seal(skill(1), 0, NOW);
        sealed.seal(skill(2), 0, NOW);
        sealed.allowCrystal(skill(1), 0, NOW + 100);

        assertFalse(sealed.isHealHitSealed(skill(1), 4, 0, 0, NOW + 150));
        assertTrue(sealed.isHealHitSealed(skill(2), 4, 0, 0, NOW + 150));
    }

    @Test
    void sealedShieldCoversCasterAndAreaShieldCoversTheTeam() {
        HalloweenArenaRules.SealedEffects sealed = new HalloweenArenaRules.SealedEffects();
        sealed.seal(skill(10), 1, NOW);
        assertTrue(sealed.isShieldSealed(1, NOW + 1000));
        assertFalse(sealed.isShieldSealed(0, NOW + 1000));

        sealed.seal(skill(20), 3, NOW);
        for (int position = 0; position < 4; position++) {
            assertTrue(sealed.isShieldSealed(position, NOW + 1000));
        }
        assertFalse(sealed.isShieldSealed(11, NOW + 1000));
        assertFalse(sealed.isShieldSealed(0, NOW + HalloweenArenaRules.SEALED_EFFECT_MS));

        sealed.allowCrystal(skill(10), 2, NOW + 100);
        assertFalse(sealed.isShieldSealed(2, NOW + 1000));
        assertTrue(sealed.isShieldSealed(0, NOW + 1000));
        assertFalse(sealed.isShieldSealed(2, NOW + 3099));
        assertTrue(sealed.isShieldSealed(2, NOW + 3100));
    }

    @Test
    void anotherPlayersCrystalHealDoesNotAllowRejectedHealHits() {
        HalloweenArenaRules.SealedEffects sealed = new HalloweenArenaRules.SealedEffects();
        sealed.seal(skill(1), 0, NOW);
        sealed.allowCrystal(skill(1), 2, NOW + 100);

        assertTrue(sealed.isHealHitSealed(skill(1), 0, 0, 1, NOW + 150));
        assertFalse(sealed.isHealHitSealed(skill(1), 2, 2, 1, NOW + 150));
        assertTrue(sealed.isHealHitSealed(skill(1), 4, 0, 0, NOW + 150));
        assertFalse(sealed.isHealHitSealed(skill(1), 4, 2, 2, NOW + 150));
    }

    @Test
    void rejectedHealDoesNotSuppressAnotherPlayersEarlierCrystalHeal() {
        HalloweenArenaRules.SealedEffects sealed = new HalloweenArenaRules.SealedEffects();
        sealed.allowCrystal(skill(1), 2, NOW);
        sealed.seal(skill(1), 0, NOW + 100);

        assertFalse(sealed.isHealHitSealed(skill(1), 2, 2, 1, NOW + 150));
        assertTrue(sealed.isHealHitSealed(skill(1), 0, 0, 1, NOW + 150));
        assertFalse(sealed.isHealHitSealed(skill(1), 4, 2, 2, NOW + 150));
        assertTrue(sealed.isHealHitSealed(skill(1), 4, 0, 0, NOW + 150));
    }

    @Test
    void rejectedAreaShieldPreservesAnotherPlayersEarlierCrystalShield() {
        HalloweenArenaRules.SealedEffects sealed = new HalloweenArenaRules.SealedEffects();
        sealed.allowCrystal(skill(10), 2, NOW);
        sealed.seal(skill(20), 0, NOW + 100);

        assertFalse(sealed.isShieldSealed(2, NOW + 150));
        assertTrue(sealed.isShieldSealed(1, NOW + 150));
        assertFalse(sealed.isShieldSealed(2, NOW + 2999));
        assertTrue(sealed.isShieldSealed(2, NOW + 3000));
        assertFalse(sealed.isShieldSealed(2, NOW + 4100));
    }

    @Test
    void crystalAreaShieldProtectsWholeTeamBeforeAndAfterRejectedShield() {
        for (boolean crystalFirst : new boolean[]{true, false}) {
            HalloweenArenaRules.SealedEffects sealed = new HalloweenArenaRules.SealedEffects();
            if (crystalFirst) {
                sealed.allowCrystal(skill(20), 3, NOW);
            }
            sealed.seal(skill(20), 0, NOW + 100);
            if (!crystalFirst) {
                sealed.allowCrystal(skill(20), 3, NOW + 200);
            }

            for (int position = 0; position < 4; position++) {
                assertFalse(sealed.isShieldSealed(position, NOW + 250), "position " + position);
            }
            assertTrue(sealed.isShieldSealed(1, NOW + 3200));
        }
    }

    private static Skill skill(long id) {
        Skill skill = new Skill();
        skill.setId(id);
        skill.setPlayTime(3.0);
        return skill;
    }
}
