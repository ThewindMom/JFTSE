package com.jftse.emulator.server.core.matchplay;

import com.jftse.entities.database.model.battle.Skill;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FastMovementSpeedClockTest {
    private static final long NOW = 1_000_000L;

    @Test
    void shieldReEchoesRecordedFastMovement() {
        PlayerBattleState state = player();

        assertNull(FastMovementSpeedClock.onAppliedSkill(state, speed(46L, 20.0), NOW));
        assertEquals(NOW + 20_000L, state.getMovementSpeedExpiresAtMillis());
        assertEquals(46L, state.getMovementSpeedSkillId());

        assertEquals(46L, FastMovementSpeedClock.onAppliedSkill(
                state, skill(FastMovementSpeedClock.SHIELD_SKILL_ID), NOW + 1_000L));
        assertEquals(NOW + 20_000L, state.getMovementSpeedExpiresAtMillis());
    }

    @Test
    void shieldReEchoesRecordedRangeFastMovement() {
        PlayerBattleState state = player();

        assertNull(FastMovementSpeedClock.onAppliedSkill(state, speed(54L, 20.0), NOW));
        assertEquals(54L, FastMovementSpeedClock.onAppliedSkill(
                state, skill(FastMovementSpeedClock.RANGE_SHIELD_SKILL_ID), NOW + 1_000L));
    }

    @Test
    void secondApplyDoesNotExtendOrReplaceWhileLive() {
        PlayerBattleState state = player();
        FastMovementSpeedClock.onAppliedSkill(state, speed(46L, 20.0), NOW);

        assertNull(FastMovementSpeedClock.onAppliedSkill(state, speed(54L, 15.0), NOW + 5_000L));
        assertEquals(NOW + 20_000L, state.getMovementSpeedExpiresAtMillis());
        assertEquals(46L, state.getMovementSpeedSkillId());
    }

    @Test
    void shieldWithNoClockDoesNothing() {
        PlayerBattleState state = player();

        assertNull(FastMovementSpeedClock.onAppliedSkill(state, skill(FastMovementSpeedClock.SHIELD_SKILL_ID), NOW));
        assertNull(state.getMovementSpeedExpiresAtMillis());
        assertNull(state.getMovementSpeedSkillId());
    }

    @Test
    void shieldAfterExpiryDoesNotReEcho() {
        PlayerBattleState state = player();
        FastMovementSpeedClock.onAppliedSkill(state, speed(46L, 20.0), NOW);

        assertNull(FastMovementSpeedClock.onAppliedSkill(
                state, skill(FastMovementSpeedClock.SHIELD_SKILL_ID), NOW + 20_000L));
        assertNull(FastMovementSpeedClock.onAppliedSkill(state, speed(54L, 20.0), NOW + 20_000L));
        assertEquals(54L, state.getMovementSpeedSkillId());
        assertEquals(NOW + 40_000L, state.getMovementSpeedExpiresAtMillis());
    }

    private static PlayerBattleState player() {
        return new PlayerBattleState((short) 0, 1L, 100, 10, 10, 10, 10);
    }

    private static Skill speed(long id, double chantTime) {
        Skill skill = skill(id);
        skill.setChantTime(chantTime);
        skill.setDamage(0);
        return skill;
    }

    private static Skill skill(long id) {
        Skill skill = new Skill();
        skill.setId(id);
        skill.setDamage(0);
        skill.setChantTime(0.01);
        return skill;
    }
}
