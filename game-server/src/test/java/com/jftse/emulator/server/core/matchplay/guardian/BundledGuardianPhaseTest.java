package com.jftse.emulator.server.core.matchplay.guardian;

import com.jftse.emulator.common.scripting.ScriptFile;
import com.jftse.emulator.common.scripting.ScriptManagerV2;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.combat.GuardianCombatSystem;
import com.jftse.emulator.server.core.matchplay.combat.PlayerCombatSystem;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.core.task.GuardianServeTask;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.battle.Skill;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import com.jftse.server.core.service.SkillService;
import com.jftse.server.core.thread.ThreadManager;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BundledGuardianPhaseTest {
    static Stream<Arguments> phases() throws Exception {
        Path root = Path.of(BundledGuardianPhaseTest.class.getClassLoader().getResource("scripts/guardian-phase").toURI());
        List<Path> files;
        try (var paths = Files.walk(root)) {
            files = paths.filter(path -> path.toString().endsWith(".js")).sorted().toList();
        }
        assertEquals(8, files.size(), "Update the coverage inventory when bundled phases change");
        return files.stream().flatMap(file -> Stream.of("live", "all-player-death", "partial-death", "replacement", "finished", "transition", "chain", "late-timers", "random-high", "revive-low", "revive-high")
                .filter(state -> !state.startsWith("revive-") || file.getFileName().toString().startsWith("3_"))
                .filter(state -> !state.equals("chain") || file.getFileName().toString().startsWith("1_"))
                .filter(state -> !state.equals("late-timers") || root.relativize(file).toString().startsWith("10/"))
                .map(state -> Arguments.of(root.relativize(file).toString(), file, state)));
    }

    @ParameterizedTest(name = "{0} {2}")
    @MethodSource("phases")
    void servesStartsHealsAndRunsFirstTimedUpdate(String key, Path path, String state) throws Exception {
        Map<Class<?>, Object> previous = new LinkedHashMap<>();
        for (Class<?> type : List.of(GameManager.class, ServiceManager.class, ScriptManagerV2.class, ThreadManager.class))
            previous.put(type, ReflectionTestUtils.getField(type, "instance"));
        ScriptManagerV2 scripts = null;
        ScriptFile file = null;
        List<ScriptFile> loadedFiles = new ArrayList<>();
        try {
            EventHandler events = new EventHandler();
            events.init();
            GameManager manager = mock(GameManager.class);
            when(manager.getEventHandler()).thenReturn(events);
            ReflectionTestUtils.setField(manager, "clients", new ConcurrentLinkedDeque<FTClient>());
            ReflectionTestUtils.setField(GameManager.class, "instance", manager);
            ReflectionTestUtils.setField(ThreadManager.class, "instance", mock(ThreadManager.class));
            ServiceManager services = mock(ServiceManager.class);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", services);
            SkillService skills = mock(SkillService.class);
            when(services.getSkillService()).thenReturn(skills);
            when(skills.findSkillById(anyLong())).thenAnswer(invocation -> {
                Skill skill = new Skill();
                skill.setId(invocation.getArgument(0));
                skill.setDamage(-1);
                return skill;
            });
            MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
            when(game.getFinished()).thenReturn(new AtomicBoolean());
            when(game.getIsHardMode()).thenReturn(new AtomicBoolean());
            when(game.getBossBattleActive()).thenReturn(new AtomicBoolean(true));
            when(game.getStageChangingToBoss()).thenReturn(new AtomicBoolean(true));
            when(game.isAdvancedBossGuardianMode()).thenReturn(true);
            when(game.getMap()).thenReturn(mock(com.jftse.entities.database.model.map.SMaps.class));
            AdvancedGuardianState boss = guardian(10, true);
            AdvancedGuardianState minion = guardian(11, false);
            when(game.getGuardianBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(boss, minion)));
            when(game.getGuardianBattleStateByPosition(10)).thenReturn(boss);
            when(game.getGuardianBattleStateByPosition(11)).thenReturn(minion);
            PlayerBattleState host = new PlayerBattleState((short) 0, 1, 100, 0, 0, 0, 0);
            PlayerBattleState guest = new PlayerBattleState((short) 1, 2, 100, 0, 0, 0, 0);
            when(game.getPlayerBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(host, guest)));
            when(game.getPlayerCombatSystem()).thenReturn(new PlayerCombatSystem(game));
            when(game.getGuardianCombatSystem()).thenReturn(new GuardianCombatSystem(game));
            when(game.getGuardianHealPercentage()).thenReturn((short) 20);
            GameSession session = new GameSession();
            session.setMatchplayGame(game);
            FTClient client = mock(FTClient.class, withSettings().useConstructor());
            doCallRealMethod().when(client).matchMembership();
            doCallRealMethod().when(client).sendMatchPacket(any(), any());
            FTConnection connection = mock(FTConnection.class);
            var room = mock(com.jftse.emulator.server.core.life.room.Room.class);
            var hostSeat = mock(com.jftse.emulator.server.core.life.room.RoomPlayer.class);
            when(hostSeat.getPosition()).thenReturn((short) 0);
            when(hostSeat.getPlayerId()).thenReturn(1L);
            when(hostSeat.isMaster()).thenReturn(true);
            when(client.getRoomPlayer()).thenReturn(hostSeat);
            when(client.hasPlayer()).thenReturn(true);
            when(client.getActiveRoom()).thenReturn(room);
            when(connection.getClient()).thenReturn(client);
            when(client.getConnection()).thenReturn(connection);
            when(client.getActiveGameSession()).thenReturn(session);
            FTClient other = mock(FTClient.class, withSettings().useConstructor());
            doCallRealMethod().when(other).matchMembership();
            doCallRealMethod().when(other).sendMatchPacket(any(), any());
            FTConnection otherConnection = mock(FTConnection.class);
            when(other.getConnection()).thenReturn(otherConnection);
            when(other.getActiveRoom()).thenReturn(room);
            when(other.getActiveGameSession()).thenReturn(session);
            session.getClients().addAll(List.of(client, other));
            session.initializeGameplayActorPositions();
            doCallRealMethod().when(manager).sendPacketToAllClientsInSameGameSession(any(), eq(connection));
            List<String> hostPackets = new ArrayList<>();
            List<String> guestPackets = new ArrayList<>();
            doAnswer(invocation -> {
                hostPackets.add(HexFormat.of().formatHex(((com.jftse.server.core.protocol.IPacket) invocation.getArgument(0)).toBytes()));
                return null;
            }).when(connection).sendTCP(any(com.jftse.server.core.protocol.IPacket.class));
            doAnswer(invocation -> {
                guestPackets.add(HexFormat.of().formatHex(((com.jftse.server.core.protocol.IPacket) invocation.getArgument(0)).toBytes()));
                return null;
            }).when(otherConnection).sendTCP(any(com.jftse.server.core.protocol.IPacket.class));
            file = new ScriptFile(key, path.toFile(), "GUARDIAN-PHASE", path.getParent().getFileName().toString());
            loadedFiles.add(file);
            if (state.equals("chain")) {
                try (var paths = Files.list(path.getParent())) {
                    paths.filter(candidate -> candidate.toString().endsWith(".js") && !candidate.equals(path)).sorted()
                            .forEach(candidate -> loadedFiles.add(new ScriptFile(candidate.getFileName().toString(),
                                    candidate.toFile(), "GUARDIAN-PHASE", path.getParent().getFileName().toString())));
                }
            }
            scripts = new ScriptManagerV2(loadedFiles);
            List<PhaseScript> chain = new ArrayList<>();
            for (ScriptFile loaded : loadedFiles) {
                BossBattlePhaseable phase = scripts.getInterfaceByImplementingObject(loaded, "phase", BossBattlePhaseable.class,
                        Map.of("game", game, "gameManager", manager, "serviceManager", services,
                                "eventHandler", events, "log", LogManager.getLogger(getClass())));
                assertNotNull(phase);
                scripts.callOnScriptThread(loaded, () -> loaded.getContext().eval("js", "var auditNow = 1000; Date.now = () => auditNow; Math.random = () => " + (state.endsWith("high") ? "0.9" : "0.1") + ";"));
                chain.add(new PhaseScript(phase, loaded, scripts));
            }
            ScriptFile scriptFile = file;
            PhaseScript script = chain.getFirst();
            PhaseManager phases = new PhaseManager(chain, scripts);
            when(game.getPhaseManager()).thenReturn(phases);
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> new GuardianServeTask(connection).run());
            assertTrue(phases.getIsRunning().get());
            assertFalse(game.getStageChangingToBoss().get());
            assertEquals(0, script.phaseTime());
            assertEquals(PhaseUpdateResult.CONTINUE, script.update(connection));
            boolean immune = key.contains("/1_");
            assertEquals(immune ? 100 : 99, phases.onDealDamage(0, 10, -1, false, false, null));
            boss.getCurrentHealth().set(100);
            assertEquals(99, phases.onDealDamage(0, 11, -1, false, false, null));
            minion.getCurrentHealth().set(100);
            assertEquals(99, phases.onDealDamageToPlayer(10, 1, -1, false, false, null));
            guest.getCurrentHealth().set(100);
            assertEquals(immune ? 100 : 98, phases.onDealDamageOnBallLoss(4, 10, false));
            boss.getCurrentHealth().set(100);
            assertEquals(98, phases.onDealDamageOnBallLossToPlayer(4, 1, false));
            guest.getCurrentHealth().set(100);
            host.getCurrentHealth().set(50);
            boss.getCurrentHealth().set(50);
            assertEquals(key.startsWith("10/3_") ? 57 : 70, phases.onHeal(0, 20, false));
            assertEquals(70, phases.onHeal(10, 99, true), "Guardian configured percentage overrides request");
            host.getCurrentHealth().set(100);
            boss.getCurrentHealth().set(100);
            if (state.equals("late-timers")) {
                boss.getCurrentHealth().set(50);
                scripts.callOnScriptThread(file, () -> scriptFile.getContext().eval("js", "auditNow = 92000;"));
                assertTimeoutPreemptively(Duration.ofSeconds(5), () -> phases.update(91000));
                assertTrue(phases.getIsRunning().get());
                Set<Integer> emittedSkills = new HashSet<>();
                for (String hex : hostPackets) {
                    byte[] packet = HexFormat.of().parseHex(hex);
                    if (packet.length == 24) emittedSkills.add(Byte.toUnsignedInt(packet[10]) + 1);
                }
                if (key.startsWith("10/3_")) {
                    assertTrue(emittedSkills.containsAll(List.of(37, 35, 26, 13, 57)), emittedSkills.toString());
                    assertEquals(45, boss.getCurrentHealth().get(), "Current script sets45%, not additive healing");
                }
                if (key.startsWith("10/4_")) {
                    assertTrue(emittedSkills.containsAll(List.of(32, 61, 62, 65, 6)), emittedSkills.toString());
                    assertEquals(100, boss.getCurrentHealth().get());
                    for (int pass = 0; pass < 4; pass++) {
                        List<com.jftse.emulator.server.core.matchplay.event.Fireable> queued = new ArrayList<>();
                        events.getFireableDeque().drainTo(queued);
                        for (var event : queued)
                            if (event instanceof com.jftse.emulator.server.core.matchplay.event.RunnableEvent runnable)
                                assertTimeoutPreemptively(Duration.ofSeconds(5), runnable.getRunnable()::run);
                    }
                    assertEquals(40, host.getCurrentHealth().get());
                    assertEquals(40, guest.getCurrentHealth().get());
                    assertTrue(events.getFireableDeque().isEmpty(), "Finite three-tick DoT leaves no pending tick");
                }
                assertEquals(hostPackets, guestPackets);
                return;
            }
            if (state.equals("chain")) {
                for (int index = 0; index < chain.size(); index++) {
                    assertSame(chain.get(index), phases.getCurrentPhase().get());
                    assertTrue(phases.getIsRunning().get());
                    if (index == 0) minion.getCurrentHealth().set(0);
                    else if (key.startsWith("10/") && index < 3) boss.getCurrentHealth().set(index == 1 ? 49 : 24);
                    else {
                        if (key.startsWith("10/")) {
                            minion.getCurrentHealth().set(0);
                            phases.update(0);
                            assertEquals(100, minion.getCurrentHealth().get());
                        }
                        boss.getCurrentHealth().set(0);
                        minion.getCurrentHealth().set(0);
                    }
                    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> phases.update(0));
                    for (var event : List.copyOf(events.getFireableDeque())) {
                        if (event instanceof com.jftse.emulator.server.core.matchplay.event.RunnableEvent runnable &&
                                event.getExecutionMode() == com.jftse.emulator.server.core.matchplay.event.ExecutionMode.JS_INLINE)
                            assertTimeoutPreemptively(Duration.ofSeconds(5), runnable.getRunnable()::run);
                    }
                    events.getFireableDeque().clear();
                    if (index + 1 < chain.size()) {
                        assertSame(chain.get(index + 1), phases.getCurrentPhase().get());
                        assertFalse(phases.getIsChangingPhase().get());
                    }
                }
                assertFalse(phases.getIsRunning().get());
                assertEquals(hostPackets, guestPackets);
                return;
            }
            if (state.equals("transition")) {
                PhaseUpdateResult expected = PhaseUpdateResult.NEXT_PHASE;
                if (key.startsWith("7/2_") || key.startsWith("8/2_")) {
                    for (int threshold : List.of(80, 45)) {
                        minion.getCurrentHealth().set(0);
                        boss.getCurrentHealth().set(threshold);
                        assertEquals(PhaseUpdateResult.CONTINUE, script.update(connection));
                        assertEquals(100, minion.getCurrentHealth().get());
                        var callback = (com.jftse.emulator.server.core.matchplay.event.RunnableEvent) events.poll();
                        assertNotNull(callback);
                        assertTimeoutPreemptively(Duration.ofSeconds(5), callback.getRunnable()::run);
                        events.getFireableDeque().clear();
                    }
                    minion.getCurrentHealth().set(0);
                    scripts.callOnScriptThread(file, () -> scriptFile.getContext().eval("js", "auditNow = 122000;"));
                    assertEquals(PhaseUpdateResult.CONTINUE, script.update(connection));
                    verify(boss).setStr(250);
                    assertEquals(1000, script.getGuardianAttackLoopTime(boss));
                    boss.getCurrentHealth().set(0);
                    expected = PhaseUpdateResult.END_PHASE;
                } else if (key.startsWith("10/2_")) boss.getCurrentHealth().set(49);
                else if (key.startsWith("10/3_")) boss.getCurrentHealth().set(24);
                else if (key.startsWith("10/4_")) {
                    minion.getCurrentHealth().set(0);
                    assertEquals(PhaseUpdateResult.CONTINUE, script.update(connection));
                    assertEquals(100, minion.getCurrentHealth().get());
                    var callback = (com.jftse.emulator.server.core.matchplay.event.RunnableEvent) events.poll();
                    assertNotNull(callback);
                    assertTimeoutPreemptively(Duration.ofSeconds(5), callback.getRunnable()::run);
                    minion.getCurrentHealth().set(0);
                    boss.getCurrentHealth().set(0);
                    expected = PhaseUpdateResult.END_PHASE;
                } else minion.getCurrentHealth().set(0);
                assertEquals(expected, script.update(connection));
                assertTimeoutPreemptively(Duration.ofSeconds(5), () -> phases.update(0));
                assertFalse(phases.getIsRunning().get(), "Single-phase fixture finishes after its transition signal");
                assertEquals(hostPackets, guestPackets);
                return;
            }
            if (state.equals("all-player-death")) host.getCurrentHealth().set(0);
            if (state.equals("all-player-death") || state.equals("partial-death")) guest.getCurrentHealth().set(0);
            if (state.startsWith("revive-")) minion.getCurrentHealth().set(0);
            if (state.equals("replacement")) when(client.getActiveGameSession()).thenReturn(new GameSession());
            if (state.equals("finished")) game.getFinished().set(true);
            clearInvocations(manager);
            hostPackets.clear();
            guestPackets.clear();
            scripts.callOnScriptThread(file, () -> scriptFile.getContext().eval("js", "auditNow = 13000;"));
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> phases.update(12000));
            if (state.equals("replacement") || state.equals("finished")) {
                verifyNoInteractions(manager);
                assertTrue(events.getFireableDeque().isEmpty());
            } else assertNotEquals(PhaseUpdateResult.ERROR, script.update(connection));
            if (state.startsWith("revive-")) assertEquals(state.endsWith("low") ? 30 : 0, minion.getCurrentHealth().get());
            if (state.equals("random-high")) {
                if (key.startsWith("10/")) {
                    byte[] cast = hostPackets.stream().map(HexFormat.of()::parseHex)
                            .filter(bytes -> bytes.length == 24 && Byte.toUnsignedInt(bytes[10]) == 6).findFirst().orElseThrow();
                    assertEquals(1, Byte.toUnsignedInt(cast[9]), "High RNG selects last living player for polymorph");
                } else {
                    assertEquals(12000, script.getGuardianAttackLoopTime(minion));
                }
            }
            if (state.equals("live") && key.startsWith("10/")) {
                for (var event : List.copyOf(events.getFireableDeque())) {
                    if (event instanceof com.jftse.emulator.server.core.matchplay.event.RunnableEvent runnable &&
                            event.getExecutionMode() == com.jftse.emulator.server.core.matchplay.event.ExecutionMode.JS_INLINE)
                        assertTimeoutPreemptively(Duration.ofSeconds(5), runnable.getRunnable()::run);
                }
                byte[] cast = hostPackets.stream().map(HexFormat.of()::parseHex)
                        .filter(bytes -> bytes.length == 24 && Byte.toUnsignedInt(bytes[10]) == 5)
                        .findFirst().orElseThrow();
                short actor = (short) Byte.toUnsignedInt(cast[8]);
                short target = (short) Byte.toUnsignedInt(cast[9]);
                byte skillId = (byte) (Byte.toUnsignedInt(cast[10]) + 1);
                assertEquals(11, actor);
                assertEquals(0, target);
                var hits = new com.jftse.emulator.server.core.handler.matchplay.SpellHitsTargetHandler();
                for (short[] invalid : List.of(new short[]{99, target, skillId}, new short[]{actor, 99, skillId},
                        new short[]{actor, target, (short) (skillId + 1)})) {
                    hits.handle(connection, com.jftse.server.core.shared.packets.matchplay.CMSGSpellHitsTarget.builder()
                            .attackerPosition(invalid[0]).targetPosition(invalid[1]).skillId((byte) invalid[2]).build());
                    assertEquals(100, host.getCurrentHealth().get());
                }
                var report = com.jftse.server.core.shared.packets.matchplay.CMSGSpellHitsTarget.builder()
                        .attackerPosition(actor).targetPosition(target).skillId(skillId).applySkillEffect((byte) 0).build();
                hits.handle(connection, report);
                assertEquals(99, host.getCurrentHealth().get(), "A server-emitted script cast must admit its matching server-side hit report");
                hits.handle(connection, report);
                assertEquals(99, host.getCurrentHealth().get(), "Duplicate report is not another hit");
                assertFalse(session.tryConsumeSkillHit(actor, guest.getPosition(), skillId, System.nanoTime() + 16_000_000_000L));
                when(game.isAdvancedBossGuardianMode()).thenReturn(false);
                var guardianSkills = mock(com.jftse.server.core.service.GuardianSkillsService.class);
                when(services.getGuardianSkillsService()).thenReturn(guardianSkills);
                Skill normalSkill = skills.findSkillById((long) skillId);
                when(skills.findSkillByIndex(skillId - 1)).thenReturn(normalSkill);
                when(guardianSkills.getRandomGuardianSkillBasedOnProbability(anyInt(), anyInt(), anyBoolean(), any(), any()))
                        .thenReturn(normalSkill);
                new com.jftse.emulator.server.core.task.GuardianAttackTask(connection, minion).run();
                byte[] assignment = HexFormat.of().parseHex(hostPackets.getLast());
                var assignmentBody = java.nio.ByteBuffer.wrap(assignment).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                assertEquals(16, assignment.length);
                assertEquals(actor, assignmentBody.getShort(10));
                int assignedIndex = assignmentBody.getInt(12);
                assertEquals(skillId - 1, assignedIndex);
                assertFalse(session.tryConsumeSkillHit(actor, target, skillId, System.nanoTime()));
                var use = com.jftse.server.core.shared.packets.matchplay.CMSGPlayerUseSkill.builder()
                        .attackerPosition((byte) actor).targetPosition((byte) target)
                        .sourceValue(assignmentBody.getShort(8)).skillIndex((byte) assignedIndex).isQuickSlot(false).build();
                var casts = new com.jftse.emulator.server.core.handler.matchplay.PlayerUseSkillHandler();
                casts.handle(connection, use);
                hits.handle(connection, report);
                assertEquals(98, host.getCurrentHealth().get(), "Normal assigned cast reaches the same hit boundary");
                casts.handle(connection, use);
                hits.handle(connection, report);
                assertEquals(98, host.getCurrentHealth().get(), "Consumed assignment cannot reauthorize a duplicate cast");
                game.getFinished().set(true);
                int before = hostPackets.size();
                synchronized (game) {
                    phases.sendSkill(new com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill(
                            (byte) actor, (byte) target, cast[10], cast[11], 0, 0, 0), connection);
                }
                assertEquals(before, hostPackets.size());
                assertFalse(session.tryConsumeSkillHit(actor, target, skillId, System.nanoTime()));
            }
            assertEquals(hostPackets, guestPackets, "Both connected participants receive identical phase broadcasts");
            script.end();
            assertTrue(script.hasEnded());
        } finally {
            if (scripts != null) scripts.shutdownAllExecutors();
            for (ScriptFile loaded : loadedFiles)
                if (loaded.getContext() != null) loaded.getContext().close(true);
            previous.forEach((type, value) -> ReflectionTestUtils.setField(type, "instance", value));
        }
    }

    private static AdvancedGuardianState guardian(int position, boolean boss) {
        AdvancedGuardianState guardian = mock(AdvancedGuardianState.class);
        when(guardian.getPosition()).thenReturn(position);
        when(guardian.isBoss()).thenReturn(boss);
        when(guardian.getCurrentHealth()).thenReturn(new AtomicInteger(100));
        when(guardian.getMaxHealth()).thenReturn(100);
        when(guardian.getSkills()).thenReturn(new ArrayList<>());
        return guardian;
    }
}
