package com.jftse.emulator.server.core.matchplay.guardian;

import com.jftse.emulator.server.core.matchplay.MatchplayGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.entities.database.model.battle.Skill;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Halloween Arena rules for Guardian mode on client map 6 that the phase script
 * (scripts/guardian-phase/6/1_halloween_arena.js) cannot enforce, because only the packet
 * handlers know whether a skill came from an equipped quick slot or from a court crystal.
 */
public final class HalloweenArenaRules {
    public static final int ARENA_MAP = 6;

    /** Pumpkin (material 264), the Halloween currency of the Pumpkin Hat and Mask recipes. */
    public static final int HALLOWEEN_COIN_ITEM_INDEX = 264;
    public static final String HALLOWEEN_COIN_CATEGORY = "MATERIAL";

    public static final String SEALED_NOTICE = "Equipped Heal/Shield are sealed in the Arena.";
    public static final String MATCH_START_NOTICE = "Halloween Arena: equipped Heal/Shield are sealed this match.";

    public static final int CRYSTAL_HEAL_INDEX = 0;
    public static final int CRYSTAL_REVIVE_INDEX = 4;
    public static final int CRYSTAL_SHIELD_INDEX = 9;
    public static final int REVIVE_CHANCE_PERCENT = 35;
    public static final int HEAL_CHANCE_PERCENT = 70;

    // SmallHeal, BigHeal, RangeHeal1-4, Cookie, Pie, RiceCake, FullHeal, Juice, Fast SmallHeal.
    private static final Set<Long> HEAL_SKILL_IDS = Set.of(1L, 2L, 16L, 17L, 18L, 19L, 23L, 24L, 30L, 31L, 33L, 39L);
    private static final long SHIELD_SKILL_ID = 10L;
    private static final long AREA_SHIELD_SKILL_ID = 20L;

    // The client reports a heal hit within about 150 ms of the use packet; Shield lasts 3 s (Skill.playTime).
    static final long SEALED_EFFECT_MS = 4000;

    private HalloweenArenaRules() {
    }

    public static boolean isArena(MatchplayGame game) {
        return game instanceof MatchplayGuardianGame guardianGame
                && guardianGame.getMap() != null
                && guardianGame.getMap().getMap() == ARENA_MAP;
    }

    /** The Arena crystal pool is a boss-stage rule; the guardian stage keeps the standard pool. */
    public static boolean isArenaBossStage(MatchplayGame game) {
        return isArena(game) && ((MatchplayGuardianGame) game).getBossBattleActive().get();
    }

    public static boolean isSealedWhenEquipped(Skill skill) {
        return skill != null && (isHeal(skill) || isShield(skill));
    }

    /**
     * Item_Quick.set indices of the stock client whose quick slots are emptied for the whole Arena match
     * (the client reads its quick slots only while the match loads).
     */
    public static boolean isSealedQuickItem(int itemIndex) {
        return switch (itemIndex) {
            case 1, 3, 4, 5, 7, 13, 19, 20, 21, 22, 23, 25, 26, 27, 28, 29, 50 -> true;
            default -> false;
        };
    }

    /**
     * Arena crystal outcome. Rolls are in [0, 100). A dead teammate keeps the standard 35% Revive
     * roll; every other outcome is 70% Heal and 30% Shield.
     */
    public static int crystalSkillIndex(boolean teammateDead, int reviveRoll, int poolRoll) {
        if (teammateDead && reviveRoll < REVIVE_CHANCE_PERCENT) {
            return CRYSTAL_REVIVE_INDEX;
        }
        return poolRoll < HEAL_CHANCE_PERCENT ? CRYSTAL_HEAL_INDEX : CRYSTAL_SHIELD_INDEX;
    }

    /** Per-match record of rejected equipped uses, kept in the game's extension state. */
    public static SealedEffects sealedEffects(MatchplayGame game) {
        return ((MatchplayGuardianGame) game).getExtensionState(SealedEffects.class, SealedEffects::new);
    }

    private static boolean isHeal(Skill skill) {
        return HEAL_SKILL_IDS.contains(skill.getId());
    }

    private static boolean isShield(Skill skill) {
        return skill.getId() == SHIELD_SKILL_ID || skill.getId() == AREA_SHIELD_SKILL_ID;
    }

    public static final class SealedEffects {
        private record HealSource(int casterPosition, long skillId) {
        }

        private final Map<HealSource, Long> healUntil = new ConcurrentHashMap<>();
        private final Map<Integer, Long> shieldUntil = new ConcurrentHashMap<>();
        private final Map<Integer, Long> crystalShieldUntil = new ConcurrentHashMap<>();

        /** Records a rejected equipped use so the hits the caster's client still reports are ignored. */
        public void seal(Skill skill, int casterPosition, long now) {
            long until = now + SEALED_EFFECT_MS;
            if (isHeal(skill)) {
                healUntil.put(new HealSource(casterPosition, skill.getId()), until);
            } else if (skill.getId() == AREA_SHIELD_SKILL_ID) {
                for (int position = 0; position < 4; position++) {
                    shieldUntil.put(position, until);
                }
            } else {
                shieldUntil.put(casterPosition, until);
            }
        }

        /** Crystal Heal only unseals its caster; a valid crystal Shield survives later rejected uses. */
        public void allowCrystal(Skill skill, int casterPosition, long now) {
            if (skill == null) {
                return;
            }
            if (isHeal(skill)) {
                healUntil.remove(new HealSource(casterPosition, skill.getId()));
            } else if (isShield(skill)) {
                long until = now + (long) (skill.getPlayTime() * 1000);
                if (skill.getId() == AREA_SHIELD_SKILL_ID) {
                    for (int position = 0; position < 4; position++) {
                        crystalShieldUntil.merge(position, until, Math::max);
                    }
                } else {
                    crystalShieldUntil.merge(casterPosition, until, Math::max);
                }
            }
        }

        public boolean isHealHitSealed(Skill skill, int attackerPosition, int reportingPosition, int targetPosition, long now) {
            if (skill == null || targetPosition > 3 || !isHeal(skill)) {
                return false;
            }
            // Self-heal hits use the God attacker (4); their source is the reporting player.
            int casterPosition = attackerPosition == 4 ? reportingPosition : attackerPosition;
            Long until = healUntil.get(new HealSource(casterPosition, skill.getId()));
            return until != null && now < until;
        }

        public boolean isShieldSealed(int targetPosition, long now) {
            Long until = shieldUntil.get(targetPosition);
            Long crystalUntil = crystalShieldUntil.get(targetPosition);
            return until != null && now < until && (crystalUntil == null || now >= crystalUntil);
        }
    }
}
