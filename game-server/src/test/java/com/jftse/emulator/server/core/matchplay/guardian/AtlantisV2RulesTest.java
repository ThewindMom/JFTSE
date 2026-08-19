package com.jftse.emulator.server.core.matchplay.guardian;

import com.jftse.entities.database.model.battle.Skill;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AtlantisV2RulesTest {

    @Test
    void map10AlwaysResolvesToLiveGroupTen() {
        assertEquals("10", AtlantisV2Rules.resolvePhaseGroup(10));
        assertTrue(AtlantisV2Rules.isAtlantisMap(10));
        assertEquals("7", AtlantisV2Rules.resolvePhaseGroup(7));
        assertEquals("", AtlantisV2Rules.resolvePhaseGroup(null));
        assertFalse(AtlantisV2Rules.isAtlantisMap(11));
        assertNotEquals(AtlantisV2Rules.ARCHIVED_PHASE_GROUP, AtlantisV2Rules.resolvePhaseGroup(10));
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
    void herdingDropsGuardianHitsAndPadPlayersOnly() {
        assertTrue(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 10, false));
        assertTrue(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 11, true));
        assertTrue(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 0, true));
        assertFalse(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 0, false));
        assertFalse(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 3, false));
        assertTrue(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 3, true));
        assertFalse(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 4, false));
        assertFalse(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 4, true));
    }

    @Test
    void shieldAndHealStripsAreIndependent() {
        Skill shield = skill(10L, "Shield");
        Skill teamShield = skill(20L, "TeamShield");
        Skill heal = skill(1L, "Heal");
        Skill teamHeal = skill(16L, "TeamHeal");
        Skill wave = skill(28L, "SeaWave");

        assertTrue(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 0, shield));
        assertTrue(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, false, (short) 0, shield));
        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportHit(false, true, (short) 0, shield));
        assertTrue(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, false, (short) 2, teamShield));

        assertTrue(AtlantisV2Rules.shouldIgnorePlayerSupportHit(false, true, (short) 1, heal));
        assertTrue(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 1, teamHeal));
        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, false, (short) 1, heal));

        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 10, shield));
        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 0, wave));
        assertFalse(AtlantisV2Rules.shouldIgnorePlayerSupportHit(true, true, (short) 0, null));
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
        assertFalse(AtlantisV2Rules.isPlayerSupportSkill(skill(28L, "SeaWave")));
        assertFalse(AtlantisV2Rules.isPlayerSupportSkill(skill(12L, "SpiderMine")));
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
                "4_enrage.js", "1_green_tide.js", "3_storm_charge.js", "2_crab_window.js"));
        assertEquals(
                List.of("1_green_tide.js", "2_crab_window.js", "3_storm_charge.js", "4_enrage.js"),
                AtlantisV2Rules.sortPhaseScriptNames(original));
        assertEquals("4_enrage.js", original.get(0), "sort must copy, not mutate the shared list");
        assertTrue(AtlantisV2Rules.isV2PhaseScriptName("1_green_tide.js"));
        assertTrue(AtlantisV2Rules.isLegacyPhaseScriptName("1_echoes_of_the_deep.js"));
        assertFalse(AtlantisV2Rules.isV2PhaseScriptName("1_echoes_of_the_deep.js"));
    }

    @Test
    void waveSpawnIsConfirmedLtrCaveContract() {
        assertEquals((byte) 27, AtlantisV2Rules.SEA_WAVE_PACKET_ID);
        assertEquals(28L, AtlantisV2Rules.SEA_WAVE_SKILL_ID);
        assertEquals((byte) 4, AtlantisV2Rules.DUMMY_ATTACKER);
        assertEquals(-200f, AtlantisV2Rules.WAVE_X);
        assertEquals(0f, AtlantisV2Rules.WAVE_Z);
        assertEquals(0f, AtlantisV2Rules.WAVE_Y);
        assertEquals(5, AtlantisV2Rules.FIRST_VOLLEY_COUNT);
        assertEquals(10, AtlantisV2Rules.SECOND_VOLLEY_COUNT);
        assertEquals(20, AtlantisV2Rules.MEGAWAVE_COUNT);
        assertEquals(2_500L, AtlantisV2Rules.WAVE_GAP_MS);
        assertEquals(30_000L, AtlantisV2Rules.STRIP_GUARDIAN_MS);
        assertEquals(35_000L, AtlantisV2Rules.STRIP_PLAYER_MS);
        assertEquals(120_000L, AtlantisV2Rules.CRAB_WINDOW_MS);
        assertEquals(50_000L, AtlantisV2Rules.CHARGE_MS);
        assertTrue(AtlantisV2Rules.WAVE_GAP_MS * AtlantisV2Rules.FIRST_VOLLEY_COUNT > 5_000L,
                "first volley must last longer than 5s so Testmon is not melted instantly");
    }

    private static Skill skill(long id, String name) {
        Skill skill = new Skill();
        skill.setId(id);
        skill.setName(name);
        return skill;
    }
}
