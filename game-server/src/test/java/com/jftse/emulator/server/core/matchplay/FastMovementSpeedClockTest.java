package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayDealDamage;
import com.jftse.entities.database.model.battle.Skill;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
        assertNull(FastMovementSpeedClock.cancelDelayMillis(state, NOW));
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

    @Test
    void shieldAtFiveSecondsLeavesFifteenUntilCancel() {
        PlayerBattleState state = player();
        FastMovementSpeedClock.onAppliedSkill(state, speed(46L, 20.0), NOW);

        assertEquals(46L, FastMovementSpeedClock.onAppliedSkill(
                state, skill(FastMovementSpeedClock.SHIELD_SKILL_ID), NOW + 5_000L));
        assertEquals(15_000L, FastMovementSpeedClock.cancelDelayMillis(state, NOW + 5_000L));
    }

    @Test
    void shieldAtNineteenSecondsLeavesOneUntilCancel() {
        PlayerBattleState state = player();
        FastMovementSpeedClock.onAppliedSkill(state, speed(46L, 20.0), NOW);

        assertEquals(46L, FastMovementSpeedClock.onAppliedSkill(
                state, skill(FastMovementSpeedClock.SHIELD_SKILL_ID), NOW + 19_000L));
        assertEquals(1_000L, FastMovementSpeedClock.cancelDelayMillis(state, NOW + 19_000L));
    }

    @Test
    void shieldThenExpiryEmitsReEchoThenCancelDealDamage() {
        PlayerBattleState state = player();
        List<Byte> sentSkillIds = new ArrayList<>();

        FastMovementSpeedClock.onAppliedSkill(state, speed(46L, 20.0), NOW);
        FastMovementSpeedClock.noteOutgoingSkill(state, 46L);
        sentSkillIds.add(dealDamageSkillId((byte) 46));

        long shieldAt = NOW + 5_000L;
        Long echoSkillId = FastMovementSpeedClock.onAppliedSkill(
                state, skill(FastMovementSpeedClock.SHIELD_SKILL_ID), shieldAt);
        assertEquals(46L, echoSkillId);
        FastMovementSpeedClock.noteOutgoingSkill(state, echoSkillId);
        sentSkillIds.add(dealDamageSkillId(echoSkillId.byteValue()));

        long expiresAt = state.getMovementSpeedExpiresAtMillis();
        assertEquals(15_000L, FastMovementSpeedClock.cancelDelayMillis(state, shieldAt));
        assertEquals(0L, FastMovementSpeedClock.cancelWaitMillis(state, expiresAt, expiresAt));
        assertEquals(FastMovementSpeedClock.CANCEL_SKILL_ID, FastMovementSpeedClock.cancelSkillId(state));
        sentSkillIds.add(dealDamageSkillId((byte) FastMovementSpeedClock.CANCEL_SKILL_ID));

        assertEquals(List.of((byte) 46, (byte) 46, (byte) 4), sentSkillIds);
    }

    @Test
    void laterExtraStatSkipsCancel() {
        PlayerBattleState state = player();
        FastMovementSpeedClock.onAppliedSkill(state, speed(46L, 20.0), NOW);
        FastMovementSpeedClock.noteOutgoingSkill(state, 46L);
        FastMovementSpeedClock.onAppliedSkill(state, skill(FastMovementSpeedClock.SHIELD_SKILL_ID), NOW + 5_000L);
        FastMovementSpeedClock.noteOutgoingSkill(state, 46L);

        FastMovementSpeedClock.noteOutgoingSkill(state, 47L);

        long expiresAt = state.getMovementSpeedExpiresAtMillis();
        assertEquals(0L, FastMovementSpeedClock.cancelWaitMillis(state, expiresAt, expiresAt));
        assertNull(FastMovementSpeedClock.cancelSkillId(state));
    }

    @Test
    void cancelDoesNotKillALaterFastMovementClock() {
        PlayerBattleState state = player();
        FastMovementSpeedClock.onAppliedSkill(state, speed(46L, 20.0), NOW);
        FastMovementSpeedClock.noteOutgoingSkill(state, 46L);
        long firstExpiresAt = state.getMovementSpeedExpiresAtMillis();

        FastMovementSpeedClock.onAppliedSkill(state, speed(54L, 20.0), NOW + 20_000L);
        FastMovementSpeedClock.noteOutgoingSkill(state, 54L);

        assertNull(FastMovementSpeedClock.cancelWaitMillis(state, NOW + 20_000L, firstExpiresAt));
        assertEquals(FastMovementSpeedClock.CANCEL_SKILL_ID, FastMovementSpeedClock.cancelSkillId(state));
    }

    @Test
    void cancelWaitsIfTheFirstApplyClockIsStillLive() {
        PlayerBattleState state = player();
        FastMovementSpeedClock.onAppliedSkill(state, speed(46L, 20.0), NOW);
        long expiresAt = state.getMovementSpeedExpiresAtMillis();

        assertEquals(5_000L, FastMovementSpeedClock.cancelWaitMillis(state, NOW + 15_000L, expiresAt));
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

    private static byte dealDamageSkillId(byte skillId) {
        S2CMatchplayDealDamage packet = new S2CMatchplayDealDamage((short) 0, (short) 100, (short) 0, skillId, 0.0f, 0.0f);
        return packet.getData()[6];
    }
}
