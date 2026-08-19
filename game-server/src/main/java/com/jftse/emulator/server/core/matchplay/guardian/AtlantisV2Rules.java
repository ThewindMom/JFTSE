package com.jftse.emulator.server.core.matchplay.guardian;

import com.jftse.entities.database.model.battle.Skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    public static final byte DUMMY_ATTACKER = 4;
    public static final float WAVE_X = -200f;
    public static final float WAVE_Z = 0f;
    public static final float WAVE_Y = 0f;

    public static final int FIRST_VOLLEY_COUNT = 5;
    public static final int SECOND_VOLLEY_COUNT = 10;
    public static final int BLIZZARD_VOLLEY_COUNT = 5;
    public static final int MEGAWAVE_COUNT = 20;

    public static final long STRIP_GUARDIAN_MS = 30_000L;
    public static final long STRIP_PLAYER_MS = 35_000L;
    public static final long FIRST_VOLLEY_MS = 40_000L;
    public static final long SECOND_VOLLEY_MS = 55_000L;
    public static final long RESTORE_MS = 85_000L;
    public static final long WAVE_GAP_MS = 2_500L;
    public static final long MEGAWAVE_GAP_MS = 1_200L;
    public static final long CRAB_WINDOW_MS = 120_000L;
    public static final long WAVE_ONLY_MS = 30_000L;
    public static final long CRAB_BLIZZARD_MS = 18_000L;
    public static final long CRAB_WAVE_MS = 8_000L;
    public static final long STORM_DWELL_MS = 30_000L;
    public static final long INFERNO_INTERVAL_MS = 5_000L;
    public static final long CHARGE_MS = 50_000L;
    public static final long STUN_MS = 5_000L;
    public static final long ENRAGE_WAVE_MS = 6_000L;
    public static final long ENRAGE_BLIZZARD_MS = 14_000L;

    public static final double POST_REVIVE_HEAL_MULTIPLIER = 0.20d;

    public static final long BLIZZARD_SKILL_ID = 13L;
    public static final long STORM_SKILL_ID = 62L;
    public static final long INFERNO_SKILL_ID = 35L;
    public static final long REBIRTH_SKILL_ID = 29L;

    public static final byte PLAYER_SLOT_MAX = 3;
    public static final byte GUARDIAN_SLOT_MIN = 10;

    public static final long[] PLAYER_SHIELD_SKILL_IDS = {10L, 20L};
    public static final long[] PLAYER_HEAL_SKILL_IDS = {1L, 2L, 16L, 17L, 18L, 19L, 31L, 39L};

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
     * or a player standing in a visible pad circle.
     */
    public static boolean shouldIgnoreSeaWaveHit(short targetPosition, boolean playerInVisiblePad) {
        if (!isSeaWaveRelevantTarget(targetPosition)) {
            return false;
        }
        if (isGuardianSlot(targetPosition)) {
            return true;
        }
        return isPlayerSlot(targetPosition) && playerInVisiblePad;
    }

    private static boolean isSeaWaveRelevantTarget(short targetPosition) {
        return isPlayerSlot(targetPosition) || isGuardianSlot(targetPosition);
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

    public static boolean shouldIgnorePlayerSupportHit(
            boolean shieldDisabled, boolean healDisabled, short targetPosition, Skill skill) {
        if (!isPlayerSlot(targetPosition) || skill == null) {
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
                || lower.equals("2_crab_window.js")
                || lower.equals("3_storm_charge.js")
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
