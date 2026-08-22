package com.jftse.emulator.server.core.matchplay.guardian;

import com.jftse.emulator.server.core.matchplay.MatchplayHandleable;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.entities.database.model.battle.Skill;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AtlantisV2RulesTest {

    @Test
    void map10AlwaysResolvesToLiveGroupTen() {
        assertEquals("10", AtlantisV2Rules.resolvePhaseGroup(10));
        assertTrue(AtlantisV2Rules.isAtlantisMap(10));
        assertEquals("7", AtlantisV2Rules.resolvePhaseGroup(7));
        assertEquals("", AtlantisV2Rules.resolvePhaseGroup(null));
        assertFalse(AtlantisV2Rules.isAtlantisMap(11));
        assertNotEquals("10-legacy", AtlantisV2Rules.resolvePhaseGroup(10));
        assertNotEquals("10-v2", AtlantisV2Rules.resolvePhaseGroup(10));
    }

    @Test
    void atlantisOptsOutWithoutRemovingPadsFromOtherGuardianMaps() {
        assertFalse(AtlantisV2Rules.shouldSchedulePads(10));
        assertTrue(AtlantisV2Rules.shouldSchedulePads(7));
        assertTrue(AtlantisV2Rules.shouldSchedulePads(11));
        assertFalse(AtlantisV2Rules.shouldSchedulePads(null));
    }

    @Test
    void atlantisAdvancedBossRotatesTwoDistinctLizardAids() {
        assertEquals(List.of(43L, 44L, 45L, 46L, 47L, 48L), AtlantisV2Rules.LIZARD_FAMILY_IDS);
        assertTrue(AtlantisV2Rules.shouldUseLizardFamilyAids(10, true));
        assertFalse(AtlantisV2Rules.shouldUseLizardFamilyAids(10, false));
        assertFalse(AtlantisV2Rules.shouldUseLizardFamilyAids(7, true));
        assertFalse(AtlantisV2Rules.shouldUseLizardFamilyAids(null, true));

        Random random = new Random(42);
        for (int i = 0; i < 100; i++) {
            List<Long> aids = AtlantisV2Rules.selectLizardAidIds(random);
            assertEquals(2, aids.size());
            assertNotEquals(aids.get(0), aids.get(1));
            assertTrue(AtlantisV2Rules.LIZARD_FAMILY_IDS.containsAll(aids));
        }
    }

    @Test
    void scriptedAtlantisSuppressesNativeGuardianLoadout() {
        assertTrue(AtlantisV2Rules.shouldSuppressNativeGuardianAttacks(10, true));
        assertFalse(AtlantisV2Rules.shouldSuppressNativeGuardianAttacks(10, false));
        assertFalse(AtlantisV2Rules.shouldSuppressNativeGuardianAttacks(7, true));
        assertFalse(AtlantisV2Rules.shouldSuppressNativeGuardianAttacks(null, true));
    }

    @Test
    void seaWaveIsRecognizedFromPacketAndTableIds() {
        Skill table = skill(28L, "SeaWave");
        assertTrue(AtlantisV2Rules.isSeaWaveHit(table, (byte) 27));
        assertTrue(AtlantisV2Rules.isSeaWaveHit(null, (byte) 27));
        assertTrue(AtlantisV2Rules.isSeaWaveHit(table, (byte) 0));
        assertTrue(AtlantisV2Rules.isSeaWaveHit(skill(99L, "SeaWave"), (byte) 1));
        assertFalse(AtlantisV2Rules.isSeaWaveHit(skill(13L, "Blizzard"), (byte) 12));
        assertFalse(AtlantisV2Rules.isSeaWaveHit(null, (byte) 12));
    }

    @Test
    void seaWaveDropsGuardianHitsButNotPlayerHits() {
        assertTrue(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 10, false));
        assertTrue(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 11, true));
        assertFalse(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 0, false));
        assertFalse(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 3, false));
        assertFalse(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 4, true));
    }

    @Test
    void shieldAndHealStripsAreIndependent() {
        Skill shield = skill(10L, "Shield");
        Skill teamShield = skill(20L, "TeamShield");
        Skill heal = skill(1L, "Heal");
        Skill teamHeal = skill(16L, "TeamHeal");
        Skill wave = skill(28L, "SeaWave");

        assertTrue(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 0, -1, (short) 0, shield, false));
        assertTrue(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, false, (short) 0, -1, (short) 0, shield, false));
        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportHit(false, true, (short) 0, -1, (short) 0, shield, false));
        assertTrue(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, false, (short) 2, -1, (short) 2, teamShield, false));

        assertTrue(AtlantisV2Rules.shouldIgnorePlayerSupportHit(false, true, (short) 1, -1, (short) 1, heal, false));
        assertTrue(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 1, -1, (short) 1, teamHeal, false));
        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, false, (short) 1, -1, (short) 1, heal, false));

        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 2, 2, (short) 0, shield, false));
        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 2, 2, (short) 1, teamHeal, false));
        assertTrue(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 1, 2, (short) 0, shield, false));
        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 1, 2, (short) 1, teamHeal, true),
                "an authorized area heal collision reported by an ally must pass");
        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 1, 2, (short) 1, teamShield, true),
                "an authorized area shield collision reported by an ally must pass");
        assertTrue(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 1, 2, (short) 1, shield, true),
                "area authorization must not enable a single-target support skill");
        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 0, -1, (short) 10, shield, false));
        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 0, -1, (short) 0, wave, false));
        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 0, -1, (short) 0, null, false));

        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportUse(true, true, (short) 2, 2, teamHeal));
        assertTrue(AtlantisV2Rules.shouldIgnorePlayerSupportUse(true, true, (short) 1, 2, teamHeal));
        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportUse(false, false, (short) 1, -1, teamHeal));
        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportUse(true, true, (short) 1, 2, wave));
    }

    @Test
    void liveSkillUseIdsMatchShieldAndHealTables() {
        assertTrue(AtlantisV2Rules.isPlayerShieldSkill(skill(10L, "Shield")));
        assertTrue(AtlantisV2Rules.isPlayerShieldSkill(skill(20L, "TeamShield")));
        assertTrue(AtlantisV2Rules.isPlayerHealSkill(skill(1L, "Heal")));
        assertTrue(AtlantisV2Rules.isPlayerHealSkill(skill(2L, "Heal2")));
        assertTrue(AtlantisV2Rules.isPlayerHealSkill(skill(31L, "Heal31")));
        assertTrue(AtlantisV2Rules.isPlayerHealSkill(skill(39L, "Heal39")));
        assertTrue(AtlantisV2Rules.isPlayerHealSkill(skill(16L, "RangeHeal")));
        assertTrue(AtlantisV2Rules.isPlayerHealSkill(skill(19L, "RangeHeal4")));
        assertTrue(AtlantisV2Rules.isPlayerAreaSupportSkill(skill(16L, "RangeHeal")));
        assertTrue(AtlantisV2Rules.isPlayerAreaSupportSkill(skill(20L, "RangeShield")));
        assertFalse(AtlantisV2Rules.isPlayerAreaSupportSkill(skill(1L, "Heal")));
        assertFalse(AtlantisV2Rules.isPlayerAreaSupportSkill(skill(10L, "Shield")));
        assertFalse(AtlantisV2Rules.isPlayerSupportSkill(skill(28L, "SeaWave")));
        assertFalse(AtlantisV2Rules.isPlayerSupportSkill(skill(12L, "SpiderMine")));

        assertTrue(AtlantisV2Rules.isAllyAreaSupportHitReport((short) 1, 0, (short) 4, (short) 1));
        assertFalse(AtlantisV2Rules.isAllyAreaSupportHitReport((short) 0, 0, (short) 4, (short) 0),
                "the selected caster's own report does not consume ally authorization");
        assertFalse(AtlantisV2Rules.isAllyAreaSupportHitReport((short) 1, 0, (short) 0, (short) 1),
                "ordinary player attacks cannot consume ally authorization");
        assertFalse(AtlantisV2Rules.isAllyAreaSupportHitReport((short) 1, 0, (short) 4, (short) 2),
                "an ally must report its own collision target");
    }

    @Test
    void selectedSupportPlayersAreaCastAuthorizesEachAllyHitOnce() {
        MatchplayGuardianGame game = new TestMatchplayGuardianGame();
        Skill rangeHeal = skill(19L, "RangeHeal4");
        Skill rangeShield = skill(20L, "RangeShield");
        game.setPlayerSupportExemptPosition(0);
        try {
            AtlantisV2Rules.setNowForTest(1_000L);
            game.authorizePlayerAreaSupportHits(0, rangeHeal);
            assertFalse(game.consumeAuthorizedPlayerAreaSupportHit((short) 1, rangeHeal),
                    "a cast needs an active suppression window before it can be authorized");

            game.setPlayerSupportSkillsDisabled(true);
            game.authorizePlayerAreaSupportHits(1, rangeHeal);
            assertFalse(game.consumeAuthorizedPlayerAreaSupportHit((short) 1, rangeHeal),
                    "a non-selected player cannot authorize an area cast");

            game.authorizePlayerAreaSupportHits(0, rangeHeal);
            assertFalse(game.consumeAuthorizedPlayerAreaSupportHit((short) 1, rangeShield),
                    "a report for another area skill cannot use or consume the authorization");
            assertTrue(game.consumeAuthorizedPlayerAreaSupportHit((short) 1, rangeHeal));
            assertFalse(game.consumeAuthorizedPlayerAreaSupportHit((short) 1, rangeHeal),
                    "one client collision must not apply the same cast twice");
            assertTrue(game.consumeAuthorizedPlayerAreaSupportHit((short) 2, rangeHeal));

            AtlantisV2Rules.setNowForTest(3_001L);
            assertFalse(game.consumeAuthorizedPlayerAreaSupportHit((short) 3, rangeHeal),
                    "stale area-support casts must not authorize future hits");

            game.authorizePlayerAreaSupportHits(0, rangeShield);
            assertTrue(game.consumeAuthorizedPlayerAreaSupportHit((short) 3, rangeShield),
                    "the selected player's area shield authorizes ally reports too");
        } finally {
            AtlantisV2Rules.clearNowForTest();
        }
    }

    @Test
    void postReviveHealIsTwentyPercentAndDoesNotOverflow() {
        assertEquals(520, AtlantisV2Rules.applyHeal(500, 1000, 100, AtlantisV2Rules.POST_REVIVE_HEAL_MULTIPLIER));
        assertEquals(1000, AtlantisV2Rules.applyHeal(990, 1000, 100, AtlantisV2Rules.POST_REVIVE_HEAL_MULTIPLIER));
        assertEquals(400, AtlantisV2Rules.applyHeal(400, 1000, 100, 0.0d));
        assertEquals(400, AtlantisV2Rules.applyHeal(400, 1000, 0, 1.0d));
    }

    @Test
    void phaseOrderIsFilenameSortAndV2NamesAreTheLiveSet() {
        List<String> original = new ArrayList<>(List.of(
                "4_enrage.js", "1_green_tide.js", "3_rising_tide.js", "2_twin_tides.js"));
        assertEquals(
                List.of("1_green_tide.js", "2_twin_tides.js", "3_rising_tide.js", "4_enrage.js"),
                AtlantisV2Rules.sortPhaseScriptNames(original));
        assertEquals("4_enrage.js", original.get(0), "sort must copy, not mutate the shared list");
        assertTrue(AtlantisV2Rules.isV2PhaseScriptName("1_green_tide.js"));
        assertTrue(AtlantisV2Rules.isLegacyPhaseScriptName("1_echoes_of_the_deep.js"));
        assertFalse(AtlantisV2Rules.isV2PhaseScriptName("1_echoes_of_the_deep.js"));
    }

    @Test
    void phaseOneUsesMonslavaPacingAndEnemyCourtWavePlants() {
        assertEquals((byte) 27, AtlantisV2Rules.SEA_WAVE_PACKET_ID);
        assertEquals(28L, AtlantisV2Rules.SEA_WAVE_SKILL_ID);
        assertEquals(6L, AtlantisV2Rules.HOMING_BALL_SKILL_ID);
        assertEquals(13L, AtlantisV2Rules.BLIZZARD_SKILL_ID);
        assertEquals(62L, AtlantisV2Rules.STORM_SKILL_ID);
        assertEquals(3, AtlantisV2Rules.FIRST_VOLLEY_COUNT);
        assertEquals(5, AtlantisV2Rules.SECOND_VOLLEY_COUNT);
        assertEquals(5, AtlantisV2Rules.BOSS_FINAL_VOLLEY_COUNT);
        assertEquals(0.50d, AtlantisV2Rules.ADD_PHASE_TRIGGER_HEALTH);
        assertEquals(0.90d, AtlantisV2Rules.BOSS_FINAL_PHASE_HEALTH);
        assertEquals(0.70d, AtlantisV2Rules.BOSS_NEXT_PHASE_HEALTH);
        assertEquals(4_000L, AtlantisV2Rules.BOSS_ATTACK_INTERVAL_MS);
        assertEquals(6_000L, AtlantisV2Rules.ADD_ATTACK_MIN_MS);
        assertEquals(12_000L, AtlantisV2Rules.ADD_ATTACK_MAX_MS);
        assertEquals(1_000L, AtlantisV2Rules.PHASE_ONE_WAVE_GAP_MS);
        assertEquals(4_000L, AtlantisV2Rules.PHASE_ONE_VOLLEY_REST_MIN_MS);
        assertEquals(5_000L, AtlantisV2Rules.PHASE_ONE_VOLLEY_REST_MAX_MS);
        assertEquals(-60f, AtlantisV2Rules.PHASE_ONE_WAVE_X_MIN);
        assertEquals(60f, AtlantisV2Rules.PHASE_ONE_WAVE_X_MAX);
        assertArrayEquals(new float[]{50f, 75f, 100f}, AtlantisV2Rules.SEA_WAVE_DEPTHS);
        for (float depth : AtlantisV2Rules.SEA_WAVE_DEPTHS) {
            assertTrue(depth > 0, "every randomized Atlantis depth must stay on the enemy court");
        }

        assertEquals(0.10d, AtlantisV2Rules.TWIN_TIDE_WAVE_DELTA);
        assertEquals(0.20d, AtlantisV2Rules.TWIN_TIDE_PILLAR_DELTA);
        assertEquals(0.30d, AtlantisV2Rules.TWIN_TIDE_BLIZZARD_DELTA);
        assertEquals(0.60d, AtlantisV2Rules.TWIN_TIDE_START_HEALTH);
        assertEquals(0.10d, AtlantisV2Rules.TWIN_TIDE_EXECUTE_HEALTH);
        assertEquals(0.30d, AtlantisV2Rules.TWIN_TIDE_REVIVE_HEALTH);
        assertEquals(3, AtlantisV2Rules.TWIN_TIDE_WAVE_COUNT);
        assertEquals(5, AtlantisV2Rules.TWIN_TIDE_PILLAR_WAVE_COUNT);
        assertEquals(8, AtlantisV2Rules.TWIN_TIDE_BLIZZARD_WAVE_COUNT);
        assertEquals(10_000L, AtlantisV2Rules.TWIN_TIDE_HOMING_BALL_MS);
        assertEquals(10_000L, AtlantisV2Rules.TWIN_TIDE_KILL_WINDOW_MS);
        assertEquals(61L, AtlantisV2Rules.WATER_PILLAR_SKILL_ID);
        assertEquals(0.25d, AtlantisV2Rules.RISING_TIDE_END_HEALTH);
        assertEquals(3, AtlantisV2Rules.RISING_TIDE_MAX_LEVEL);
        assertEquals(10_000L, AtlantisV2Rules.RISING_TIDE_HOMING_BALL_MS);
        assertEquals(15_000L, AtlantisV2Rules.RISING_TIDE_WATER_PILLAR_MS);
        assertEquals(20_000L, AtlantisV2Rules.RISING_TIDE_BLIZZARD_MS);
        assertEquals(30_000L, AtlantisV2Rules.RISING_TIDE_STORM_MS);
        assertEquals(25, AtlantisV2Rules.TIDAL_CONVERGENCE_RADIUS);
        assertEquals(3_000L, AtlantisV2Rules.TIDAL_CONVERGENCE_WARNING_MS);
        assertEquals(5_000L, AtlantisV2Rules.TIDAL_CONVERGENCE_SCAN_MS);
        assertEquals(18_000L, AtlantisV2Rules.TWIN_TIDE_CONVERGENCE_COOLDOWN_MS);
        assertEquals(15_000L, AtlantisV2Rules.RISING_TIDE_CONVERGENCE_COOLDOWN_MS);
        assertEquals(12_000L, AtlantisV2Rules.DROWNED_CROWN_CONVERGENCE_COOLDOWN_MS);
        assertEquals(5_000L, AtlantisV2Rules.DROWNED_CROWN_CALM_MS);
        assertEquals(0.35d, AtlantisV2Rules.DROWNED_CROWN_START_HEALTH);
        assertEquals(0.30d, AtlantisV2Rules.DROWNED_CROWN_ADD_HEALTH);
        assertEquals(0.02d, AtlantisV2Rules.BLOOD_TIDE_BOSS_HEAL);
        assertEquals(0.08d, AtlantisV2Rules.BLOOD_TIDE_ADD_HEAL);
        assertEquals(0.05d, AtlantisV2Rules.BLOOD_TIDE_CONSUME_HEAL);
        assertEquals(8_000L, AtlantisV2Rules.BLOOD_TIDE_HOMING_BALL_MS);
        assertEquals(12_000L, AtlantisV2Rules.BLOOD_TIDE_WATER_PILLAR_MS);
        assertEquals(16_000L, AtlantisV2Rules.BLOOD_TIDE_BLIZZARD_MS);
        assertEquals(24_000L, AtlantisV2Rules.BLOOD_TIDE_STORM_MS);
        assertEquals(0.05d, AtlantisV2Rules.FINAL_POINT_HEALTH);
        assertEquals(10, AtlantisV2Rules.FINAL_POINT_WAVE_COUNT);
        assertEquals(6_000L, AtlantisV2Rules.FINAL_POINT_HOMING_BALL_MS);
        assertEquals(18_000L, AtlantisV2Rules.FINAL_POINT_STORM_MS);
    }

    private static Skill skill(long id, String name) {
        Skill skill = new Skill();
        skill.setId(id);
        skill.setName(name);
        return skill;
    }

    private static final class TestMatchplayGuardianGame extends MatchplayGuardianGame {
        @Override
        protected MatchplayHandleable createHandler() {
            return null;
        }
    }
}
