package com.jftse.emulator.server.core.matchplay.guardian;

import com.jftse.entities.database.model.battle.Skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single source of truth for the live Atlantis (map 10) V2 fight.
 * Scripts and hit filters must follow these numbers; tests pin them.
 */
public final class AtlantisV2Rules {
    public static final String MAP_PATH = "10";
    public static final String PHASE_GROUP = "10";

    public static final byte SEA_WAVE_PACKET_ID = 27;
    public static final long SEA_WAVE_SKILL_ID = 28L;
    /** Atlantis lizard family: Dolizard, Penlizard, Belizard, Holizard, Silizard, Elizard. */
    public static final List<Long> LIZARD_FAMILY_IDS = List.of(43L, 44L, 45L, 46L, 47L, 48L);

    public static final int FIRST_VOLLEY_COUNT = 3;
    public static final int SECOND_VOLLEY_COUNT = 5;
    public static final int BOSS_FINAL_VOLLEY_COUNT = 5;

    public static final double ADD_PHASE_TRIGGER_HEALTH = 0.50d;
    public static final double BOSS_FINAL_PHASE_HEALTH = 0.90d;
    public static final double BOSS_NEXT_PHASE_HEALTH = 0.70d;
    public static final long BOSS_ATTACK_INTERVAL_MS = 4_000L;
    public static final long ADD_ATTACK_MIN_MS = 6_000L;
    public static final long ADD_ATTACK_MAX_MS = 12_000L;
    public static final long PHASE_ONE_WAVE_GAP_MS = 1_000L;
    public static final long PHASE_ONE_VOLLEY_REST_MIN_MS = 4_000L;
    public static final long PHASE_ONE_VOLLEY_REST_MAX_MS = 5_000L;
    public static final float PHASE_ONE_WAVE_X_MIN = -60f;
    public static final float PHASE_ONE_WAVE_X_MAX = 60f;
    /** Every depth is on the enemy court; Atlantis SeaWaves must never originate behind the players. */
    public static final float[] SEA_WAVE_DEPTHS = {50f, 75f, 100f};

    public static final double TWIN_TIDE_WAVE_DELTA = 0.10d;
    public static final double TWIN_TIDE_PILLAR_DELTA = 0.20d;
    public static final double TWIN_TIDE_BLIZZARD_DELTA = 0.30d;
    public static final double TWIN_TIDE_START_HEALTH = 0.60d;
    public static final double TWIN_TIDE_EXECUTE_HEALTH = 0.10d;
    public static final double TWIN_TIDE_REVIVE_HEALTH = 0.30d;
    public static final int TWIN_TIDE_WAVE_COUNT = 3;
    public static final int TWIN_TIDE_PILLAR_WAVE_COUNT = 5;
    public static final int TWIN_TIDE_BLIZZARD_WAVE_COUNT = 8;
    public static final long TWIN_TIDE_HOMING_BALL_MS = 10_000L;
    public static final long TWIN_TIDE_KILL_WINDOW_MS = 10_000L;
    public static final long TWIN_TIDE_VOLLEY_INTERVAL_MS = 12_000L;
    public static final long TWIN_TIDE_WATER_PILLAR_MS = 10_000L;
    public static final long TWIN_TIDE_BLIZZARD_MS = 18_000L;
    public static final double RISING_TIDE_END_HEALTH = 0.25d;
    public static final int RISING_TIDE_MAX_LEVEL = 3;
    public static final long RISING_TIDE_HOMING_BALL_MS = 10_000L;
    public static final long RISING_TIDE_WATER_PILLAR_MS = 15_000L;
    public static final long RISING_TIDE_BLIZZARD_MS = 20_000L;
    public static final long RISING_TIDE_STORM_MS = 30_000L;
    public static final long RISING_TIDE_MIN_ATTACK_GAP_MS = 2_000L;
    public static final int TIDAL_CONVERGENCE_RADIUS = 25;
    public static final long TIDAL_CONVERGENCE_WARNING_MS = 3_000L;
    public static final long TIDAL_CONVERGENCE_SCAN_MS = 5_000L;
    public static final long TWIN_TIDE_CONVERGENCE_COOLDOWN_MS = 18_000L;
    public static final long RISING_TIDE_CONVERGENCE_COOLDOWN_MS = 15_000L;
    public static final long DROWNED_CROWN_CONVERGENCE_COOLDOWN_MS = 12_000L;
    public static final long DROWNED_CROWN_CALM_MS = 5_000L;
    public static final double DROWNED_CROWN_START_HEALTH = 0.35d;
    public static final double DROWNED_CROWN_ADD_HEALTH = 0.30d;
    public static final double BLOOD_TIDE_BOSS_HEAL = 0.02d;
    public static final double BLOOD_TIDE_ADD_HEAL = 0.08d;
    public static final double BLOOD_TIDE_CONSUME_HEAL = 0.05d;
    public static final long BLOOD_TIDE_HOMING_BALL_MS = 8_000L;
    public static final long BLOOD_TIDE_WATER_PILLAR_MS = 12_000L;
    public static final long BLOOD_TIDE_BLIZZARD_MS = 16_000L;
    public static final long BLOOD_TIDE_STORM_MS = 24_000L;
    public static final long BLOOD_TIDE_ADD_ATTACK_MIN_MS = 8_000L;
    public static final long BLOOD_TIDE_ADD_ATTACK_MAX_MS = 12_000L;
    public static final long BLOOD_TIDE_MIN_ATTACK_GAP_MS = 1_500L;
    public static final double FINAL_POINT_HEALTH = 0.05d;
    public static final long FINAL_POINT_SILENCE_MS = 3_000L;
    public static final int FINAL_POINT_WAVE_COUNT = 10;
    public static final long FINAL_POINT_HOMING_BALL_MS = 6_000L;
    public static final long FINAL_POINT_WATER_PILLAR_MS = 10_000L;
    public static final long FINAL_POINT_BLIZZARD_MS = 12_000L;
    public static final long FINAL_POINT_STORM_MS = 18_000L;

