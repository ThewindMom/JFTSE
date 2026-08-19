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
    void greenTideStripsThenFiresConfirmedLtrVolleys() throws IOException {
        Harness harness = load("1_green_tide.js");
        harness.phase.invokeMember("start");

        assertEquals("Green Tide", harness.phase.invokeMember("getPhaseName").asString());
        assertEquals(-1L, harness.phase.invokeMember("getGuardianAttackLoopTime", harness.boss).asLong());
        assertTrue(harness.boss.getSkills().isEmpty());
        assertTrue(harness.left.getSkills().isEmpty());
        assertFalse(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.STRIP_GUARDIAN_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertTrue(harness.announcements.stream().anyMatch(text -> text.contains("voices fall silent")));

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.STRIP_PLAYER_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertTrue(harness.game.shieldDisabled);
        assertTrue(harness.game.healDisabled);

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.FIRST_VOLLEY_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(AtlantisV2Rules.FIRST_VOLLEY_COUNT, harness.eventHandler.pending.size());

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.SECOND_VOLLEY_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(AtlantisV2Rules.FIRST_VOLLEY_COUNT + AtlantisV2Rules.SECOND_VOLLEY_COUNT,
                harness.eventHandler.pending.size());

        harness.eventHandler.runAll();
        List<UseSkillView> waves = harness.useSkills.stream()
                .filter(packet -> packet.skillId() == AtlantisV2Rules.SEA_WAVE_PACKET_ID)
                .toList();
        assertEquals(AtlantisV2Rules.FIRST_VOLLEY_COUNT + AtlantisV2Rules.SECOND_VOLLEY_COUNT, waves.size());
        for (UseSkillView wave : waves) {
            assertEquals(AtlantisV2Rules.DUMMY_ATTACKER, wave.attacker());
            assertEquals((byte) 4, wave.target());
            assertEquals(AtlantisV2Rules.WAVE_X, wave.x(), 0.001f);
            assertEquals(AtlantisV2Rules.WAVE_Z, wave.z(), 0.001f);
            assertEquals(AtlantisV2Rules.WAVE_Y, wave.y(), 0.001f);
        }

        harness.left.getCurrentHealth().set(0);
        harness.right.getCurrentHealth().set(0);
        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.RESTORE_MS);
        assertEquals(PhaseUpdateResult.NEXT_PHASE, result(harness));
        assertFalse(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);
        harness.phase.invokeMember("end");
        assertFalse(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);
    }

    @Test
    void crabWindowRevivesAddsAtFullHpAndReenablesTwentyPercentHeal() throws IOException {
        Harness harness = load("2_crab_window.js");
        harness.left.getCurrentHealth().set(0);
        harness.right.getCurrentHealth().set(0);
        harness.phase.invokeMember("start");
        assertTrue(harness.game.shieldDisabled);
        assertTrue(harness.game.healDisabled);

        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(AtlantisV2Rules.BLIZZARD_VOLLEY_COUNT, harness.eventHandler.pending.size());

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.WAVE_ONLY_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertTrue(harness.announcements.stream().anyMatch(text -> text.contains("Plant crabs")));

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.WAVE_ONLY_MS + AtlantisV2Rules.CRAB_WINDOW_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(harness.left.getMaxHealth(), harness.left.getCurrentHealth().get());
        assertEquals(harness.right.getMaxHealth(), harness.right.getCurrentHealth().get());
        assertTrue(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);
        assertTrue(harness.dealDamage.stream().anyMatch(packet -> packet.target() == 11));
        assertTrue(harness.dealDamage.stream().anyMatch(packet -> packet.target() == 12));

        harness.left.getCurrentHealth().set(0);
        harness.right.getCurrentHealth().set(0);
        assertEquals(PhaseUpdateResult.NEXT_PHASE, result(harness));
        harness.phase.invokeMember("end");
        assertTrue(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);
    }

    @Test
    void stormChargeFiresOneStormThenMegawaveAfterCharge() throws IOException {
        Harness harness = load("3_storm_charge.js");
        harness.phase.invokeMember("start");
        assertTrue(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);

        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(1, harness.useSkills.size());
        assertEquals((byte) (AtlantisV2Rules.STORM_SKILL_ID - 1), harness.useSkills.get(0).skillId());

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.INFERNO_INTERVAL_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertTrue(harness.useSkills.stream().anyMatch(packet -> packet.skillId() == (byte) (AtlantisV2Rules.INFERNO_SKILL_ID - 1)));

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.STORM_DWELL_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertTrue(harness.announcements.stream().anyMatch(text -> text.contains("charges")));

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.STORM_DWELL_MS + AtlantisV2Rules.CHARGE_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(AtlantisV2Rules.MEGAWAVE_COUNT, harness.eventHandler.pending.size());

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.STORM_DWELL_MS + AtlantisV2Rules.CHARGE_MS
                + 250 + AtlantisV2Rules.MEGAWAVE_COUNT * AtlantisV2Rules.MEGAWAVE_GAP_MS);
        assertEquals(PhaseUpdateResult.NEXT_PHASE, result(harness));
    }

    @Test
    void enrageWaitsForAddsToDieBeforeFiringWaves() throws IOException {
        Harness harness = load("4_enrage.js");
        harness.left.getCurrentHealth().set(0);
        harness.right.getCurrentHealth().set(0);
        harness.phase.invokeMember("start");
        assertFalse(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);

        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertEquals(harness.left.getMaxHealth(), harness.left.getCurrentHealth().get());
        assertEquals(harness.right.getMaxHealth(), harness.right.getCurrentHealth().get());

        AtlantisV2Rules.setNowForTest(harness.startedAt + AtlantisV2Rules.STUN_MS);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertFalse(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);
        assertTrue(harness.useSkills.isEmpty());

        harness.left.getCurrentHealth().set(0);
        harness.right.getCurrentHealth().set(0);
        assertEquals(PhaseUpdateResult.CONTINUE, result(harness));
        assertTrue(harness.game.shieldDisabled);
        assertTrue(harness.game.healDisabled);
        assertEquals(1, harness.useSkills.size());
        assertEquals(AtlantisV2Rules.SEA_WAVE_PACKET_ID, harness.useSkills.get(0).skillId());

        harness.boss.getCurrentHealth().set(0);
        assertEquals(PhaseUpdateResult.NEXT_PHASE, result(harness));
        harness.phase.invokeMember("end");
        assertFalse(harness.game.shieldDisabled);
        assertFalse(harness.game.healDisabled);
    }

    private static PhaseUpdateResult result(Harness harness) {
        return harness.phase.invokeMember("update", harness.connection).as(PhaseUpdateResult.class);
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
        public Object getActiveGameSession() {
            return new Object();
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
