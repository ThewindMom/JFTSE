package com.jftse.emulator.server.core.matchplay.guardian;

import com.jftse.emulator.common.utilities.BitKit;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.event.Fireable;
import com.jftse.emulator.server.core.matchplay.event.RunnableEvent;
import com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayDealDamage;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill;
import com.jftse.entities.database.model.battle.BossGuardian;
import com.jftse.entities.database.model.battle.Guardian;
import com.jftse.entities.database.model.battle.Skill;
import com.jftse.entities.database.model.battle.Skill2Guardians;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import com.jftse.server.core.protocol.Packet;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class AtlantisV2ScriptHarnessTest {
    private static final Path LIVE = Path.of("src/main/resources/scripts/guardian-phase/10");

    @AfterEach
    void resetClock() {
        AtlantisV2Rules.clearNowForTest();
    }

    @Test
    void greenTideRunsHpDrivenLizardAndBossRages() throws IOException {
        Harness harness = load("1_green_tide.js");
        harness.phase.invokeMember("start");

        assertEquals("Green Tide", harness.phase.invokeMember("getPhaseName").asString());
        assertEquals("OPENING", harness.phase.invokeMember("getState").asString());
        assertEquals(-1L, harness.phase.invokeMember("getGuardianAttackLoopTime", harness.boss).asLong());
        assertTrue(harness.boss.getSkills().isEmpty());
        assertTrue(harness.left.getSkills().isEmpty());
        assertTrue(harness.right.getSkills().isEmpty());
        assertFalse(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);
        assertEquals(harness.boss.getCurrentHealth().get(), harness.phase.invokeMember(
                "onDealDamage", 0, 10, 100, false, false, null).asInt(),
                "Royal Lizard must ignore direct damage while either add lives");
        assertEquals(harness.boss.getCurrentHealth().get(), harness.phase.invokeMember(
                "onDealDamageOnBallLoss", 0, 10, false).asInt(),
                "Royal Lizard must ignore ball-loss damage while either add lives");

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.BOSS_ATTACK_INTERVAL_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertTrue(harness.useSkills.isEmpty(), "Royal Lizard must not attack during the opening");

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.ADD_ATTACK_MAX_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(2, harness.useSkills.size(), "only the two adds attack during the opening");

        int skillsBeforeFirstVolley = harness.useSkills.size();
        harness.game.players.getFirst().setShieldActive(true);
        harness.left.getCurrentHealth().set(harness.left.getMaxHealth() / 2);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals("THREE_WAVE_RAGE", harness.phase.invokeMember("getState").asString());
        assertTrue(harness.game.shieldDisabled);
        assertTrue(harness.game.healDisabled);
        assertFalse(harness.game.players.getFirst().isShieldActive(),
                "entering the suppression window must strip an already-active shield");
        assertEquals(AtlantisV2Rules.FIRST_VOLLEY_COUNT + 1, harness.eventHandler.pending.size(),
                "three waves plus the randomized-rest completion event");

        harness.eventHandler.runAll();
        List<UseSkillView> firstVolley = harness.useSkills.subList(skillsBeforeFirstVolley, harness.useSkills.size()).stream()
                .filter(packet -> packet.skillId() == AtlantisV2Rules.SEA_WAVE_PACKET_ID)
                .toList();
        assertEquals(AtlantisV2Rules.FIRST_VOLLEY_COUNT, firstVolley.size());
        for (UseSkillView wave : firstVolley) {
            assertEquals((byte) 4, wave.attacker(), "SeaWave must use the guardian-independent dummy slot");
            assertEquals((byte) 4, wave.target());
            assertTrue(wave.x() >= AtlantisV2Rules.PHASE_ONE_WAVE_X_MIN);
            assertTrue(wave.x() <= AtlantisV2Rules.PHASE_ONE_WAVE_X_MAX);
            assertEquals(0f, wave.z(), 0.001f, "SeaWave height stays on the court");
            assertTrue(isSafeWaveDepth(wave.y()),
                    "every wave must choose a randomized enemy-court depth");
        }

        int skillsBeforeSecondVolley = harness.useSkills.size();
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        harness.eventHandler.runAll();
        List<UseSkillView> secondVolley = harness.useSkills.subList(skillsBeforeSecondVolley, harness.useSkills.size()).stream()
                .filter(packet -> packet.skillId() == AtlantisV2Rules.SEA_WAVE_PACKET_ID)
                .toList();
        assertEquals(AtlantisV2Rules.FIRST_VOLLEY_COUNT, secondVolley.size());
        for (UseSkillView wave : secondVolley) {
            assertTrue(isSafeWaveDepth(wave.y()));
        }

        harness.left.getCurrentHealth().set(0);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals("FIVE_WAVE_RAGE", harness.phase.invokeMember("getState").asString());
        assertEquals(0, harness.game.supportExemptPosition);
        assertTrue(harness.announcements.stream().anyMatch(text ->
                text.contains("TestPlayer bears the Tide's Grace during the Five-Wave Rage")));
        assertEquals(AtlantisV2Rules.SECOND_VOLLEY_COUNT + 1, harness.eventHandler.pending.size());

        int skillsAfterSelection = harness.useSkills.size();
        AtlantisV2Rules.setNowForTest(AtlantisV2Rules.now() + 3_000L);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(skillsAfterSelection, harness.useSkills.size(),
                "the support announcement must not create a visual marker packet");

        harness.eventHandler.runAll();
        List<UseSkillView> fiveWaveVolley = harness.useSkills.subList(skillsAfterSelection, harness.useSkills.size()).stream()
                .filter(packet -> packet.skillId() == AtlantisV2Rules.SEA_WAVE_PACKET_ID)
                .toList();
        assertEquals(AtlantisV2Rules.SECOND_VOLLEY_COUNT, fiveWaveVolley.size());
        assertTrue(fiveWaveVolley.stream().allMatch(packet -> isSafeWaveDepth(packet.y())),
                "after one add dies every wave still randomizes within safe enemy-court depths");
        harness.right.getCurrentHealth().set(0);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals("BOSS_RELEASED", harness.phase.invokeMember("getState").asString());
        assertTrue(harness.game.shieldDisabled);
        assertTrue(harness.game.healDisabled);
        assertEquals(0, harness.game.supportExemptPosition);
        assertTrue(harness.announcements.stream().anyMatch(text ->
                text.contains("TestPlayer bears the Tide's Grace during the Crown's first reckoning")));
        assertEquals(0, harness.phase.invokeMember(
                "onDealDamage", 0, 10, 100, false, false, null).asInt(),
                "Royal Lizard must become vulnerable when both adds are dead");

        int skillsBeforeReleasedAttack = harness.useSkills.size();
        AtlantisV2Rules.setNowForTest(AtlantisV2Rules.now() + AtlantisV2Rules.BOSS_ATTACK_INTERVAL_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(skillsBeforeReleasedAttack + 1, harness.useSkills.size(),
                "only the four-second Royal Lizard attack is emitted");

        harness.boss.getCurrentHealth().set((int) (harness.boss.getMaxHealth()
                * AtlantisV2Rules.BOSS_FINAL_PHASE_HEALTH));
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals("BOSS_FINAL", harness.phase.invokeMember("getState").asString());
        assertTrue(harness.game.shieldDisabled);
        assertTrue(harness.game.healDisabled);
        assertEquals(0, harness.game.supportExemptPosition,
                "the Phase 1.3 support player keeps the exemption through the 90% section");
        assertEquals(AtlantisV2Rules.BOSS_FINAL_VOLLEY_COUNT + 1, harness.eventHandler.pending.size());

        int skillsBeforeFinalBossAttack = harness.useSkills.size();
        AtlantisV2Rules.setNowForTest(AtlantisV2Rules.now() + AtlantisV2Rules.BOSS_ATTACK_INTERVAL_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(skillsBeforeFinalBossAttack + 1, harness.useSkills.size());
        byte finalSkill = harness.useSkills.getLast().skillId();
        assertEquals((byte) (AtlantisV2Rules.HOMING_BALL_SKILL_ID - 1), finalSkill,
                "90%-70% Royal Lizard may only use HomingBall");

        int wavesBeforeExit = (int) harness.useSkills.stream()
                .filter(packet -> packet.skillId() == AtlantisV2Rules.SEA_WAVE_PACKET_ID)
                .count();
        harness.boss.getCurrentHealth().set((int) (harness.boss.getMaxHealth()
                * AtlantisV2Rules.BOSS_NEXT_PHASE_HEALTH));
        assertEquals(PhaseUpdateResult.NEXT_PHASE, result(harness));
        harness.eventHandler.runAll();
        assertEquals(wavesBeforeExit, harness.useSkills.stream()
                .filter(packet -> packet.skillId() == AtlantisV2Rules.SEA_WAVE_PACKET_ID)
                .count(), "queued Phase 1 waves must be cancelled at 70% boss HP");

        harness.phase.invokeMember("end");
        assertTrue(harness.game.shieldDisabled, "Phase 2 receives the existing disabled-support state");
        assertTrue(harness.game.healDisabled);
        assertEquals(-1, harness.game.supportExemptPosition);
    }

    @Test
    void twinTidesBalancesAddsEscalatesHazardsAndRequiresSynchronizedKill() throws IOException {
        Harness harness = load("2_twin_tides.js");
        harness.left.getCurrentHealth().set(0);
        harness.right.getCurrentHealth().set(0);
        harness.phase.invokeMember("start");
        assertFalse(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);
        assertEquals(20, harness.phase.invokeMember("onHeal", 0, 100, false).asInt());
        assertEquals(1800, harness.left.getCurrentHealth().get());
        assertEquals(1800, harness.right.getCurrentHealth().get());

        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals("Twin Tides", harness.phase.invokeMember("getPhaseName").asString());
        assertEquals(2, harness.dealDamage.size(), "both attendants must visibly return at 60% health");
        List<UseSkillView> rebirths = harness.useSkills.stream()
                .filter(packet -> packet.skillId() == (byte) (AtlantisV2Rules.REBIRTH_SKILL_ID - 1))
                .toList();
        assertEquals(2, rebirths.size(), "each attendant must receive its own native rebirth cast");
        assertTrue(rebirths.stream().allMatch(packet -> packet.attacker() == 10));
        assertTrue(rebirths.stream().anyMatch(packet -> packet.target() == 11),
                "the left attendant must revive in its own battle slot");
        assertTrue(rebirths.stream().anyMatch(packet -> packet.target() == 12),
                "the right attendant must revive in its own battle slot");
        assertTrue(harness.announcements.stream().anyMatch(text -> text.contains("Keep their health within 10%")));
        int skillsBeforeHomingBall = harness.useSkills.size();
        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.BOSS_ATTACK_INTERVAL_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(skillsBeforeHomingBall, harness.useSkills.size(),
                "Phase 2 Homing Ball must not retain the Phase 1 four-second interval");
        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.TWIN_TIDE_HOMING_BALL_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(skillsBeforeHomingBall + 1, harness.useSkills.size(),
                "Phase 2 Homing Ball targets every living fixture player after ten seconds");
        assertEquals((byte) (AtlantisV2Rules.HOMING_BALL_SKILL_ID - 1),
                harness.useSkills.getLast().skillId());
        assertEquals(harness.boss.getCurrentHealth().get(), harness.phase.invokeMember(
                "onDealDamage", 0, 10, 100, false, false, null).asInt(),
                "Royal Lizard remains immune while either Twin Tide lives");

        harness.left.getCurrentHealth().set(1700);
        harness.right.getCurrentHealth().set(1800);
        assertNotEquals(harness.left.getCurrentHealth().get(), harness.phase.invokeMember(
                "onDealDamage", 0, 11, 100, false, false, null).asInt(),
                "a sub-10% difference must not return unchanged health as a Guard result");

        harness.left.getCurrentHealth().set(2100);
        harness.right.getCurrentHealth().set(3000);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(3, harness.phase.invokeMember("getSeverity").asInt());
        assertEquals(harness.left.getCurrentHealth().get(), harness.phase.invokeMember(
                "onDealDamage", 0, 11, 100, false, false, null).asInt(),
                "the lower-health attendant is protected until the other is brought down");
        assertEquals(300, harness.phase.invokeMember(
                "onDealDamage", 0, 12, 100, false, false, null).asInt(),
                "the higher-health attendant remains vulnerable but cannot die before both reach 10%");
        assertEquals(AtlantisV2Rules.TWIN_TIDE_BLIZZARD_WAVE_COUNT + 1,
                harness.eventHandler.pending.size(), "critical imbalance queues eight waves plus completion");
        harness.eventHandler.runAll();
        assertEquals(AtlantisV2Rules.TWIN_TIDE_BLIZZARD_WAVE_COUNT, harness.useSkills.stream()
                .filter(packet -> packet.skillId() == AtlantisV2Rules.SEA_WAVE_PACKET_ID)
                .count());
        assertTrue(harness.useSkills.stream()
                .filter(packet -> packet.skillId() == AtlantisV2Rules.SEA_WAVE_PACKET_ID)
                .allMatch(packet -> isSafeWaveDepth(packet.y())),
                "Twin Tide waves randomize X and safe enemy-court depth");

        harness.left.getCurrentHealth().set(300);
        harness.right.getCurrentHealth().set(300);
        assertEquals(0, harness.phase.invokeMember(
                "onDealDamage", 0, 11, 100, false, false, null).asInt(),
                "both attendants become vulnerable together at 10% health");

        AtlantisV2Rules.setNowForTest(harness.startedAt);
        harness.left.getCurrentHealth().set(0);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(harness.startedAt, harness.phase.invokeMember("getFirstDeathAt").asLong());
        assertTrue(harness.announcements.stream().anyMatch(text -> text.contains("within 10 seconds")));

        long leftRebirthsBeforeRetry = harness.useSkills.stream()
                .filter(packet -> packet.skillId() == (byte) (AtlantisV2Rules.REBIRTH_SKILL_ID - 1)
                        && packet.target() == 11)
                .count();
        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.TWIN_TIDE_KILL_WINDOW_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(900, harness.left.getCurrentHealth().get(), "failed execution revives the dead add at 30%");
        assertTrue(harness.dealDamage.stream().anyMatch(packet -> packet.target() == 11 && packet.hp() == 900));
        assertEquals(leftRebirthsBeforeRetry + 1, harness.useSkills.stream()
                .filter(packet -> packet.skillId() == (byte) (AtlantisV2Rules.REBIRTH_SKILL_ID - 1)
                        && packet.target() == 11)
                .count(), "a failed execution must rebirth the dead attendant in its original slot");

        harness.left.getCurrentHealth().set(300);
        harness.right.getCurrentHealth().set(300);
        harness.left.getCurrentHealth().set(0);
        AtlantisV2Rules.setNowForTest(AtlantisV2Rules.now() + 1);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        AtlantisV2Rules.setNowForTest(AtlantisV2Rules.now() + AtlantisV2Rules.TWIN_TIDE_KILL_WINDOW_MS - 1);
        harness.right.getCurrentHealth().set(0);
        assertEquals(PhaseUpdateResult.NEXT_PHASE, result(harness));
        harness.phase.invokeMember("end");
        assertFalse(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);
    }

    @Test
    void risingTideStartsAtMaximumAndRecedesOnWonPoints() throws IOException {
        Harness harness = load("3_rising_tide.js");
        harness.boss.getCurrentHealth().set(5600);
        harness.phase.invokeMember("start");
        assertFalse(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);
        assertEquals(20, harness.phase.invokeMember("onHeal", 0, 100, false).asInt());
        assertEquals(3, harness.phase.invokeMember("getTideLevel").asInt());

        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertTrue(harness.announcements.stream().anyMatch(text -> text.contains("begins at Maximum Tide")));
        assertTrue(harness.useSkills.isEmpty());

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.RISING_TIDE_HOMING_BALL_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals((byte) (AtlantisV2Rules.HOMING_BALL_SKILL_ID - 1), harness.useSkills.getLast().skillId());

        AtlantisV2Rules.setNowForTest(harness.startedAt + 20_000L);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals((byte) (AtlantisV2Rules.BLIZZARD_SKILL_ID - 1), harness.useSkills.getLast().skillId());

        AtlantisV2Rules.setNowForTest(harness.startedAt + 22_000L);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals((byte) (AtlantisV2Rules.WATER_PILLAR_SKILL_ID - 1), harness.useSkills.getLast().skillId());

        AtlantisV2Rules.setNowForTest(harness.startedAt + 24_000L);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals((byte) (AtlantisV2Rules.HOMING_BALL_SKILL_ID - 1), harness.useSkills.getLast().skillId());

        AtlantisV2Rules.setNowForTest(harness.startedAt + 30_000L);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals((byte) (AtlantisV2Rules.STORM_SKILL_ID - 1), harness.useSkills.getLast().skillId());

        harness.boss.getCurrentHealth().set(5200);
        AtlantisV2Rules.setNowForTest(harness.startedAt + 42_000L);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(3, harness.phase.invokeMember("getTideLevel").asInt(),
                "direct damage and boss-health thresholds do not lower the tide");

        harness.phase.invokeMember("onDealDamageOnBallLoss", 0, 10, false);
        assertEquals(2, harness.phase.invokeMember("getTideLevel").asInt(),
                "winning a point against Royal Lizard lowers the tide once");
        harness.phase.invokeMember("onDealDamageOnBallLossToPlayer", 10, 0, false);
        assertEquals(3, harness.phase.invokeMember("getTideLevel").asInt(),
                "losing a point raises the tide again");
        harness.phase.invokeMember("onDealDamageOnBallLoss", 0, 10, false);

        harness.boss.getCurrentHealth().set(4400);
        AtlantisV2Rules.setNowForTest(harness.startedAt + 44_000L);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(2, harness.phase.invokeMember("getTideLevel").asInt());

        harness.phase.invokeMember("onDealDamageOnBallLoss", 0, 10, false);
        assertEquals(1, harness.phase.invokeMember("getTideLevel").asInt());

        harness.boss.getCurrentHealth().set(3200);
        AtlantisV2Rules.setNowForTest(harness.startedAt + 46_000L);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(1, harness.phase.invokeMember("getTideLevel").asInt(),
                "boss HP does not impose a Tide floor");

        assertEquals(2000, harness.phase.invokeMember(
                "onDealDamage", 0, 10, 10_000, false, false, null).asInt(),
                "boss damage is clamped at the final-phase threshold");
        assertEquals(PhaseUpdateResult.NEXT_PHASE, result(harness));
        assertTrue(harness.eventHandler.pending.isEmpty());
        assertTrue(harness.useSkills.stream().noneMatch(packet -> packet.skillId() == AtlantisV2Rules.SEA_WAVE_PACKET_ID),
                "Phase 3 must not repeat the SeaWave mechanic");
    }

    @Test
    void drownedCrownHealsOnBallLossInheritsPowersAndEndsWithFinalProcession() throws IOException {
        Harness harness = load("4_enrage.js");
        harness.left.getCurrentHealth().set(0);
        harness.right.getCurrentHealth().set(0);
        harness.phase.invokeMember("start");
        assertFalse(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);
        assertEquals("CALM", harness.phase.invokeMember("getState").asString());
        assertEquals(2800, harness.boss.getCurrentHealth().get());
        assertEquals(900, harness.left.getCurrentHealth().get());
        assertEquals(900, harness.right.getCurrentHealth().get());
        assertEquals(100, harness.phase.invokeMember("onHeal", 0, 100, false).asInt());

        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(3, harness.dealDamage.size(), "boss heal and two attendant rebirths must be visible");
        List<UseSkillView> rebirths = harness.useSkills.stream()
                .filter(packet -> packet.skillId() == (byte) (AtlantisV2Rules.REBIRTH_SKILL_ID - 1))
                .toList();
        assertEquals(2, rebirths.size(), "Phase 4 must cast Rebirth separately for both attendants");
        assertTrue(rebirths.stream().allMatch(packet -> packet.attacker() == 10));
        assertTrue(rebirths.stream().anyMatch(packet -> packet.target() == 11),
                "the left attendant must return to its own battle slot in Phase 4");
        assertTrue(rebirths.stream().anyMatch(packet -> packet.target() == 12),
                "the right attendant must return to its own battle slot in Phase 4");
        assertTrue(harness.announcements.stream().anyMatch(text -> text.contains("Blood Tide")));

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.DROWNED_CROWN_CALM_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals("BLOOD_TIDE", harness.phase.invokeMember("getState").asString());
        assertFalse(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);
        assertEquals(20, harness.phase.invokeMember("onHeal", 0, 100, false).asInt());

        harness.boss.getCurrentHealth().set(2400);
        harness.left.getCurrentHealth().set(600);
        harness.right.getCurrentHealth().set(600);
        harness.phase.invokeMember("onDealDamageOnBallLossToPlayer", 10, 0, false);
        assertEquals(2560, harness.boss.getCurrentHealth().get());
        assertEquals(840, harness.left.getCurrentHealth().get());
        assertEquals(840, harness.right.getCurrentHealth().get());
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertTrue(harness.announcements.stream().anyMatch(text -> text.contains("recover")));

        harness.left.getCurrentHealth().set(0);
        AtlantisV2Rules.setNowForTest(harness.startedAt + 14_000L);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(1, harness.phase.invokeMember("getInheritedPowers").asInt());
        assertTrue(harness.announcements.stream().anyMatch(text -> text.contains("frost passes")));

        AtlantisV2Rules.setNowForTest(harness.startedAt + 30_000L);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertTrue(harness.useSkills.stream().anyMatch(packet ->
                packet.skillId() == (byte) (AtlantisV2Rules.BLIZZARD_SKILL_ID - 1)));

        harness.right.getCurrentHealth().set(0);
        AtlantisV2Rules.setNowForTest(harness.startedAt + 31_000L);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals("CROWNLESS", harness.phase.invokeMember("getState").asString());
        assertEquals(2, harness.phase.invokeMember("getInheritedPowers").asInt());

        harness.boss.getCurrentHealth().set(400);
        AtlantisV2Rules.setNowForTest(harness.startedAt + 32_000L);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals("FINAL_PROCESSION", harness.phase.invokeMember("getState").asString());

        int wavesBefore = (int) harness.useSkills.stream()
                .filter(packet -> packet.skillId() == AtlantisV2Rules.SEA_WAVE_PACKET_ID)
                .count();
        for (int i = 0; i < AtlantisV2Rules.FINAL_POINT_WAVE_COUNT; i++) {
            AtlantisV2Rules.setNowForTest(harness.startedAt + 35_000L + i * 1_000L);
            assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        }
        assertEquals("FINAL_POINT", harness.phase.invokeMember("getState").asString());
        List<UseSkillView> procession = harness.useSkills.stream()
                .filter(packet -> packet.skillId() == AtlantisV2Rules.SEA_WAVE_PACKET_ID)
                .skip(wavesBefore)
                .toList();
        assertEquals(AtlantisV2Rules.FINAL_POINT_WAVE_COUNT, procession.size());
        assertTrue(procession.stream().allMatch(packet -> isSafeWaveDepth(packet.y())));

        int healthBeforeFinalLoss = harness.boss.getCurrentHealth().get();
        harness.phase.invokeMember("onDealDamageOnBallLossToPlayer", 10, 0, false);
        assertEquals(healthBeforeFinalLoss, harness.boss.getCurrentHealth().get(),
                "Blood Tide healing must end permanently at the Final Point");

        AtlantisV2Rules.setNowForTest(harness.startedAt + 50_000L);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertTrue(harness.useSkills.stream().anyMatch(packet ->
                packet.skillId() == (byte) (AtlantisV2Rules.HOMING_BALL_SKILL_ID - 1)));

        harness.boss.getCurrentHealth().set(0);
        assertEquals(PhaseUpdateResult.NEXT_PHASE, result(harness));
        assertTrue(harness.announcements.stream().anyMatch(text -> text.contains("Crown is no more")));
        harness.phase.invokeMember("end");
        assertFalse(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);
    }

    @Test
    void drownedCrownConsumesLivingAttendantsAtFivePercent() throws IOException {
        Harness harness = load("4_enrage.js");
        harness.phase.invokeMember("start");
        result(harness);
        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.DROWNED_CROWN_CALM_MS);
        result(harness);

        harness.boss.getCurrentHealth().set(400);
        AtlantisV2Rules.setNowForTest(harness.startedAt + 6_000L);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals("CROWNLESS", harness.phase.invokeMember("getState").asString());
        assertEquals(1200, harness.boss.getCurrentHealth().get(),
                "each of two consumed attendants restores five percent boss health");
        assertEquals(0, harness.left.getCurrentHealth().get());
        assertEquals(0, harness.right.getCurrentHealth().get());
        assertEquals(2, harness.phase.invokeMember("getInheritedPowers").asInt());
        assertTrue(harness.announcements.stream().anyMatch(text -> text.contains("devours")));
    }

    private static PhaseUpdateResult result(Harness harness) {
        return harness.phase.invokeMember("update", harness.connection).as(PhaseUpdateResult.class);
    }

    private static boolean isSafeWaveDepth(float actual) {
        for (float expected : AtlantisV2Rules.SEA_WAVE_DEPTHS) {
            if (Math.abs(actual - expected) < 0.001f) {
                return true;
            }
        }
        return false;
    }

    private static Harness load(String fileName) throws IOException {
        Harness harness = new Harness();
        AtlantisV2Rules.setNowForTest(1_000_000L);
        harness.startedAt = AtlantisV2Rules.now();
        Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        Value bindings = context.getBindings("js");
        bindings.putMember("gameManager", harness.gameManager);
        bindings.putMember("serviceManager", harness.serviceManager);
        bindings.putMember("eventHandler", harness.eventHandler);
        bindings.putMember("log", harness.log);
        bindings.putMember("game", harness.game);
        context.eval(Source.newBuilder("js", Files.readString(LIVE.resolve(fileName)), fileName).build());
        harness.phase = bindings.getMember("phase");
        assertFalse(harness.phase.isNull(), fileName + " must export phase");
        return harness;
    }

    static final class Harness {
        final FakeGame game = new FakeGame();
        final FakeGameManager gameManager;
        final FakeServiceManager serviceManager = new FakeServiceManager();
        final RecordingEventHandler eventHandler = new RecordingEventHandler();
        final FakeLog log = new FakeLog();
        final FakeConnection connection = new FakeConnection();
        final AdvancedGuardianState boss;
        final AdvancedGuardianState left;
        final AdvancedGuardianState right;
        final List<String> announcements = new ArrayList<>();
        final List<UseSkillView> useSkills = new ArrayList<>();
        final List<DealDamageView> dealDamage = new ArrayList<>();
        Value phase;
        long startedAt;

        Harness() {
            BossGuardian bossEntity = new BossGuardian();
            bossEntity.setId(1L);
            bossEntity.setBtItemID(1);
            Guardian addEntity = new Guardian();
            addEntity.setId(2L);
            addEntity.setBtItemID(2);
            boss = new AdvancedGuardianState(10L, 3L, bossEntity, (short) 10, 8000, 10, 10, 10, 10, 0, 0, 0);
            left = new AdvancedGuardianState(10L, 3L, addEntity, (short) 11, 3000, 10, 10, 10, 10, 0, 0, 0);
            right = new AdvancedGuardianState(10L, 3L, addEntity, (short) 12, 3000, 10, 10, 10, 10, 0, 0, 0);
            boss.getSkills().add(new Skill2Guardians());
            left.getSkills().add(new Skill2Guardians());
            right.getSkills().add(new Skill2Guardians());
            game.guardians.add(boss);
            game.guardians.add(left);
            game.guardians.add(right);
            game.players.add(new PlayerBattleState((short) 0, 77L, 3000, 10, 10, 10, 10));
            gameManager = new FakeGameManager(this);
        }
    }

    public static final class FakeGame {
        final ConcurrentLinkedDeque<AdvancedGuardianState> guardians = new ConcurrentLinkedDeque<>();
        final ConcurrentLinkedDeque<PlayerBattleState> players = new ConcurrentLinkedDeque<>();
        boolean shieldDisabled;
        boolean healDisabled;
        int supportExemptPosition = -1;
        final FakeCombat playerCombat = new FakeCombat();
        final FakeCombat guardianCombat = new FakeCombat();

        public ConcurrentLinkedDeque<AdvancedGuardianState> getGuardianBattleStates() {
            return guardians;
        }

        public ConcurrentLinkedDeque<PlayerBattleState> getPlayerBattleStates() {
            return players;
        }

        public AdvancedGuardianState getGuardianBattleStateByPosition(int position) {
            return guardians.stream().filter(g -> g.getPosition() == position).findFirst().orElse(null);
        }

        public void setPlayerSupportSkillsDisabled(boolean disabled) {
            shieldDisabled = disabled;
            healDisabled = disabled;
        }

        public void setPlayerShieldSkillsDisabled(boolean disabled) {
            shieldDisabled = disabled;
        }

        public void setPlayerHealSkillsDisabled(boolean disabled) {
            healDisabled = disabled;
        }

        public void setPlayerSupportExemptPosition(int position) {
            supportExemptPosition = position;
        }

        public void clearPlayerSupportExemptPosition() {
            supportExemptPosition = -1;
        }

        public FakeCombat getPlayerCombatSystem() {
            return playerCombat;
        }

        public FakeCombat getGuardianCombatSystem() {
            return guardianCombat;
        }
    }

    public static final class FakeCombat {
        public int heal(int target, int amount) {
            return amount;
        }

        public int dealDamage(int a, int b, int c, boolean d, boolean e, Skill skill) {
            return 0;
        }

        public int dealDamageToPlayer(int a, int b, int c, boolean d, boolean e, Skill skill) {
            return 0;
        }

        public int dealDamageOnBallLoss(int a, int b, boolean c) {
            return 0;
        }

        public int dealDamageOnBallLossToPlayer(int a, int b, boolean c) {
            return 0;
        }
    }

    public static final class FakeGameManager {
        private final Harness harness;

        FakeGameManager(Harness harness) {
            this.harness = harness;
        }

        public RecordingEventHandler getEventHandler() {
            return harness.eventHandler;
        }

        public FakeConnection getConnectionByPlayerId(long playerId) {
            return harness.connection;
        }

        public void sendPacketToAllClientsInSameGameSession(Packet packet, Object connection) {
            if (packet instanceof S2CChatRoomAnswerPacket) {
                harness.announcements.add(decodeUtf16From(packet, 1));
            } else if (packet instanceof S2CMatchplayUseSkill) {
                harness.useSkills.add(UseSkillView.from(packet));
            } else if (packet instanceof S2CMatchplayDealDamage) {
                harness.dealDamage.add(DealDamageView.from(packet));
            }
        }
    }

    public static final class FakeServiceManager {
        private final FakeSkillService skillService = new FakeSkillService();

        public FakeSkillService getSkillService() {
            return skillService;
        }
    }

    public static final class FakeSkillService {
        public Skill findSkillById(long id) {
            Skill skill = new Skill();
            skill.setId(id);
            skill.setName("skill-" + id);
            return skill;
        }
    }

    public static final class RecordingEventHandler extends EventHandler {
        final List<RunnableEvent> pending = new ArrayList<>();

        public RecordingEventHandler() {
            setFireableDeque(new LinkedBlockingQueue<>());
        }

        @Override
        public void offerJS(Fireable fireable) {
            if (fireable instanceof RunnableEvent event) {
                pending.add(event);
            }
        }

        void runAll() {
            for (RunnableEvent event : pending) {
                event.getRunnable().run();
            }
            pending.clear();
        }
    }

    public static final class FakeConnection {
        private final FakeClient client = new FakeClient();

        public FakeClient getClient() {
            return client;
        }
    }

    public static final class FakeClient {
        public Integer getGameSessionId() {
            return null;
        }

        public Object getActiveGameSession() {
            return new Object();
        }

        public FakePlayer getPlayer() {
            return new FakePlayer();
        }
    }

    public static final class FakePlayer {
        public String getName() {
            return "TestPlayer";
        }
    }

    public static final class FakeLog {
        public void error(String message, Object... args) {
            fail("script error: " + message);
        }
    }

    record UseSkillView(byte attacker, byte target, byte skillId, float x, float z, float y) {
        static UseSkillView from(Packet packet) {
            byte[] raw = packet.toBytes();
            int payload = 8;
            return new UseSkillView(
                    raw[payload],
                    raw[payload + 1],
                    raw[payload + 2],
                    BitKit.bytesToFloat(raw, payload + 4),
                    BitKit.bytesToFloat(raw, payload + 8),
                    BitKit.bytesToFloat(raw, payload + 12));
        }
    }

    record DealDamageView(short target, short hp, byte skillId) {
        static DealDamageView from(Packet packet) {
            byte[] raw = packet.toBytes();
            int payload = 8;
            return new DealDamageView(
                    BitKit.bytesToShort(raw, payload),
                    BitKit.bytesToShort(raw, payload + 2),
                    raw[payload + 6]);
        }
    }

    private static String decodeUtf16From(Packet packet, int stringIndex) {
        byte[] raw = packet.toBytes();
        int offset = 9;
        String last = "";
        for (int i = 0; i <= stringIndex && offset + 1 < raw.length; i++) {
            int end = offset;
            while (end + 1 < raw.length && !(raw[end] == 0 && raw[end + 1] == 0)) {
                end += 2;
            }
            last = new String(raw, offset, Math.max(0, end - offset), java.nio.charset.StandardCharsets.UTF_16LE);
            offset = end + 2;
        }
        return last;
    }
}