    public static final double POST_REVIVE_HEAL_MULTIPLIER = 0.20d;

    public static final long HOMING_BALL_SKILL_ID = 6L;
    public static final long BLIZZARD_SKILL_ID = 13L;
    public static final long WATER_PILLAR_SKILL_ID = 61L;
    public static final long STORM_SKILL_ID = 62L;
    public static final long REBIRTH_SKILL_ID = 29L;
    public static final long BLOOD_TIDE_HEAL_SKILL_ID = 31L;

    public static final byte PLAYER_SLOT_MAX = 3;
    public static final byte GUARDIAN_SLOT_MIN = 10;
    public static final byte PLAYER_AREA_SUPPORT_SYNTHETIC_ATTACKER = 4;

    public static final long[] PLAYER_SHIELD_SKILL_IDS = {10L, 20L};
    public static final long[] PLAYER_HEAL_SKILL_IDS = {1L, 2L, 16L, 17L, 18L, 19L, 31L, 39L};
    public static final long[] PLAYER_AREA_SUPPORT_SKILL_IDS = {16L, 17L, 18L, 19L, 20L};
    public static final long PLAYER_AREA_SUPPORT_HIT_WINDOW_MS = 2_000L;

    private static final AtomicReference<Long> NOW_OVERRIDE = new AtomicReference<>();

    private AtlantisV2Rules() {
    }

    public static long now() {
        Long override = NOW_OVERRIDE.get();
        return override != null ? override : System.currentTimeMillis();
    }

    public static void setNowForTest(long nowMs) {
        NOW_OVERRIDE.set(nowMs);
    }

    public static void clearNowForTest() {
        NOW_OVERRIDE.set(null);
    }

    public static boolean isAtlantisMap(Integer mapId) {
        return mapId != null && mapId == 10;
    }

    /** Guardian pads remain available on other maps, but Atlantis has no pad mechanic. */
    public static boolean shouldSchedulePads(Integer mapId) {
        return mapId != null && !isAtlantisMap(mapId);
    }

    public static boolean shouldUseLizardFamilyAids(Integer mapId, boolean advancedBossMode) {
        return advancedBossMode && isAtlantisMap(mapId);
    }

    /** Atlantis attacks are emitted exclusively by its phase scripts. */
    public static boolean shouldSuppressNativeGuardianAttacks(Integer mapId, boolean advancedBossMode) {
        return advancedBossMode && isAtlantisMap(mapId);
    }

    public static List<Long> selectLizardAidIds(Random random) {
        int firstIndex = random.nextInt(LIZARD_FAMILY_IDS.size());
        int secondIndex = random.nextInt(LIZARD_FAMILY_IDS.size() - 1);
        if (secondIndex >= firstIndex) {
            secondIndex++;
        }
        return List.of(LIZARD_FAMILY_IDS.get(firstIndex), LIZARD_FAMILY_IDS.get(secondIndex));
    }

    /**
     * Map 10 always loads {@code guardian-phase/10/}, the only Atlantis fight.
     * Other maps keep their own group folder.
     */
    public static String resolvePhaseGroup(Integer mapId) {
        if (isAtlantisMap(mapId)) {
            return PHASE_GROUP;
        }
        return mapId == null ? "" : Integer.toString(mapId);
    }

