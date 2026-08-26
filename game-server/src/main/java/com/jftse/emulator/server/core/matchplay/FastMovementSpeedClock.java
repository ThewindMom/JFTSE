package com.jftse.emulator.server.core.matchplay;

import com.jftse.entities.database.model.battle.Skill;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;

import java.util.Objects;

public final class FastMovementSpeedClock {
    public static final long FAST_MOVEMENT_SKILL_ID = 46L;
    public static final long RANGE_FAST_MOVEMENT_SKILL_ID = 54L;
    public static final long SHIELD_SKILL_ID = 10L;
    public static final long RANGE_SHIELD_SKILL_ID = 20L;
    // SmallMeteo. DealDamage lists it as Nothing. Not 0 (ball-loss), 3 (DoT ticks), 10 or 20 (Shield), or 12 (SpiderMine).
    public static final long CANCEL_SKILL_ID = 4L;

    private FastMovementSpeedClock() {
    }

    public static boolean isFastMovement(Skill skill) {
        return skill != null && isFastMovementSkillId(skill.getId());
    }

    public static boolean isFastMovementSkillId(Long skillId) {
        return skillId != null
                && (skillId == FAST_MOVEMENT_SKILL_ID || skillId == RANGE_FAST_MOVEMENT_SKILL_ID);
    }

    public static boolean isShieldSkill(Skill skill) {
        return skill != null && skill.getId() != null
                && (skill.getId() == SHIELD_SKILL_ID || skill.getId() == RANGE_SHIELD_SKILL_ID);
    }

    public static boolean isActive(PlayerBattleState state, long nowMillis) {
        return state != null
                && state.getMovementSpeedExpiresAtMillis() != null
                && nowMillis < state.getMovementSpeedExpiresAtMillis();
    }

    public static void noteOutgoingSkill(PlayerBattleState state, Long skillId) {
        if (state == null || skillId == null) {
            return;
        }
        state.setLastOutgoingSkillId(skillId);
    }

    public static Long onAppliedSkill(PlayerBattleState state, Skill appliedSkill, long nowMillis) {
        if (state == null || appliedSkill == null) {
            return null;
        }

        if (isFastMovement(appliedSkill)) {
            if (!isActive(state, nowMillis)) {
                double chantSeconds = appliedSkill.getChantTime() != null ? appliedSkill.getChantTime() : 20.0;
                state.setMovementSpeedExpiresAtMillis(nowMillis + Math.round(chantSeconds * 1000.0));
                state.setMovementSpeedSkillId(appliedSkill.getId());
            }
            return null;
        }

        if (isShieldSkill(appliedSkill) && isActive(state, nowMillis) && state.getMovementSpeedSkillId() != null) {
            return state.getMovementSpeedSkillId();
        }

        return null;
    }

    public static Long cancelDelayMillis(PlayerBattleState state, long nowMillis) {
        if (!isActive(state, nowMillis)) {
            return null;
        }
        return state.getMovementSpeedExpiresAtMillis() - nowMillis;
    }

    public static Long cancelWaitMillis(PlayerBattleState state, long nowMillis, long scheduledExpiresAtMillis) {
        if (state == null || !Objects.equals(state.getMovementSpeedExpiresAtMillis(), scheduledExpiresAtMillis)) {
            return null;
        }
        if (nowMillis < scheduledExpiresAtMillis) {
            return scheduledExpiresAtMillis - nowMillis;
        }
        return 0L;
    }

    public static Long cancelSkillId(PlayerBattleState state) {
        if (state == null || !isFastMovementSkillId(state.getLastOutgoingSkillId())) {
            return null;
        }
        return CANCEL_SKILL_ID;
    }
}
