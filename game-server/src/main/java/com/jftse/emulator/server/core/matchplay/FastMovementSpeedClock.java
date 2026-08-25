package com.jftse.emulator.server.core.matchplay;

import com.jftse.entities.database.model.battle.Skill;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;

public final class FastMovementSpeedClock {
    public static final long FAST_MOVEMENT_SKILL_ID = 46L;
    public static final long RANGE_FAST_MOVEMENT_SKILL_ID = 54L;
    public static final long SHIELD_SKILL_ID = 10L;
    public static final long RANGE_SHIELD_SKILL_ID = 20L;

    private FastMovementSpeedClock() {
    }

    public static boolean isFastMovement(Skill skill) {
        return skill != null && skill.getId() != null
                && (skill.getId() == FAST_MOVEMENT_SKILL_ID || skill.getId() == RANGE_FAST_MOVEMENT_SKILL_ID);
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
}
