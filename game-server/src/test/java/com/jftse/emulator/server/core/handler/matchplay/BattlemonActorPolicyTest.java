package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.combat.GuardianCombatSystem;
import com.jftse.emulator.server.core.matchplay.combat.PlayerCombatSystem;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.event.RunnableEvent;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.battle.Skill;
import com.jftse.entities.database.model.map.SMaps;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.matchplay.battle.GuardianBattleState;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import com.jftse.server.core.matchplay.battle.SkillCrystal;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.SkillDropRateService;
import com.jftse.server.core.service.SkillService;
import com.jftse.server.core.shared.packets.matchplay.CMSGPlayerPickupCrystal;
import com.jftse.server.core.shared.packets.matchplay.CMSGPlayerUseSkill;
import com.jftse.server.core.shared.packets.matchplay.CMSGSpellHitsTarget;
import com.jftse.server.core.shared.packets.matchplay.CMSGSwapSpell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

class BattlemonActorPolicyTest {
    private Object previousGameManager;
    private Object previousServiceManager;
    private SkillService skillService;

    @BeforeEach
    void setUpManagers() {
        previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");

        GameManager gameManager = mock(GameManager.class);
        ServiceManager serviceManager = mock(ServiceManager.class);
        skillService = mock(SkillService.class);
        EventHandler eventHandler = mock(EventHandler.class);
        when(gameManager.getEventHandler()).thenReturn(eventHandler);
        when(eventHandler.createRunnableEvent(any(), anyLong())).thenReturn(mock(RunnableEvent.class));
        when(serviceManager.getSkillService()).thenReturn(skillService);
        when(serviceManager.getSkillDropRateService()).thenReturn(mock(SkillDropRateService.class));

        ReflectionTestUtils.setField(GameManager.class, "instance", gameManager);
        ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);
    }

    @AfterEach
    void restoreManagers() {
        ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
        ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
    }

    @Test
    void crystalPickupUsesAuthenticatedRoomPositionInsteadOfUnreliablePacketPosition() {
        BattleContext context = battleContext((short) 2, true);
        SkillCrystal crystal = new SkillCrystal(17);
        context.game().getSkillCrystals().add(crystal);
        Queue<SkillCrystal> ownerCrystals = new ConcurrentLinkedDeque<>();
        when(context.roomPlayer().getPickedUpSkillCrystals()).thenReturn(ownerCrystals);

        CMSGPlayerPickupCrystal packet = CMSGPlayerPickupCrystal.builder()
                .playerPosition((byte) 2)
                .crystalId((short) 17)
                .build();
        new PlayerPickingUpCrystalHandler().handle(context.connection(), packet);

        assertTrue(context.game().getSkillCrystals().isEmpty());
        assertTrue(ownerCrystals.contains(crystal));
    }

    @Test
    void duplicatePickupDoesNotGrantOrScheduleTwiceAndQueueEvictsOldest() {
        BattleContext context = battleContext((short) 0, true);
        Queue<SkillCrystal> queue = new java.util.concurrent.ArrayBlockingQueue<>(2);
        queue.add(new SkillCrystal(1));
        queue.add(new SkillCrystal(2));
        when(context.roomPlayer().getPickedUpSkillCrystals()).thenReturn(queue);
        SkillCrystal crystal = new SkillCrystal(17);
        context.game().getSkillCrystals().add(crystal);
        CMSGPlayerPickupCrystal packet = CMSGPlayerPickupCrystal.builder().crystalId((short) 17).build();
        PlayerPickingUpCrystalHandler handler = new PlayerPickingUpCrystalHandler();
        handler.handle(context.connection(), packet);
        handler.handle(context.connection(), packet);
        assertEquals(List.of(2, 17), queue.stream().map(SkillCrystal::getId).toList());
        verify(GameManager.getInstance()).sendPacketToAllClientsInSameGameSession(any(), eq(context.connection()));
        assertEquals(1, context.connection().getClient().getActiveGameSession().getFireables().size());
    }

    @Test
    void oldDespawnCannotRemoveNewCrystalWithReusedWireId() {
        BattleContext context = battleContext((short) 0, true);
        SkillCrystal old = new SkillCrystal(17);
        var task = new com.jftse.emulator.server.core.task.DespawnCrystalTask(context.connection(), old);
        SkillCrystal replacement = new SkillCrystal(17);
        context.game().getSkillCrystals().add(replacement);
        task.run();
        assertEquals(List.of(replacement), List.copyOf(context.game().getSkillCrystals()));
        verify(GameManager.getInstance(), never()).sendPacketToAllClientsInSameGameSession(any(), any());
    }

    @Test
    void scheduledSpawnCannotFollowConnectionIntoReplacementSession() {
        BattleContext original = battleContext((short) 0, true);
        var task = new com.jftse.emulator.server.core.task.PlaceCrystalRandomlyTask(original.connection());
        BattleContext next = battleContext((short) 0, true);
        when(next.game().getLastCrystalId()).thenReturn(new AtomicInteger());
        when(next.game().getCrystalDeSpawnInterval()).thenReturn(new AtomicLong(1));
        GameSession replacement = next.connection().getClient().getActiveGameSession();
        when(original.connection().getClient().getActiveGameSession())
                .thenReturn(replacement);
        task.run();
        assertTrue(next.game().getSkillCrystals().isEmpty());
        verify(GameManager.getInstance(), never()).sendPacketToAllClientsInSameGameSession(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"spawn", "despawn"})
    void crystalMutationAndFollowupStayWithOriginalSessionDuringReplacement(String kind) throws Exception {
        BattleContext context = battleContext((short) 0, true);
        GameSession original = context.connection().getClient().getActiveGameSession();
        GameSession replacement = mock(GameSession.class);
        FTClient client = new FTClient() {
            @Override
            public GameSession getActiveGameSession() {
                return Integer.valueOf(7).equals(getGameSessionId()) ? original : replacement;
            }
        };
        client.setActiveGameSession(7);
        when(context.connection().getClient()).thenReturn(client);
        var storedCrystals = new ConcurrentLinkedDeque<SkillCrystal>();
        @SuppressWarnings("unchecked")
        ConcurrentLinkedDeque<SkillCrystal> crystals = mock(ConcurrentLinkedDeque.class,
                org.mockito.AdditionalAnswers.delegatesTo(storedCrystals));
        when(context.game().getSkillCrystals()).thenReturn(crystals);
        when(context.game().getLastCrystalId()).thenReturn(new AtomicInteger());
        when(context.game().getCrystalDeSpawnInterval()).thenReturn(new AtomicLong(1));
        SkillCrystal crystal = new SkillCrystal(17);
        if (kind.equals("despawn")) crystals.add(crystal);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        org.mockito.stubbing.Answer<Object> blockedMutation = invocation -> {
            entered.countDown();
            assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
            return kind.equals("spawn") ? storedCrystals.add(invocation.getArgument(0))
                    : storedCrystals.remove(invocation.getArgument(0));
        };
        if (kind.equals("spawn")) org.mockito.Mockito.doAnswer(blockedMutation).when(crystals).add(any());
        else org.mockito.Mockito.doAnswer(blockedMutation).when(crystals).remove(crystal);
        var recipients = new java.util.concurrent.CopyOnWriteArrayList<GameSession>();
        org.mockito.Mockito.doAnswer(invocation -> {
            recipients.add(client.getActiveGameSession());
            return null;
        }).when(GameManager.getInstance()).sendPacketToAllClientsInSameGameSession(any(), any());
        Runnable task = kind.equals("spawn") ? new com.jftse.emulator.server.core.task.PlaceCrystalRandomlyTask(context.connection())
                : new com.jftse.emulator.server.core.task.DespawnCrystalTask(context.connection(), crystal);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var work = executor.submit(task);
            try {
                assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
                var replace = executor.submit(() -> client.setActiveGameSession(8));
                try {
                    replace.get(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.TimeoutException expectedWhilePublicationOwnsClient) {
                }
                release.countDown();
                work.get(2, java.util.concurrent.TimeUnit.SECONDS);
                replace.get(2, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(List.of(original), recipients);
                var followup = org.mockito.ArgumentCaptor.forClass(Runnable.class);
                verify(GameManager.getInstance().getEventHandler()).createRunnableEvent(followup.capture(), eq(1L));
                followup.getValue().run();
                assertEquals(List.of(original), recipients, "Follow-up must not inherit the replacement match");
            } finally {
                release.countDown();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"spawn", "despawn"})
    void finishedMatchCannotMutateCrystalQueueOrScheduleFollowup(String kind) {
        BattleContext context = battleContext((short) 0, true);
        when(context.game().getLastCrystalId()).thenReturn(new AtomicInteger());
        when(context.game().getCrystalDeSpawnInterval()).thenReturn(new AtomicLong(1));
        SkillCrystal crystal = new SkillCrystal(17);
        context.game().getSkillCrystals().add(crystal);
        context.game().getFinished().set(true);
        Runnable task = kind.equals("spawn") ? new com.jftse.emulator.server.core.task.PlaceCrystalRandomlyTask(context.connection())
                : new com.jftse.emulator.server.core.task.DespawnCrystalTask(context.connection(), crystal);
        task.run();
        assertEquals(List.of(crystal), List.copyOf(context.game().getSkillCrystals()));
        verify(GameManager.getInstance(), never()).sendPacketToAllClientsInSameGameSession(any(), any());
        verify(GameManager.getInstance().getEventHandler(), never()).offer(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"live", "session", "phase"})
    void scriptedDotCommitsClampedDeathButCannotFollowReplacementSession(String replacementKind) {
        boolean replaceSession = replacementKind.equals("session");
        boolean replaced = !replacementKind.equals("live");
        GuardianContext context = guardianContext((short) 0, true);
        when(context.game().getGuardianCombatSystem()).thenReturn(new GuardianCombatSystem(context.game()));
        when(context.game().getFinished()).thenReturn(new AtomicBoolean(false));
        when(context.session().getFireables()).thenReturn(new ConcurrentLinkedDeque<>());
        PlayerBattleState target = context.game().getPlayerBattleStates().getFirst();
        target.getCurrentHealth().set(10);
        Skill skill = new Skill();
        skill.setId(3L);
        when(skillService.findSkillById(3L)).thenReturn(skill);
        var phases = mock(com.jftse.emulator.server.core.matchplay.guardian.PhaseManager.class);
        var currentPhase = new java.util.concurrent.atomic.AtomicReference<>(
                mock(com.jftse.emulator.server.core.matchplay.guardian.PhaseScript.class));
        when(phases.getCurrentPhase()).thenReturn(currentPhase);
        when(context.game().getPhaseManager()).thenReturn(phases);
        var task = new com.jftse.emulator.server.core.task.ApplyDoTTask(context.connection(), target, 3, 1000, 20);
        task.run();
        org.mockito.ArgumentCaptor<Runnable> callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(GameManager.getInstance().getEventHandler()).createRunnableEvent(callback.capture(), eq(1000L));
        if (replaceSession) {
            GameSession replacement = mock(GameSession.class);
            when(context.connection().getClient().getActiveGameSession()).thenReturn(replacement);
        }
        if (replacementKind.equals("phase")) currentPhase.set(mock(com.jftse.emulator.server.core.matchplay.guardian.PhaseScript.class));
        callback.getValue().run();
        assertEquals(replaced ? 10 : 0, target.getCurrentHealth().get());
        assertEquals(!replaced, target.isDead());
        verify(GameManager.getInstance(), times(replaced ? 0 : 1))
                .sendPacketToAllClientsInSameGameSession(any(), eq(context.connection()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void finishTaskOnlyEndsItsOriginalSession(boolean replaceSession) {
        BattleContext original = battleContext((short) 0, true);
        var task = new com.jftse.emulator.server.core.task.FinishGameTask(original.connection());
        BattleContext active = replaceSession ? battleContext((short) 0, true) : original;
        when(active.game().getFinished()).thenReturn(new AtomicBoolean(false));
        when(active.game().getScheduledFutures()).thenReturn(new ConcurrentLinkedDeque<>());
        var handleable = mock(com.jftse.emulator.server.core.matchplay.MatchplayHandleable.class);
        when(active.game().getHandleable()).thenReturn(handleable);
        GameSession session = active.connection().getClient().getActiveGameSession();
        when(original.connection().getClient().getActiveGameSession()).thenReturn(session);
        task.run();
        verify(handleable, times(replaceSession ? 0 : 1)).onEnd(any());
    }

    @Test
    void pickupLosingAtomicRemovalCannotGrantCrystal() {
        BattleContext context = battleContext((short) 0, true);
        SkillCrystal crystal = new SkillCrystal(17);
        ConcurrentLinkedDeque<SkillCrystal> crystals = new ConcurrentLinkedDeque<>() {
            @Override
            public boolean remove(Object value) {
                super.remove(value);
                return false;
            }
        };
        crystals.add(crystal);
        when(context.game().getSkillCrystals()).thenReturn(crystals);
        Queue<SkillCrystal> queue = new java.util.concurrent.ArrayBlockingQueue<>(2);
        when(context.roomPlayer().getPickedUpSkillCrystals()).thenReturn(queue);
        new PlayerPickingUpCrystalHandler().handle(context.connection(),
                CMSGPlayerPickupCrystal.builder().crystalId((short) 17).build());
        assertTrue(queue.isEmpty());
        verify(GameManager.getInstance(), never()).sendPacketToAllClientsInSameGameSession(any(), any());
    }

    @Test
    void petQuickSlotAndCrystalSkillUseFailClosedBeforeInventoryLookup() {
        BattleContext context = battleContext((short) 2, true);
        CMSGPlayerUseSkill quickSlotPacket = CMSGPlayerUseSkill.builder()
                .attackerPosition((byte) 2)
                .targetPosition((byte) 1)
                .isQuickSlot(true)
                .skillIndex((byte) 1)
                .build();
        CMSGPlayerUseSkill crystalPacket = CMSGPlayerUseSkill.builder()
                .attackerPosition((byte) 2)
                .targetPosition((byte) 1)
                .isQuickSlot(false)
                .skillIndex((byte) 1)
                .build();

        PlayerUseSkillHandler handler = new PlayerUseSkillHandler();
        handler.handle(context.connection(), quickSlotPacket);
        handler.handle(context.connection(), crystalPacket);

        verify(skillService, never()).findSkillByIndex(org.mockito.ArgumentMatchers.anyInt());
    }

    @ParameterizedTest
    @CsvSource({"18,1,live", "17,2,live", "17,1,live", "17,1,disconnected", "17,1,replacement"})
    void crystalValidationConsumesOnlyMatchingHead(int crystalId, byte skillIndex, String membership) {
        BattleContext context = battleContext((short) 0, true);
        SkillCrystal crystal = new SkillCrystal(17);
        crystal.setSkillIndex(1);
        SkillCrystal next = new SkillCrystal(19);
        Queue<SkillCrystal> crystals = new ConcurrentLinkedDeque<>(List.of(crystal, next));
        when(context.roomPlayer().getPickedUpSkillCrystals()).thenReturn(crystals);
        FTClient peer = matchClient();
        FTConnection peerConnection = mock(FTConnection.class);
        when(peer.getConnection()).thenReturn(peerConnection);
        when(peerConnection.getId()).thenReturn(mock(io.netty.channel.ChannelId.class));
        GameSession session = context.connection().getClient().getActiveGameSession();
        Room room = context.connection().getClient().getActiveRoom();
        when(peer.getActiveRoom()).thenReturn(room);
        when(peer.getActiveGameSession()).thenReturn(membership.equals("live") ? session : membership.equals("replacement") ? new GameSession() : null);
        session.getClients().add(peer);

        new PlayerUseSkillHandler().handle(context.connection(), CMSGPlayerUseSkill.builder()
                .attackerPosition((byte) 0)
                .targetPosition((byte) 1)
                .isQuickSlot(false)
                .sourceValue(crystalId)
                .skillIndex(skillIndex)
                .build());

        boolean accepted = crystalId == 17 && skillIndex == 1;
        assertEquals(accepted ? List.of(next) : List.of(crystal, next), new ArrayList<>(crystals));
        verify(skillService, times(accepted ? 1 : 0)).findSkillByIndex(skillIndex);
        verify(peerConnection, times(accepted && membership.equals("live") ? 1 : 0)).sendTCP(any());
        verify(context.connection(), never()).sendTCP(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void crystalSwapRequiresTwoCrystals(int count) {
        BattleContext context = battleContext((short) 0, true);
        Queue<SkillCrystal> crystals = new ConcurrentLinkedDeque<>();
        for (int index = 0; index < count; index++) {
            crystals.add(new SkillCrystal(index));
        }
        List<SkillCrystal> expected = new ArrayList<>(crystals);
        if (count == 2) Collections.reverse(expected);
        when(context.roomPlayer().getPickedUpSkillCrystals()).thenReturn(crystals);
        PlayerPocketService pockets = mock(PlayerPocketService.class);
        when(ServiceManager.getInstance().getPlayerPocketService()).thenReturn(pockets);
        PlayerPocket item = mock(PlayerPocket.class);
        when(item.getItemIndex()).thenReturn(21);
        when(item.getCategory()).thenReturn("SPECIAL");
        when(item.getItemCount()).thenReturn(1);
        when(pockets.getItemAsPocket(7L, context.roomPlayer().getPocketId())).thenReturn(item);
        when(pockets.decrementPocketItemCount(item)).thenReturn(item);

        new SwapQuickSlotItemsHandler().handle(context.connection(),
                CMSGSwapSpell.builder().itemPocketId(7).build());

        assertEquals(expected, new ArrayList<>(crystals));
        verify(pockets, times(count == 2 ? 1 : 0)).decrementPocketItemCount(item);
        verify(GameManager.getInstance(), times(count == 2 ? 1 : 0))
                .sendPacketToAllClientsInSameGameSession(any(), eq(context.connection()));
    }

    @Test
    void ownerCannotUseOpponentActor() {
        BattleContext context = battleContext((short) 1, false);
        CMSGPlayerUseSkill packet = CMSGPlayerUseSkill.builder()
                .attackerPosition((byte) 1)
                .targetPosition((byte) 0)
                .isQuickSlot(true)
                .skillIndex((byte) 1)
                .build();

        new PlayerUseSkillHandler().handle(context.connection(), packet);

        verify(skillService, never()).findSkillByIndex(org.mockito.ArgumentMatchers.anyInt());
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "false,true,false", "false,true,true",
            "true,false,false", "true,true,false"})
    void damageCommitsHealthAndSendsIdenticalStateToReporterAndPeer(
            boolean guardian, boolean pets, boolean dedicated) {
        FTConnection connection;
        PlayerBattleState target;
        short attacker;
        if (guardian) {
            GuardianContext context = guardianContext((short) 10, false);
            connection = context.connection();
            when(context.roomPlayer().isMaster()).thenReturn(true);
            when(context.game().getGuardianCombatSystem()).thenReturn(new GuardianCombatSystem(context.game()));
            target = context.game().getPlayerBattleStates().getLast();
            attacker = 10;
        } else {
            BattleContext context = battleContext((short) 0, true);
            connection = context.connection();
            when(context.game().getFinished()).thenReturn(new AtomicBoolean(false));
            when(context.game().isRedTeam(0)).thenReturn(true);
            when(context.game().isBlueTeam(1)).thenReturn(true);
            when(context.game().getPlayerCombatSystem()).thenReturn(new PlayerCombatSystem(context.game()));
            target = context.game().getPlayerBattleStates().stream()
                    .filter(state -> state.getPosition() == 1).findFirst().orElseThrow();
            attacker = 0;
        }
        GameSession session = connection.getClient().getActiveGameSession();
        when(session.hasOwnedPetSeats()).thenReturn(pets);
        when(session.isDedicatedBattlemonRoom()).thenReturn(dedicated);
        when(session.tryConsumeSkillHit(anyInt(), anyInt(), anyInt(), anyLong())).thenReturn(true);
        FTClient peer = matchClient();
        FTConnection peerConnection = mock(FTConnection.class);
        when(peer.getConnection()).thenReturn(peerConnection);
        when(peer.getActiveGameSession()).thenReturn(session);
        Room peerRoom = connection.getClient().getActiveRoom();
        when(peer.getActiveRoom()).thenReturn(peerRoom);
        session.getClients().add(peer);
        org.mockito.Mockito.doAnswer(invocation -> {
            assertTrue(Thread.holdsLock(session.getMatchplayGame()), "HP mutation and publication share game lock");
            return invocation.callRealMethod();
        }).when(GameManager.getInstance())
                .sendPacketToAllClientsInSameGameSession(any(), eq(connection));
        Skill skill = new Skill();
        skill.setId(6L);
        skill.setDamage(-1);
        when(skillService.findSkillById(6L)).thenReturn(skill);

        new SpellHitsTargetHandler().handle(connection, CMSGSpellHitsTarget.builder()
                .attackerPosition(attacker).targetPosition((short) 1).skillId((byte) 6)
                .applySkillEffect((byte) 0).build());

        org.mockito.ArgumentCaptor<com.jftse.server.core.protocol.IPacket> sent =
                org.mockito.ArgumentCaptor.forClass(com.jftse.server.core.protocol.IPacket.class);
        verify(connection).sendTCP(sent.capture());
        verify(peerConnection).sendTCP(org.mockito.ArgumentMatchers.same(sent.getValue()));
        assertEquals(99, target.getCurrentHealth().get());
        assertEquals(0x184E, sent.getValue().getPacketId());
        java.nio.ByteBuffer bytes = java.nio.ByteBuffer.wrap(sent.getValue().toBytes())
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, bytes.getShort(8));
        assertEquals(99, bytes.getShort(10));
        assertEquals(attacker, bytes.getShort(12));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {6, 10})
    void secondDamageCannotCommitOrPublishWhileFirstPublicationIsBlocked(int firstSkill) throws Exception {
        BattleContext context = battleContext((short) 0, true);
        when(context.game().getPlayerCombatSystem()).thenReturn(new PlayerCombatSystem(context.game()));
        Skill skill = new Skill();
        skill.setId(6L);
        skill.setDamage(-1);
        when(skillService.findSkillById(6L)).thenReturn(skill);
        Skill shield = new Skill();
        shield.setId(10L);
        shield.setDamage(1);
        when(skillService.findSkillById(10L)).thenReturn(shield);
        short firstHp = (short) (firstSkill == 10 ? 100 : 99);
        FTConnection reporter = context.connection();
        GameSession reporterSession = reporter.getClient().getActiveGameSession();
        FTClient peer = matchClient();
        FTConnection peerConnection = mock(FTConnection.class);
        when(peer.getConnection()).thenReturn(peerConnection);
        when(peer.getActiveGameSession()).thenReturn(reporterSession);
        Room peerRoom = reporter.getClient().getActiveRoom();
        when(peer.getActiveRoom()).thenReturn(peerRoom);
        reporter.getClient().getActiveGameSession().getClients().add(peer);
        org.mockito.Mockito.doCallRealMethod().when(GameManager.getInstance())
                .sendPacketToAllClientsInSameGameSession(any(), eq(reporter));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        List<Short> reporterHp = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<Short> peerHp = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicInteger sends = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (sends.getAndIncrement() == 0) {
                entered.countDown();
                assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
            }
            com.jftse.server.core.protocol.IPacket packet = invocation.getArgument(0);
            reporterHp.add(java.nio.ByteBuffer.wrap(packet.toBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN).getShort(10));
            return null;
        }).when(reporter).sendTCP(any());
        org.mockito.Mockito.doAnswer(invocation -> {
            com.jftse.server.core.protocol.IPacket packet = invocation.getArgument(0);
            peerHp.add(java.nio.ByteBuffer.wrap(packet.toBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN).getShort(10));
            return null;
        }).when(peerConnection).sendTCP(any());
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        AtomicInteger reports = new AtomicInteger();
        Runnable hit = () -> {
            try {
                new SpellHitsTargetHandler().handle(reporter, CMSGSpellHitsTarget.builder()
                        .attackerPosition((short) 0).targetPosition((short) 1)
                        .skillId((byte) (reports.getAndIncrement() == 0 ? firstSkill : 6)).applySkillEffect((byte) 0).build());
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        };
        Thread first = new Thread(hit);
        Thread second = new Thread(hit);
        first.start();
        try {
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            second.start();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            java.lang.management.ThreadInfo info;
            do {
                info = java.lang.management.ManagementFactory.getThreadMXBean().getThreadInfo(second.threadId());
                if (info == null || info.getLockOwnerId() == first.threadId()) break;
                Thread.yield();
            } while (System.nanoTime() < deadline);
            assertTrue(info != null && info.getLockOwnerId() == first.threadId());
            assertTrue(java.util.Set.of(System.identityHashCode(context.game()), System.identityHashCode(reporter.getClient()))
                    .contains(info.getLockInfo().getIdentityHashCode()));
            assertEquals(firstHp, context.game().getPlayerBattleStates().stream()
                    .filter(state -> state.getPosition() == 1).findFirst().orElseThrow().getCurrentHealth().get());
            assertTrue(reporterHp.isEmpty());
            assertTrue(peerHp.isEmpty());
        } finally {
            release.countDown();
            first.join(5000);
            second.join(5000);
        }
        org.junit.jupiter.api.Assertions.assertNull(failure.get());
        assertEquals(List.of(firstHp, (short) (firstHp - 1)), reporterHp);
        assertEquals(reporterHp, peerHp);
        assertEquals(firstHp - 1, context.game().getPlayerBattleStates().stream()
                .filter(state -> state.getPosition() == 1).findFirst().orElseThrow().getCurrentHealth().get());
    }

    @Test
    void guardianBallReportWithoutEffectPublishesGuardianHealthWithoutMutation() {
        GuardianContext context = guardianContext((short) 0, true);
        when(context.roomPlayer().isMaster()).thenReturn(true);
        when(context.game().getPlayerCombatSystem()).thenReturn(new PlayerCombatSystem(context.game()));
        when(context.game().getFinished()).thenReturn(new AtomicBoolean());
        org.mockito.Mockito.doCallRealMethod().when(GameManager.getInstance())
                .sendPacketToAllClientsInSameGameSession(any(), eq(context.connection()));

        new SpellHitsTargetHandler().handle(context.connection(), CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 0).targetPosition((short) 10).skillId((byte) 0)
                .applySkillEffect((byte) 1).build());

        var sent = org.mockito.ArgumentCaptor.forClass(com.jftse.server.core.protocol.IPacket.class);
        verify(context.connection()).sendTCP(sent.capture());
        java.nio.ByteBuffer bytes = java.nio.ByteBuffer.wrap(sent.getValue().toBytes())
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x184E, sent.getValue().getPacketId());
        assertEquals(10, bytes.getShort(8));
        assertEquals(100, bytes.getShort(10));
        assertEquals(100, context.game().getGuardianBattleStates().getFirst().getCurrentHealth().get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void finishedMatchRejectsCastAndHitBeforeSkillLookup(boolean guardian) {
        FTConnection connection;
        if (guardian) {
            GuardianContext context = guardianContext((short) 0, true);
            context.game().getFinished().set(true);
            connection = context.connection();
        } else {
            BattleContext context = battleContext((short) 0, true);
            context.game().getFinished().set(true);
            connection = context.connection();
        }
        new PlayerUseSkillHandler().handle(connection, CMSGPlayerUseSkill.builder()
                .attackerPosition((byte) 0).targetPosition((byte) 1).isQuickSlot(true)
                .quickSlotIndex((byte) -1).skillIndex((byte) 6).build());
        new SpellHitsTargetHandler().handle(connection, CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 0).targetPosition((short) 1).skillId((byte) 6).build());
        verify(skillService, never()).findSkillByIndex(anyInt());
        verify(skillService, never()).findSkillById(anyLong());
        verify(GameManager.getInstance(), never()).sendPacketToAllClientsInSameGameSession(any(), any());
    }

    @Test
    void guardianDeathClaimsLootOnceButRunsJdbcAfterPublicationLock() {
        GuardianContext context = guardianContext((short) 0, true);
        when(context.game().getGuardianCombatSystem()).thenReturn(new GuardianCombatSystem(context.game()));
        when(context.session().tryConsumeSkillHit(anyInt(), anyInt(), anyInt(), anyLong())).thenReturn(true);
        GuardianBattleState target = context.game().getGuardianBattleStates().getFirst();
        target.getCurrentHealth().set(1);
        target.getLooted().set(false);
        GuardianBattleState survivor = mock(GuardianBattleState.class);
        when(survivor.getPosition()).thenReturn(11);
        when(survivor.getCurrentHealth()).thenReturn(new AtomicInteger(100));
        context.game().getGuardianBattleStates().add(survivor);
        Skill skill = new Skill();
        skill.setId(6L);
        skill.setDamage(-1);
        when(skillService.findSkillById(6L)).thenReturn(skill);
        var jdbc = mock(com.jftse.server.core.jdbc.JdbcUtil.class);
        when(ServiceManager.getInstance().getJdbcUtil()).thenReturn(jdbc);
        org.mockito.Mockito.doAnswer(invocation -> {
            org.junit.jupiter.api.Assertions.assertFalse(Thread.holdsLock(context.game()));
            assertEquals(0, target.getCurrentHealth().get());
            verify(GameManager.getInstance()).sendPacketToAllClientsInSameGameSession(any(), eq(context.connection()));
            return null;
        }).when(jdbc).execute(any(com.jftse.server.core.jdbc.JdbcUtil.Operation.class));
        var packet = CMSGSpellHitsTarget.builder().attackerPosition((short) 0)
                .targetPosition((short) 10).skillId((byte) 6).applySkillEffect((byte) 0).build();
        var handler = new SpellHitsTargetHandler();
        handler.handle(context.connection(), packet);
        handler.handle(context.connection(), packet);
        assertTrue(target.getLooted().get());
        verify(jdbc).execute(any(com.jftse.server.core.jdbc.JdbcUtil.Operation.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hit", "finish", "dot", "two-loot", "jdbc-failure", "jdbc-failure-deferred", "jdbc-failure-replaced", "rejected"})
    void finalGuardianLootMustSettleBeforeSecondHitCanCompleteMatch(String secondAction) throws Exception {
        GuardianContext context = guardianContext((short) 0, true);
        when(context.game().getGuardianCombatSystem()).thenReturn(new GuardianCombatSystem(context.game()));
        when(context.session().tryConsumeSkillHit(anyInt(), anyInt(), anyInt(), anyLong())).thenReturn(true);
        when(context.game().getExpPot()).thenReturn(new AtomicInteger());
        when(context.game().getScheduledFutures()).thenReturn(new ConcurrentLinkedDeque<>());
        GuardianBattleState target = context.game().getGuardianBattleStates().getFirst();
        target.getCurrentHealth().set(1);
        target.getLooted().set(false);
        if (secondAction.equals("two-loot")) {
            GuardianBattleState other = mock(GuardianBattleState.class);
            when(other.getPosition()).thenReturn(11);
            when(other.getCurrentHealth()).thenReturn(new AtomicInteger(1));
            when(other.getLooted()).thenReturn(new AtomicBoolean());
            context.game().getGuardianBattleStates().add(other);
        }
        Skill skill = new Skill();
        skill.setId(6L);
        skill.setDamage(-1);
        when(skillService.findSkillById(6L)).thenReturn(skill);
        when(skillService.findSkillById(3L)).thenReturn(skill);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var bothEntered = new java.util.concurrent.CountDownLatch(2);
        var release = new java.util.concurrent.CountDownLatch(1);
        var jdbc = mock(com.jftse.server.core.jdbc.JdbcUtil.class);
        when(ServiceManager.getInstance().getJdbcUtil()).thenReturn(jdbc);
        org.mockito.Mockito.doAnswer(invocation -> {
            org.junit.jupiter.api.Assertions.assertFalse(Thread.holdsLock(context.game()));
            entered.countDown();
            bothEntered.countDown();
            assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
            if (secondAction.startsWith("jdbc-failure")) throw new IllegalStateException("loot unavailable");
            context.game().getExpPot().addAndGet(50);
            return null;
        }).when(jdbc).execute(any(com.jftse.server.core.jdbc.JdbcUtil.Operation.class));
        var completionPots = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
        var completion = mock(com.jftse.emulator.server.core.matchplay.MatchplayHandleable.class);
        when(context.game().getHandleable()).thenReturn(completion);
        org.mockito.Mockito.doAnswer(invocation -> {
            org.junit.jupiter.api.Assertions.assertFalse(Thread.holdsLock(context.game()));
            if (context.game().getFinished().compareAndSet(false, true))
                completionPots.add(context.game().getExpPot().get());
            return null;
        }).when(completion).onEnd(any());
        Object previousThreads = ReflectionTestUtils.getField(com.jftse.server.core.thread.ThreadManager.class, "instance");
        var threads = mock(com.jftse.server.core.thread.ThreadManager.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            if (secondAction.equals("rejected")) throw new java.util.concurrent.RejectedExecutionException("stopped");
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(threads).newTask(any(Runnable.class));
        ReflectionTestUtils.setField(com.jftse.server.core.thread.ThreadManager.class, "instance", threads);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var handler = new SpellHitsTargetHandler();
            var first = executor.submit(() -> handler.handle(context.connection(), CMSGSpellHitsTarget.builder()
                    .attackerPosition((short) 0).targetPosition((short) 10).skillId((byte) 6)
                    .applySkillEffect((byte) 0).build()));
            try {
                assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
                Runnable second = switch (secondAction) {
                    case "jdbc-failure" -> () -> {};
                    case "jdbc-failure-replaced" -> () -> when(context.connection().getClient().getActiveGameSession())
                            .thenReturn(mock(GameSession.class));
                    case "two-loot" -> () -> handler.handle(context.connection(), CMSGSpellHitsTarget.builder()
                            .attackerPosition((short) 0).targetPosition((short) 11).skillId((byte) 6)
                            .applySkillEffect((byte) 0).build());
                    case "finish" -> new com.jftse.emulator.server.core.task.FinishGameTask(context.connection());
                    case "dot" -> () -> {
                        context.game().getPlayerBattleStates().getLast().getCurrentHealth().set(0);
                        var player = context.game().getPlayerBattleStates().getFirst();
                        player.getCurrentHealth().set(1);
                        new com.jftse.emulator.server.core.task.ApplyDoTTask(context.connection(), player, 1, 1000, 20).run();
                        var tick = org.mockito.ArgumentCaptor.forClass(Runnable.class);
                        verify(GameManager.getInstance().getEventHandler()).createRunnableEvent(tick.capture(), eq(1000L));
                        tick.getValue().run();
                    };
                    default -> () -> handler.handle(context.connection(), CMSGSpellHitsTarget.builder()
                            .attackerPosition((short) 0).targetPosition((short) 10).skillId((byte) 0)
                            .applySkillEffect((byte) 1).build());
                };
                var secondTask = executor.submit(second);
                if (secondAction.equals("two-loot")) {
                    assertTrue(bothEntered.await(2, java.util.concurrent.TimeUnit.SECONDS));
                } else {
                    secondTask.get(2, java.util.concurrent.TimeUnit.SECONDS);
                }
                assertTrue(completionPots.isEmpty(), "Completion cannot observe an unsettled loot pot");
                release.countDown();
                secondTask.get(2, java.util.concurrent.TimeUnit.SECONDS);
                if (secondAction.startsWith("jdbc-failure") || secondAction.equals("rejected")) {
                    var failure = org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
                            () -> first.get(2, java.util.concurrent.TimeUnit.SECONDS));
                    assertTrue(failure.getCause() instanceof IllegalStateException ||
                            failure.getCause() instanceof java.util.concurrent.RejectedExecutionException);
                    assertTrue(context.game().getFinished().get(), "Failed loot must abort rather than strand a live match");
                    assertTrue(completionPots.isEmpty(), "Failed loot must not award incomplete success");
                    verify(context.connection(), secondAction.equals("jdbc-failure-replaced") ? never() : times(1)).close();
                    verify(GameManager.getInstance()).cleanupGameSession(any(), eq(context.session()), any());
                    org.junit.jupiter.api.Assertions.assertFalse(context.game().deferUntilLootComplete(() -> {}));
                    new com.jftse.emulator.server.core.task.FinishGameTask(context.connection()).run();
                    assertTrue(completionPots.isEmpty());
                    return;
                }
                first.get(2, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(List.of(secondAction.equals("two-loot") ? 100 : 50), completionPots);
            } finally {
                release.countDown();
            }
        } finally {
            ReflectionTestUtils.setField(com.jftse.server.core.thread.ThreadManager.class, "instance", previousThreads);
        }
    }

    @Test
    void bossTransitionCannotPublishOrScheduleIntoReplacementAfterLookup() throws Exception {
        GuardianContext context = guardianContext((short) 0, true);
        context.game().getGuardianBattleStates().getFirst().getCurrentHealth().set(0);
        when(context.game().getMap().getIsBossStage()).thenReturn(true);
        when(context.game().getMap().getTriggerBossTime()).thenReturn(1);
        when(context.game().getGuardianLevelLimit()).thenReturn(new AtomicInteger());
        when(context.game().getGuardiansInBossStage()).thenReturn(List.of());
        when(context.game().determineGuardians(any(), anyInt())).thenReturn(
                new ArrayList<>(java.util.Arrays.asList(null, null, null)));
        var boss = new com.jftse.entities.database.model.battle.BossGuardian();
        boss.setId(1L);
        boss.setGuardIndex(1);
        when(context.game().createGuardianBattleState(eq(false), eq(boss), eq((short) 10), anyInt()))
                .thenReturn(mock(GuardianBattleState.class));
        var scenarios = mock(com.jftse.server.core.service.ScenarioService.class);
        when(ServiceManager.getInstance().getScenarioService()).thenReturn(scenarios);
        var bosses = mock(com.jftse.server.core.service.BossGuardianService.class);
        when(ServiceManager.getInstance().getBossGuardianService()).thenReturn(bosses);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var lookups = new AtomicInteger();
        when(bosses.findBossGuardianById(1L)).thenAnswer(invocation -> {
            if (lookups.incrementAndGet() == 2) {
                org.junit.jupiter.api.Assertions.assertFalse(Thread.holdsLock(context.game()));
                entered.countDown();
                assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
            }
            return boss;
        });
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var handler = new SpellHitsTargetHandler();
            var hit = executor.submit(() -> handler.handle(context.connection(), CMSGSpellHitsTarget.builder()
                    .attackerPosition((short) 0).targetPosition((short) 10).skillId((byte) 0)
                    .applySkillEffect((byte) 1).build()));
            try {
                assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
                when(context.connection().getClient().getActiveGameSession()).thenReturn(mock(GameSession.class));
                release.countDown();
                hit.get(2, java.util.concurrent.TimeUnit.SECONDS);
                verify(GameManager.getInstance(), never()).sendPacketToAllClientsInSameGameSession(
                        any(com.jftse.emulator.server.core.packets.matchplay.S2CMatchplaySpawnBossBattle.class), any());
                assertTrue(context.session().getFireables().isEmpty());
            } finally {
                release.countDown();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"serve,live", "serve,replacement", "serve,finished", "attack,live", "attack,replacement",
            "attack,finished", "attack,dead", "attack,roster", "timer,live", "timer,replacement", "timer,finished"})
    void delayedGuardianTasksStayInTheirOriginalLiveSession(String kind, String state) {
        GuardianContext context = guardianContext((short) 0, true);
        context.game().getStageChangingToBoss().set(true);
        var scenario = new com.jftse.entities.database.model.scenario.MScenarios();
        scenario.setGameMode(com.jftse.entities.database.model.scenario.MScenarios.GameMode.GUARDIAN);
        when(context.game().getScenario()).thenReturn(scenario);
        when(context.game().getMap().getPlayTime()).thenReturn(1);
        var skills = mock(com.jftse.server.core.service.GuardianSkillsService.class);
        when(ServiceManager.getInstance().getGuardianSkillsService()).thenReturn(skills);
        var skill = new Skill();
        skill.setId(6L);
        when(skills.getRandomGuardianSkillBasedOnProbability(anyInt(), anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(), any(), any())).thenReturn(skill);
        Runnable task = switch (kind) {
            case "serve" -> new com.jftse.emulator.server.core.task.GuardianServeTask(context.connection());
            case "attack" -> new com.jftse.emulator.server.core.task.GuardianAttackTask(context.connection());
            default -> new com.jftse.emulator.server.core.task.DefeatTimerTask(context.connection(), context.session());
        };
        if (state.equals("replacement")) {
            GuardianContext replacement = guardianContext((short) 0, true);
            replacement.game().getStageChangingToBoss().set(true);
            when(context.connection().getClient().getActiveGameSession()).thenReturn(replacement.session());
        } else if (state.equals("finished")) {
            context.game().getFinished().set(true);
        } else if (state.equals("dead")) {
            context.game().getGuardianBattleStates().getFirst().getCurrentHealth().set(0);
        } else if (state.equals("roster")) {
            context.game().getGuardianBattleStates().clear();
            var replacement = mock(GuardianBattleState.class);
            when(replacement.getPosition()).thenReturn(10);
            when(replacement.getCurrentHealth()).thenReturn(new AtomicInteger(100));
            context.game().getGuardianBattleStates().add(replacement);
        }
        Object previous = ReflectionTestUtils.getField(com.jftse.server.core.thread.ThreadManager.class, "instance");
        var threads = mock(com.jftse.server.core.thread.ThreadManager.class);
        ReflectionTestUtils.setField(com.jftse.server.core.thread.ThreadManager.class, "instance", threads);
        try {
            task.run();
            int packets = state.equals("live") ? (kind.equals("serve") ? 2 : kind.equals("attack") ? 1 : 0) : 0;
            verify(GameManager.getInstance(), times(packets)).sendPacketToAllClientsInSameGameSession(any(), any());
            verify(GameManager.getInstance().getEventHandler(), times(state.equals("live") && !kind.equals("serve") ? 1 : 0)).offer(any());
            verify(threads, times(state.equals("live") && kind.equals("serve") ? 2 : 0)).newTask(any(Runnable.class));
        } finally {
            ReflectionTestUtils.setField(com.jftse.server.core.thread.ThreadManager.class, "instance", previous);
        }
    }

    @Test
    void guardianDeathDuringBlockedSkillLookupCannotGrantOrReschedule() throws Exception {
        GuardianContext context = guardianContext((short) 0, true);
        var skills = mock(com.jftse.server.core.service.GuardianSkillsService.class);
        when(ServiceManager.getInstance().getGuardianSkillsService()).thenReturn(skills);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var skill = new Skill();
        skill.setId(6L);
        when(skills.getRandomGuardianSkillBasedOnProbability(anyInt(), anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(), any(), any())).thenAnswer(invocation -> {
            org.junit.jupiter.api.Assertions.assertFalse(Thread.holdsLock(context.game()));
            org.junit.jupiter.api.Assertions.assertFalse(Thread.holdsLock(context.connection().getClient()));
            entered.countDown();
            assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
            return skill;
        });
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var future = executor.submit(new com.jftse.emulator.server.core.task.GuardianAttackTask(context.connection()));
            try {
                assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
                synchronized (context.game()) {
                    context.game().getGuardianBattleStates().getFirst().getCurrentHealth().set(0);
                }
                release.countDown();
                future.get(2, java.util.concurrent.TimeUnit.SECONDS);
                verify(context.session(), never()).authorizeSkillCast(anyInt(), anyInt(), anyLong());
                verify(GameManager.getInstance(), never()).sendPacketToAllClientsInSameGameSession(any(), any());
                verify(GameManager.getInstance().getEventHandler(), never()).offer(any());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void ownerCannotApplySpellDamageAsOpponentActor() {
        BattleContext context = battleContext((short) 1, false);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 0)
                .skillId((byte) 6)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(skillService, never()).findSkillById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void battlemonReviveRestoresADeadTeamActor() throws Exception {
        BattleContext context = battleContext((short) 0, true);
        Skill revive = new Skill();
        revive.setId(5L);
        revive.setDamage(50);
        when(skillService.findSkillById(5L)).thenReturn(revive);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        PlayerBattleState revivedPet = context.game().getPlayerBattleStates().stream()
                .filter(state -> state.getPosition() == 2)
                .findFirst()
                .orElseThrow();
        when(combatSystem.reviveAnyPlayer((short) 50, 0)).thenReturn(revivedPet);

        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 0)
                .targetPosition((short) 2)
                .skillId((byte) 5)
                .build();
        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(skillService).findSkillById(5L);
        verify(combatSystem).reviveAnyPlayer((short) 50, 0);
    }

    @Test
    void petOriginatedSpellDamagesBattlemonTarget() throws Exception {
        BattleContext context = battleContext((short) 2, true);
        Skill skill = new Skill();
        skill.setId(6L);
        skill.setDamage(-10);
        when(skillService.findSkillById(6L)).thenReturn(skill);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        when(combatSystem.dealDamage(2, 3, (short) -10, false, false, skill)).thenReturn((short) 90);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 2)
                .targetPosition((short) 3)
                .skillId((byte) 6)
                .attackerBuffId1((byte) -1)
                .attackerBuffId2((byte) -1)
                .receiverBuffId1((byte) -1)
                .receiverBuffId2((byte) -1)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem).dealDamage(2, 3, (short) -10, false, false, skill);
    }

    @Test
    void guardianHealAppliesToBattlemonTarget() throws Exception {
        BattleContext context = battleContext((short) 0, true);
        Skill heal = new Skill();
        heal.setId(2L);
        heal.setDamage(15);
        when(skillService.findSkillById(2L)).thenReturn(heal);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        when(combatSystem.heal(2, (short) 15)).thenReturn((short) 100);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 4)
                .targetPosition((short) 2)
                .skillId((byte) 2)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem).heal(2, (short) 15);
    }

    @Test
    void guardianShieldEffectAppliesToBattlemonTarget() throws Exception {
        BattleContext context = battleContext((short) 0, true);
        Skill shield = new Skill();
        shield.setId(10L);
        shield.setDamage(1);
        when(skillService.findSkillById(10L)).thenReturn(shield);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        when(combatSystem.dealDamage(4, 2, (short) 1, false, false, shield)).thenReturn((short) 100);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 4)
                .targetPosition((short) 2)
                .skillId((byte) 10)
                .attackerBuffId1((byte) -1)
                .attackerBuffId2((byte) -1)
                .receiverBuffId1((byte) -1)
                .receiverBuffId2((byte) -1)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem).dealDamage(4, 2, (short) 1, false, false, shield);
    }

    @Test
    void petOriginatedBallLossUsesBattleCombatGeometry() throws Exception {
        BattleContext context = battleContext((short) 2, true);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        when(combatSystem.dealDamageOnBallLoss(2, 1, false)).thenReturn((short) 99);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 2)
                .targetPosition((short) 1)
                .skillId((byte) 0)
                .damageType((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem).dealDamageOnBallLoss(2, 1, false);
    }

    @Test
    void guardianServeSentinelFromGameplayEndpointUsesBattleCombatGeometry() throws Exception {
        BattleContext context = battleContext((short) 0, false);
        FTClient client = context.connection().getClient();
        when(client.getActiveGameSession().isGameplayEndpoint(client)).thenReturn(true);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        when(combatSystem.dealDamageOnBallLoss(4, 1, false)).thenReturn((short) 99);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 4)
                .targetPosition((short) 1)
                .skillId((byte) 0)
                .damageType((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem).dealDamageOnBallLoss(4, 1, false);
    }

    @Test
    void guardianServeSentinelFromNonGameplayEndpointFailsClosed() {
        BattleContext context = battleContext((short) 0, false);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 4)
                .targetPosition((short) 1)
                .skillId((byte) 0)
                .damageType((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(context.game(), never()).getPlayerCombatSystem();
    }

    @Test
    void guardianModeRejectsAPlayerActorNotOwnedByTheReportingEndpoint() {
        GuardianContext context = guardianContext((short) 1, false);
        when(context.session().hasOwnedPetSeats()).thenReturn(false);
        CMSGPlayerUseSkill packet = CMSGPlayerUseSkill.builder()
                .attackerPosition((byte) 1)
                .targetPosition((byte) 10)
                .isQuickSlot(true)
                .skillIndex((byte) 1)
                .build();

        new PlayerUseSkillHandler().handle(context.connection(), packet);

        verify(skillService, never()).findSkillByIndex(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void guardianModeRejectsNonexistentGuardianAttackersAndTargets() {
        GuardianContext context = guardianContext((short) 0, true);
        CMSGPlayerUseSkill nonexistentAttacker = CMSGPlayerUseSkill.builder()
                .attackerPosition((byte) 12)
                .targetPosition((byte) 0)
                .skillIndex((byte) 1)
                .build();
        CMSGSpellHitsTarget nonexistentTarget = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 0)
                .targetPosition((short) 12)
                .skillId((byte) 1)
                .build();

        new PlayerUseSkillHandler().handle(context.connection(), nonexistentAttacker);
        new SpellHitsTargetHandler().handle(context.connection(), nonexistentTarget);

        verify(skillService, never()).findSkillByIndex(org.mockito.ArgumentMatchers.anyInt());
        verify(skillService, never()).findSkillById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void guardianCastRequiresTheMasterAndAServerGrant() {
        GuardianContext context = guardianContext((short) 0, true);
        Skill skill = new Skill();
        skill.setId(9L);
        skill.setDamage(-1);
        when(skillService.findSkillByIndex(8)).thenReturn(skill);
        CMSGPlayerUseSkill packet = CMSGPlayerUseSkill.builder()
                .attackerPosition((byte) 10)
                .targetPosition((byte) 0)
                .skillIndex((byte) 8)
                .build();

        new PlayerUseSkillHandler().handle(context.connection(), packet);
        verify(skillService, never()).findSkillByIndex(8);

        when(context.roomPlayer().isMaster()).thenReturn(true);
        new PlayerUseSkillHandler().handle(context.connection(), packet);
        verify(context.session(), never()).authorizeSkillHits(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong());

        when(context.session().tryConsumeSkillCast(org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq(8),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        new PlayerUseSkillHandler().handle(context.connection(), packet);
        verify(context.session()).authorizeSkillHits(
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(-1),
                org.mockito.ArgumentMatchers.eq(9), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void guardianHostCanReportSecondPlayerBallDamage() throws Exception {
        GuardianContext context = guardianContext((short) 1, false);
        GuardianCombatSystem combatSystem = mock(GuardianCombatSystem.class);
        PlayerCombatSystem playerCombatSystem = mock(PlayerCombatSystem.class);
        when(context.roomPlayer().isMaster()).thenReturn(true);
        when(context.game().getGuardianCombatSystem()).thenReturn(combatSystem);
        when(context.game().getPlayerCombatSystem()).thenReturn(playerCombatSystem);
        when(combatSystem.dealDamageOnBallLoss(1, 10, false)).thenReturn((short) 99);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 10)
                .skillId((byte) 0)
                .damageType((byte) 0)
                .applySkillEffect((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem).dealDamageOnBallLoss(1, 10, false);
    }

    @Test
    void nonHostGuardianEndpointCannotReportAnotherPlayersBallDamage() throws Exception {
        GuardianContext context = guardianContext((short) 1, false);
        GuardianCombatSystem combatSystem = mock(GuardianCombatSystem.class);
        when(context.game().getGuardianCombatSystem()).thenReturn(combatSystem);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 10)
                .skillId((byte) 0)
                .damageType((byte) 0)
                .applySkillEffect((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem, never()).dealDamageOnBallLoss(1, 10, false);
    }

    @Test
    void guardianHostOutsideGameplayEndpointCannotReportSecondPlayerBallDamage() throws Exception {
        GuardianContext context = guardianContext((short) 1, false);
        GuardianCombatSystem combatSystem = mock(GuardianCombatSystem.class);
        FTClient client = context.connection().getClient();
        when(context.roomPlayer().isMaster()).thenReturn(true);
        when(context.session().isGameplayEndpoint(client)).thenReturn(false);
        when(context.game().getGuardianCombatSystem()).thenReturn(combatSystem);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 10)
                .skillId((byte) 0)
                .damageType((byte) 0)
                .applySkillEffect((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem, never()).dealDamageOnBallLoss(1, 10, false);
    }

    @Test
    void nonHostGuardianEndpointCannotReportGuardianHitAgainstAnotherPlayer() {
        GuardianContext context = guardianContext((short) 0, true);
        when(context.session().tryConsumeSkillHit(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 10)
                .targetPosition((short) 1)
                .skillId((byte) 9)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(skillService, never()).findSkillById(9L);
    }

    @Test
    void guardianHostCanReportGuardianHitAgainstSecondPlayer() {
        GuardianContext context = guardianContext((short) 0, true);
        when(context.roomPlayer().isMaster()).thenReturn(true);
        when(context.session().tryConsumeSkillHit(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 10)
                .targetPosition((short) 1)
                .skillId((byte) 9)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(skillService).findSkillById(9L);
    }

    @Test
    void guardianHostAuthorizedSpellDamagesGuardianForSecondPlayer() throws Exception {
        GuardianContext context = guardianContext((short) 1, false);
        GuardianCombatSystem combatSystem = mock(GuardianCombatSystem.class);
        Skill skill = new Skill();
        skill.setId(9L);
        skill.setDamage(-5);
        when(context.roomPlayer().isMaster()).thenReturn(true);
        when(context.game().getGuardianCombatSystem()).thenReturn(combatSystem);
        when(context.session().tryConsumeSkillHit(org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(9),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
        when(skillService.findSkillById(9L)).thenReturn(skill);
        when(combatSystem.dealDamage(1, 10, (short) -5, true, false, skill)).thenReturn((short) 95);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 10)
                .skillId((byte) 9)
                .applySkillEffect((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem).dealDamage(1, 10, (short) -5, true, false, skill);
    }

    @Test
    void guardianHostAuthorizedHealAndShieldReachSecondPlayer() throws Exception {
        GuardianContext context = guardianContext((short) 1, false);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        GuardianCombatSystem guardianCombatSystem = mock(GuardianCombatSystem.class);
        Skill heal = new Skill();
        heal.setId(9L);
        heal.setDamage(10);
        Skill shield = new Skill();
        shield.setId(10L);
        shield.setDamage(1);
        when(context.roomPlayer().isMaster()).thenReturn(true);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        when(context.game().getGuardianCombatSystem()).thenReturn(guardianCombatSystem);
        when(context.session().tryConsumeSkillHit(org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(9),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
        when(context.session().tryConsumeSkillHit(org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
        when(skillService.findSkillById(9L)).thenReturn(heal);
        when(skillService.findSkillById(10L)).thenReturn(shield);
        when(combatSystem.heal(1, (short) 10)).thenReturn((short) 100);
        when(guardianCombatSystem.dealDamageToPlayer(
                1, 1, (short) 1, true, false, shield)).thenReturn((short) 100);
        CMSGSpellHitsTarget healPacket = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 1)
                .skillId((byte) 9)
                .applySkillEffect((byte) 0)
                .build();
        CMSGSpellHitsTarget shieldPacket = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 1)
                .skillId((byte) 10)
                .applySkillEffect((byte) 0)
                .build();

        SpellHitsTargetHandler handler = new SpellHitsTargetHandler();
        handler.handle(context.connection(), healPacket);
        handler.handle(context.connection(), shieldPacket);

        verify(combatSystem).heal(1, (short) 10);
        verify(guardianCombatSystem).dealDamageToPlayer(
                1, 1, (short) 1, true, false, shield);
    }

    @Test
    void guardianHostNonzeroHitWithoutGrantIsRejected() throws Exception {
        GuardianContext context = guardianContext((short) 1, false);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        Skill heal = new Skill();
        heal.setId(9L);
        heal.setDamage(10);
        when(context.roomPlayer().isMaster()).thenReturn(true);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        when(skillService.findSkillById(9L)).thenReturn(heal);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 1)
                .skillId((byte) 9)
                .applySkillEffect((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem, never()).heal(1, (short) 10);
    }

    @Test
    void nonHostGuardianEndpointCannotReportAnotherPlayersSpell() {
        GuardianContext context = guardianContext((short) 1, false);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 10)
                .skillId((byte) 9)
                .applySkillEffect((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(skillService, never()).findSkillById(9L);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "battle,10,0", "battle,20,0", "guardian-player,10,0", "guardian-player,20,0",
            "guardian-enemy,10,0", "guardian-enemy,20,0",
            "battle,10,50", "battle,10,100", "battle,20,50", "battle,20,100",
            "guardian-player,10,50", "guardian-player,10,100", "guardian-player,20,50", "guardian-player,20,100",
            "guardian-enemy,10,50", "guardian-enemy,10,100", "guardian-enemy,20,50", "guardian-enemy,20,100"})
    void acceptedPositiveOneSkillCannotPublishHealthAboveMaximum(String mode, int skillId, int initialHp) {
        FTConnection connection;
        com.jftse.server.core.matchplay.battle.BattleState target;
        if (mode.equals("battle")) {
            var context = battleContext((short) 0, true);
            connection = context.connection();
            target = context.game().getPlayerBattleStates().getLast();
            when(context.game().getPlayerCombatSystem()).thenReturn(new PlayerCombatSystem(context.game()));
        } else {
            var context = guardianContext((short) 0, true);
            connection = context.connection();
            when(context.roomPlayer().isMaster()).thenReturn(true);
            when(context.game().getGuardianCombatSystem()).thenReturn(new GuardianCombatSystem(context.game()));
            when(context.game().getPlayerCombatSystem()).thenReturn(new PlayerCombatSystem(context.game()));
            target = mode.equals("guardian-enemy") ? context.game().getGuardianBattleStates().getFirst()
                    : context.game().getPlayerBattleStates().getLast();
            if (target instanceof GuardianBattleState guardian) when(guardian.getMaxHealth()).thenReturn(100);
            when(context.session().tryConsumeSkillHit(anyInt(), anyInt(), anyInt(), anyLong())).thenReturn(true);
        }
        target.getCurrentHealth().set(initialHp);
        if (initialHp == 0 && target instanceof PlayerBattleState player) player.setDead(true);
        Skill shield = new Skill();
        shield.setId((long) skillId);
        shield.setDamage(1);
        when(skillService.findSkillById((long) skillId)).thenReturn(shield);
        new SpellHitsTargetHandler().handle(connection, CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 0).targetPosition((short) target.getPosition())
                .skillId((byte) skillId).applySkillEffect((byte) 0).build());
        if (initialHp == 0) {
            assertEquals(0, target.getCurrentHealth().get());
            if (target instanceof PlayerBattleState player) assertTrue(player.isDead());
            verify(GameManager.getInstance(), never()).sendPacketToAllClientsInSameGameSession(any(), eq(connection));
            return;
        }
        int expected = Math.min(100, initialHp + 1);
        assertEquals(expected, target.getCurrentHealth().get());
        var published = org.mockito.ArgumentCaptor.forClass(com.jftse.server.core.protocol.IPacket.class);
        verify(GameManager.getInstance()).sendPacketToAllClientsInSameGameSession(published.capture(), eq(connection));
        assertEquals(expected, java.nio.ByteBuffer.wrap(published.getValue().toBytes())
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).getShort(10));
    }

    @Test
    void staleOverlappingRostersCannotDeadlockTwoGamesPublishingUnderTheirOwnerLocks() throws Exception {
        BattleContext first = battleContext((short) 0, true);
        BattleContext second = battleContext((short) 0, true);
        FTClient firstClient = first.connection().getClient();
        FTClient secondClient = second.connection().getClient();
        firstClient.getActiveGameSession().getClients().add(secondClient);
        secondClient.getActiveGameSession().getClients().addFirst(firstClient);
        org.mockito.Mockito.doCallRealMethod().when(GameManager.getInstance()).sendPacketToAllClientsInSameGameSession(any(), any());
        var ownersLocked = new java.util.concurrent.CountDownLatch(2);
        var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        java.util.function.Consumer<BattleContext> publish = context -> {
            try {
                synchronized (context.game()) {
                    synchronized (context.connection().getClient()) {
                        ownersLocked.countDown();
                        assertTrue(ownersLocked.await(2, java.util.concurrent.TimeUnit.SECONDS));
                        GameManager.getInstance().sendPacketToAllClientsInSameGameSession(
                                new com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayDealDamage((short) 0, (short) 90, (short) 1, (byte) 6, 0, 0), context.connection());
                    }
                }
            } catch (Throwable failure) { error.set(failure); }
        };
        Thread left = Thread.ofPlatform().daemon().start(() -> publish.accept(first));
        Thread right = Thread.ofPlatform().daemon().start(() -> publish.accept(second));
        left.join(3000); right.join(3000);
        org.junit.jupiter.api.Assertions.assertFalse(left.isAlive() || right.isAlive(), "Different game locks cannot serialize overlapping stale recipient monitors");
        org.junit.jupiter.api.Assertions.assertNull(error.get());
        verify(first.connection()).sendTCP(any());
        verify(second.connection()).sendTCP(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"live", "disconnected", "replacement"})
    void matchBroadcastDoesNotFollowStaleGuestMembership(String membership) {
        BattleContext context = battleContext((short) 0, true);
        FTClient guest = matchClient();
        FTConnection socket = mock(FTConnection.class);
        when(guest.getConnection()).thenReturn(socket);
        GameSession original = context.connection().getClient().getActiveGameSession();
        Room guestRoom = context.connection().getClient().getActiveRoom();
        when(guest.getActiveRoom()).thenReturn(guestRoom);
        when(guest.getActiveGameSession()).thenReturn(membership.equals("replacement") ? new GameSession() : membership.equals("live") ? original : null);
        original.getClients().add(guest);
        org.mockito.Mockito.doCallRealMethod().when(GameManager.getInstance())
                .sendPacketToAllClientsInSameGameSession(any(), eq(context.connection()));
        GameManager.getInstance().sendPacketToAllClientsInSameGameSession(
                new com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayDealDamage((short) 0, (short) 90, (short) 1, (byte) 6, 0, 0), context.connection());
        verify(socket, times(membership.equals("live") ? 1 : 0)).sendTCP(any());
        verify(context.connection()).sendTCP(any());
    }

    @ParameterizedTest
    @CsvSource({"false,true,true", "false,false,true", "false,true,false",
            "true,true,true", "true,false,true", "true,true,false"})
    void sandglassExtendsOnlyAuthorizedExistingMatchTimer(boolean guardian, boolean timerPresent, boolean grant) {
        FTConnection connection;
        if (guardian) {
            var context = guardianContext((short) 0, true);
            connection = context.connection();
            when(context.roomPlayer().isMaster()).thenReturn(true);
        } else {
            connection = battleContext((short) 0, true).connection();
        }
        GameSession session = connection.getClient().getActiveGameSession();
        when(session.tryConsumeSkillHit(anyInt(), anyInt(), anyInt(), anyLong())).thenReturn(grant);
        RunnableEvent timer = RunnableEvent.builder().currentTime(0).delayMS(1000).runnable(() -> {}).build();
        when(session.getCountDownRunnable()).thenReturn(timerPresent ? timer : null);
        Skill skill = new Skill();
        skill.setId(38L);
        skill.setDamage(0);
        when(skillService.findSkillById(38L)).thenReturn(skill);
        new SpellHitsTargetHandler().handle(connection, CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 0).targetPosition((short) 0).skillId((byte) 38).build());
        assertEquals(!(timerPresent && grant), timer.shouldFire(1001));
        assertTrue(timer.shouldFire(61001));
        verify(GameManager.getInstance(), org.mockito.Mockito.times(timerPresent && grant ? 1 : 0))
                .sendPacketToAllClientsInSameGameSession(any(), eq(connection));
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> specialReports() {
        return java.util.stream.Stream.of("battle", "guardian-player", "guardian-enemy").flatMap(mode ->
                java.util.stream.IntStream.of(15, 63, 64, 40).boxed().flatMap(skill ->
                        java.util.stream.IntStream.of(0, 1).mapToObj(effect ->
                                org.junit.jupiter.params.provider.Arguments.of(mode, skill, effect))));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("specialReports")
    void specialDamageAndStatusBranchesPreserveHpAndAnimationContract(String mode, int skillId, int effect) throws Exception {
        Object oldConfig = ((java.util.concurrent.atomic.AtomicReference<?>) ReflectionTestUtils.getField(
                com.jftse.emulator.server.core.utils.BattleUtils.class, "statConfig")).get();
        var config = mock(com.jftse.server.core.shared.ServerConfService.class);
        when(GameManager.getInstance().getServerConfService()).thenReturn(config);
        when(config.get("StrengthDamageScale", Double.class)).thenReturn(0.35);
        when(config.get("StaminaDamageReductionScale", Double.class)).thenReturn(0.30);
        when(config.get("WillpowerBallDamageScale", Double.class)).thenReturn(0.52);
        when(config.get("BallBaseDamage", Integer.class)).thenReturn(10);
        when(config.get("BallMinDamage", Integer.class)).thenReturn(20);
        com.jftse.emulator.server.core.utils.BattleUtils.reloadStatConfig();
        try {
            FTConnection connection;
            com.jftse.server.core.matchplay.battle.BattleState target;
            if (mode.equals("battle")) {
                var context = battleContext((short) 0, true);
                connection = context.connection();
                target = context.game().getPlayerBattleStates().getLast();
                when(context.game().getPlayerCombatSystem()).thenReturn(new PlayerCombatSystem(context.game()));
            } else {
                var context = guardianContext((short) 0, true);
                connection = context.connection();
                when(context.roomPlayer().isMaster()).thenReturn(true);
                when(context.game().getGuardianCombatSystem()).thenReturn(new GuardianCombatSystem(context.game()));
                when(context.game().getPlayerCombatSystem()).thenReturn(new PlayerCombatSystem(context.game()));
                when(context.session().tryConsumeSkillHit(anyInt(), anyInt(), anyInt(), anyLong())).thenReturn(true);
                target = mode.equals("guardian-enemy") ? context.game().getGuardianBattleStates().getFirst()
                        : context.game().getPlayerBattleStates().getLast();
                if (target instanceof GuardianBattleState guardian) when(guardian.getMaxHealth()).thenReturn(100);
            }
            int damage = switch (skillId) { case 15 -> -8; case 63 -> -25; case 64 -> -10; default -> 0; };
            Skill skill = new Skill();
            skill.setId((long) skillId);
            skill.setDamage(damage);
            when(skillService.findSkillById((long) skillId)).thenReturn(skill);
            Skill animation = new Skill();
            animation.setId(3L);
            when(skillService.findSkillById(3L)).thenReturn(animation);
            new SpellHitsTargetHandler().handle(connection, CMSGSpellHitsTarget.builder()
                    .attackerPosition((short) 0).targetPosition((short) target.getPosition()).skillId((byte) skillId)
                    .applySkillEffect((byte) effect).attackerBuffId1((byte) 9).attackerBuffId2((byte) 9)
                    .receiverBuffId1((byte) 9).receiverBuffId2((byte) 9).build());
            boolean damageApplied = skillId != 40 && (effect == 0 || skillId == 15 || skillId == 63);
            int expected = damageApplied ? 100 + damage + (mode.equals("guardian-player") ? 3 : mode.equals("guardian-enemy") ? -3 : 0) : 100;
            assertEquals(expected, target.getCurrentHealth().get());
            var packets = org.mockito.ArgumentCaptor.forClass(com.jftse.server.core.protocol.IPacket.class);
            verify(GameManager.getInstance()).sendPacketToAllClientsInSameGameSession(packets.capture(), eq(connection));
            var bytes = java.nio.ByteBuffer.wrap(packets.getValue().toBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            assertEquals(expected, bytes.getShort(10));
            assertEquals(skillId == 64 || effect == 1 && (skillId == 15 || skillId == 63) ? 3 : skillId, bytes.get(14));
        } finally {
            ReflectionTestUtils.invokeMethod(ReflectionTestUtils.getField(
                    com.jftse.emulator.server.core.utils.BattleUtils.class, "statConfig"), "set", oldConfig);
        }
    }

    private static FTClient matchClient() {
        FTClient client = mock(FTClient.class, org.mockito.Mockito.withSettings().useConstructor());
        org.mockito.Mockito.doCallRealMethod().when(client).matchMembership();
        org.mockito.Mockito.doCallRealMethod().when(client).sendMatchPacket(any(), any());
        return client;
    }

    private static BattleContext battleContext(short actorPosition, boolean actorOwned) {
        FTPlayer player = mock(FTPlayer.class);
        Room room = mock(Room.class);
        RoomPlayer roomPlayer = mock(RoomPlayer.class);
        when(roomPlayer.getPosition()).thenReturn((short) 0);

        MatchplayBattleGame game = mock(MatchplayBattleGame.class);
        when(game.getFinished()).thenReturn(new AtomicBoolean());
        ConcurrentLinkedDeque<PlayerBattleState> states = new ConcurrentLinkedDeque<>();
        for (short position = 0; position < 4; position++) {
            states.add(new PlayerBattleState(position, 100L + position, 100, 10, 10, 10, 10));
        }
        when(game.getPlayerBattleStates()).thenReturn(states);
        when(game.getSkillCrystals()).thenReturn(new ConcurrentLinkedDeque<>());
        when(game.getCrystalSpawnInterval()).thenReturn(new AtomicLong(1));

        GameSession session = mock(GameSession.class);
        when(session.getMatchplayGame()).thenReturn(game);
        when(session.isDedicatedBattlemonRoom()).thenReturn(true);
        when(session.isActorOwnedBy(roomPlayer, actorPosition)).thenReturn(actorOwned);
        when(session.tryConsumeSkillHit(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        when(session.getFireables()).thenReturn(new ConcurrentLinkedDeque<>());

        FTClient client = matchClient();
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getActiveRoom()).thenReturn(room);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        when(client.getActiveGameSession()).thenReturn(session);
        when(session.isGameplayEndpoint(client)).thenReturn(actorOwned);

        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        when(client.getConnection()).thenReturn(connection);
        when(session.getClients()).thenReturn(new ConcurrentLinkedDeque<>(java.util.List.of(client)));
        return new BattleContext(connection, roomPlayer, game);
    }

    private static GuardianContext guardianContext(short actorPosition, boolean actorOwned) {
        FTPlayer player = mock(FTPlayer.class);
        Room room = mock(Room.class);
        RoomPlayer roomPlayer = mock(RoomPlayer.class);
        when(roomPlayer.getPosition()).thenReturn((short) 0);

        MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
        when(game.getFinished()).thenReturn(new AtomicBoolean());
        ReflectionTestUtils.setField(game, "deferredLootActions", new ArrayList<Runnable>());
        org.mockito.Mockito.doCallRealMethod().when(game).beginLootUpdate();
        org.mockito.Mockito.doCallRealMethod().when(game).completeLootUpdate();
        org.mockito.Mockito.doCallRealMethod().when(game).failLootUpdates();
        when(game.deferUntilLootComplete(any())).thenCallRealMethod();
        SMaps map = mock(SMaps.class);
        ConcurrentLinkedDeque<PlayerBattleState> playerStates = new ConcurrentLinkedDeque<>();
        playerStates.add(new PlayerBattleState((short) 0, 100L, 100, 10, 10, 10, 10));
        playerStates.add(new PlayerBattleState((short) 1, 200L, 100, 10, 10, 10, 10));
        GuardianBattleState guardianState = mock(GuardianBattleState.class);
        when(guardianState.getPosition()).thenReturn(10);
        when(guardianState.getCurrentHealth()).thenReturn(new AtomicInteger(100));
        when(guardianState.getLooted()).thenReturn(new AtomicBoolean(true));
        when(game.getPlayerBattleStates()).thenReturn(playerStates);
        when(game.getGuardianBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(java.util.List.of(guardianState)));
        when(game.getMap()).thenReturn(map);
        when(game.getIsHardMode()).thenReturn(new AtomicBoolean(false));
        when(game.getStageChangingToBoss()).thenReturn(new AtomicBoolean(false));
        when(game.getBossBattleActive()).thenReturn(new AtomicBoolean(false));

        GameSession session = mock(GameSession.class);
        when(session.getMatchplayGame()).thenReturn(game);
        when(session.hasOwnedPetSeats()).thenReturn(true);
        when(session.getFireables()).thenReturn(new ConcurrentLinkedDeque<>());
        when(session.isActorOwnedBy(roomPlayer, actorPosition)).thenReturn(actorOwned);

        FTClient client = matchClient();
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getActiveRoom()).thenReturn(room);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        when(client.getActiveGameSession()).thenReturn(session);
        when(session.isGameplayEndpoint(client)).thenReturn(true);

        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        when(client.getConnection()).thenReturn(connection);
        when(session.getClients()).thenReturn(new ConcurrentLinkedDeque<>(java.util.List.of(client)));
        return new GuardianContext(connection, roomPlayer, session, game);
    }

    private record BattleContext(FTConnection connection, RoomPlayer roomPlayer, MatchplayBattleGame game) {
    }

    private record GuardianContext(FTConnection connection, RoomPlayer roomPlayer, GameSession session,
                                   MatchplayGuardianGame game) {
    }
}