    public static boolean isSeaWaveHit(Skill skill, byte packetSkillId) {
        if (packetSkillId == SEA_WAVE_PACKET_ID || packetSkillId == (byte) SEA_WAVE_SKILL_ID) {
            return true;
        }
        if (skill == null) {
            return false;
        }
        if (skill.getId() != null && skill.getId() == SEA_WAVE_SKILL_ID) {
            return true;
        }
        return skill.getName() != null && "SeaWave".equalsIgnoreCase(skill.getName());
    }

    public static boolean isGuardianSlot(short targetPosition) {
        return targetPosition > GUARDIAN_SLOT_MIN - 1;
    }

    public static boolean isPlayerSlot(short targetPosition) {
        return targetPosition >= 0 && targetPosition <= PLAYER_SLOT_MAX;
    }

    /**
     * Drop a SeaWave hit when the target is a guardian (herding is players-only)
     * or a player standing in a visible pad circle on a map that supports pads.
     */
    public static boolean shouldIgnoreSeaWaveHit(short targetPosition, boolean playerInVisiblePad) {
        if (isGuardianSlot(targetPosition)) {
            return true;
        }
        return isPlayerSlot(targetPosition) && playerInVisiblePad;
    }

    public static boolean isPlayerShieldSkill(Skill skill) {
        return skillIdIn(skill, PLAYER_SHIELD_SKILL_IDS);
    }

    public static boolean isPlayerHealSkill(Skill skill) {
        return skillIdIn(skill, PLAYER_HEAL_SKILL_IDS);
    }

    public static boolean isPlayerSupportSkill(Skill skill) {
        return isPlayerShieldSkill(skill) || isPlayerHealSkill(skill);
    }

    public static boolean isPlayerAreaSupportSkill(Skill skill) {
        return skillIdIn(skill, PLAYER_AREA_SUPPORT_SKILL_IDS);
    }

    public static boolean isAllyAreaSupportHitReport(
            short reportingPlayerPosition, int exemptPosition,
            short attackerPosition, short targetPosition) {
        return isPlayerSlot(reportingPlayerPosition)
                && reportingPlayerPosition != exemptPosition
                && attackerPosition == PLAYER_AREA_SUPPORT_SYNTHETIC_ATTACKER
                && targetPosition == reportingPlayerPosition;
    }

    public static boolean shouldIgnorePlayerSupportUse(
            boolean shieldDisabled, boolean healDisabled, short actorPosition,
            int exemptPosition, Skill skill) {
        if (skill == null || !isPlayerSupportSkill(skill)) {
            return false;
        }
        if (exemptPosition >= 0 && actorPosition == exemptPosition) {
            return false;
        }
        if (shieldDisabled && isPlayerShieldSkill(skill)) {
            return true;
        }
        return healDisabled && isPlayerHealSkill(skill);
    }

    public static boolean shouldIgnorePlayerSupportHit(
            boolean shieldDisabled, boolean healDisabled, short actorPosition,
            int exemptPosition, short targetPosition, Skill skill,
            boolean authorizedAreaSupportHit) {
        if (!isPlayerSlot(targetPosition) || skill == null) {
            return false;
        }
        if (exemptPosition >= 0 && actorPosition == exemptPosition) {
            return false;
        }
        if (authorizedAreaSupportHit && isPlayerAreaSupportSkill(skill)) {
            return false;
        }
        if (shieldDisabled && isPlayerShieldSkill(skill)) {
            return true;
        }
        return healDisabled && isPlayerHealSkill(skill);
    }

    public static int applyHeal(int currentHealth, int maxHealth, int healAmount, double multiplier) {
        if (healAmount <= 0) {
            return currentHealth;
        }
        long applied = Math.round(healAmount * multiplier);
        long next = (long) currentHealth + applied;
        if (next > maxHealth) {
            next = maxHealth;
        }
        return (int) next;
    }

    public static List<String> sortPhaseScriptNames(List<String> names) {
        List<String> copy = new ArrayList<>(names);
        copy.sort(Comparator.naturalOrder());
        return copy;
    }

    public static boolean isV2PhaseScriptName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.equals("1_green_tide.js")
                || lower.equals("2_twin_tides.js")
                || lower.equals("3_rising_tide.js")
                || lower.equals("4_enrage.js");
    }

    public static boolean isLegacyPhaseScriptName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.equals("1_echoes_of_the_deep.js")
                || lower.equals("2_maelstrom_unleashed.js")
                || lower.equals("3_leviathans_will.js")
                || lower.equals("4_abyssal_reckoning.js");
    }

    private static boolean skillIdIn(Skill skill, long[] ids) {
        if (skill == null || skill.getId() == null) {
            return false;
        }
        long id = skill.getId();
        for (long expected : ids) {
            if (id == expected) {
                return true;
            }
        }
        return false;
    }
}
