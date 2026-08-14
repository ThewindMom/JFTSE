package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.server.core.constants.PacketEventType;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.handler.matchplay.MatchplayItemRewardPickHandler;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.pet.PetStatistic;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.matchplay.MatchplayGame;
import com.jftse.emulator.server.core.matchplay.MatchplayHandleable;
import com.jftse.emulator.server.core.matchplay.MatchplayReward;
import com.jftse.emulator.server.core.matchplay.PlayerReward;
import com.jftse.emulator.server.core.matchplay.event.Fireable;
import com.jftse.emulator.server.core.matchplay.event.PacketEvent;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame;
import com.jftse.emulator.server.core.rabbit.MatchRallyStatsConsumer;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.core.task.AutoItemRewardPickerTask;
import com.jftse.emulator.server.core.task.DefeatTimerTask;
import com.jftse.emulator.server.core.task.FinishGameTask;
import com.jftse.emulator.server.core.task.GuardianAttackTask;
import com.jftse.emulator.server.core.task.GuardianServeTask;
import com.jftse.emulator.server.core.task.PlaceCrystalRandomlyTask;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.shared.packets.matchplay.CMSGPickupItemReward;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameSessionTest {
    @Test
    void unknownMatchplayGameDoesNotSilentlyBecomeGuardian() {
        GameSession session = new GameSession();

        assertThrows(IllegalArgumentException.class, () -> session.setMatchplayGame(new UnknownMatchplayGame()));
        assertNull(session.getMatchplayGame());
    }

    @Test
    void ordinaryBasicSessionIsOneHumanSeatPerClient() {
        GameSession session = new GameSession();
        RoomPlayer firstPlayer = roomPlayer(100L, (short) 0);
        RoomPlayer secondPlayer = roomPlayer(200L, (short) 1);
        session.getClients().add(clientFor(firstPlayer));
        session.getClients().add(clientFor(secondPlayer));

        session.initializeGameplayActorPositions();

        assertFalse(session.isDedicatedBattlemonRoom());
        assertFalse(session.hasOwnedPetSeats());
        assertEquals(List.of((short) 0, (short) 1), session.getGameplayActorPositions());
        assertTrue(session.isHumanSeat(0));
        assertTrue(session.isHumanSeat(1));
        assertTrue(session.isActorOwnedBy(firstPlayer, 0));
        assertFalse(session.isActorOwnedBy(firstPlayer, 1));
        assertEquals(0, session.getOwnerPositionForActor(0));
        assertEquals(1, session.getOwnerPositionForActor(1));
        assertNull(session.getOwnedPetSeat(100L));
    }

    @Test
    void battlemonActorsUseOwnerPositionPlusTwoAndOwnerEndpoint() {
        GameSession session = new GameSession(true);
        RoomPlayer firstPlayer = roomPlayer(100L, (short) 0);
        RoomPlayer secondPlayer = roomPlayer(200L, (short) 1);
        session.getClients().add(clientFor(firstPlayer));
        session.getClients().add(clientFor(secondPlayer));

        session.addOwnedPetSeat(firstPlayer, pet(10L, "First pet"));
        session.addOwnedPetSeat(secondPlayer, pet(20L, "Second pet"));
        session.initializeGameplayActorPositions();
        GameplayActor firstPet = session.getActor(2);
        GameplayActor secondPet = session.getActor(3);

        assertEquals((short) 2, firstPet.position());
        assertEquals((short) 3, secondPet.position());
        assertEquals(List.of((short) 0, (short) 1, (short) 2, (short) 3), session.getGameplayActorPositions());
        assertSame(firstPet, session.getOwnedPetSeat(100L));
        assertSame(secondPet, session.getOwnedPetSeat(200L));
        assertTrue(session.isHumanSeat(0));
        assertTrue(session.isHumanSeat(1));
        assertFalse(session.isHumanSeat(2));
        assertFalse(session.isHumanSeat(3));
        assertEquals((short) 0, firstPet.ownerPosition());
        assertEquals((short) 1, secondPet.ownerPosition());
        assertTrue(session.isActorOwnedBy(firstPlayer, 0));
        assertTrue(session.isActorOwnedBy(firstPlayer, 2));
        assertFalse(session.isActorOwnedBy(firstPlayer, 1));
        assertFalse(session.isActorOwnedBy(firstPlayer, 3));
    }

    @Test
    void ordinaryGuardianSessionCanOwnOneOrTwoOptionalPetActors() {
        GameSession session = new GameSession();
        RoomPlayer firstPlayer = roomPlayer(100L, (short) 0);
        RoomPlayer secondPlayer = roomPlayer(200L, (short) 1);
        session.getClients().add(clientFor(firstPlayer));
        session.getClients().add(clientFor(secondPlayer));

        session.addOwnedPetSeat(firstPlayer, pet(10L, "First pet"));
        assertEquals(1, session.getOwnedPetSeats().size());
        session.addOwnedPetSeat(secondPlayer, pet(20L, "Second pet"));
        session.initializeGameplayActorPositions();

        assertFalse(session.isDedicatedBattlemonRoom());
        assertEquals(List.of((short) 0, (short) 1, (short) 2, (short) 3),
                session.getGameplayActorPositions());
        assertTrue(session.isActorOwnedBy(firstPlayer, 2));
        assertTrue(session.isActorOwnedBy(secondPlayer, 3));
    }

    @Test
    void battlemonActorsRejectPositionsWithoutAHumanOwner() {
        GameSession session = new GameSession(true);
        RoomPlayer invalidOwner = roomPlayer(100L, (short) 2);

        assertThrows(IllegalArgumentException.class, () -> session.addOwnedPetSeat(invalidOwner, pet(10L, "Pet")));
        assertTrue(session.getOwnedPetSeats().isEmpty());
    }

    @Test
    void battlemonActorsRequireMatchingHumanEndpoint() {
        GameSession session = new GameSession(true);
        RoomPlayer owner = roomPlayer(100L, (short) 0);
        RoomPlayer differentPlayer = roomPlayer(200L, (short) 0);
        session.getClients().add(clientFor(differentPlayer));

        assertThrows(IllegalArgumentException.class, () -> session.addOwnedPetSeat(owner, pet(10L, "Pet")));
        assertTrue(session.getOwnedPetSeats().isEmpty());
    }

    @Test
    void battlemonActorsRejectDuplicateOwner() {
        GameSession session = new GameSession(true);
        RoomPlayer owner = roomPlayer(100L, (short) 0);
        session.getClients().add(clientFor(owner));
        session.addOwnedPetSeat(owner, pet(10L, "First pet"));

        assertThrows(IllegalStateException.class, () -> session.addOwnedPetSeat(owner, pet(20L, "Second pet")));
        assertEquals(1, session.getOwnedPetSeats().size());
        assertEquals(10L, session.getActor(2).pet().id());
    }

    @Test
    void gameplayActorRosterDoesNotChangeWhenAnEndpointDisconnects() {
        GameSession session = new GameSession(true);
        RoomPlayer firstPlayer = roomPlayer(100L, (short) 0);
        RoomPlayer secondPlayer = roomPlayer(200L, (short) 1);
        FTClient firstClient = clientFor(firstPlayer);
        FTClient secondClient = clientFor(secondPlayer);
        session.getClients().add(firstClient);
        session.getClients().add(secondClient);
        session.addOwnedPetSeat(firstPlayer, pet(10L, "First pet"));
        session.addOwnedPetSeat(secondPlayer, pet(20L, "Second pet"));
        session.initializeGameplayActorPositions();

        session.getClients().remove(secondClient);

        assertEquals(List.of((short) 0, (short) 1, (short) 2, (short) 3), session.getGameplayActorPositions());
        assertEquals(0, session.getOwnerPositionForActor(2));
        assertEquals(1, session.getOwnerPositionForActor(3));
    }

    @Test
    void battlemonSpectatorsAreNotGameplayEndpoints() {
        GameSession session = new GameSession(true);
        RoomPlayer firstPlayer = roomPlayer(100L, (short) 0);
        RoomPlayer secondPlayer = roomPlayer(200L, (short) 1);
        FTClient firstClient = clientFor(firstPlayer);
        FTClient secondClient = clientFor(secondPlayer);
        RoomPlayer spectatorPlayer = roomPlayer(300L, (short) 4);
        FTClient spectatorClient = clientFor(spectatorPlayer);
        session.getClients().add(firstClient);
        session.getClients().add(secondClient);
        session.getClients().add(spectatorClient);
        session.addOwnedPetSeat(firstPlayer, pet(10L, "First pet"));
        session.addOwnedPetSeat(secondPlayer, pet(20L, "Second pet"));
        session.initializeGameplayActorPositions();

        assertTrue(session.isGameplayEndpoint(firstClient));
        assertTrue(session.isGameplayEndpoint(secondClient));
        assertFalse(session.isGameplayEndpoint(spectatorClient));

        FTClient detachedSpectator = mock(FTClient.class);
        when(detachedSpectator.isSpectator()).thenReturn(true);
        session.getClients().add(detachedSpectator);
        assertFalse(session.isGameplayEndpoint(detachedSpectator));
    }

    @Test
    void sessionIdsRemainInTheClientCompatibleRangeAndRemovalUsesObjectIdentity() {
        Object previousManager = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        try {
            GameSessionManager manager = new GameSessionManager();
            manager.init();
            Set<Integer> sessionIds = new HashSet<>();

            for (int i = 0; i < 1_000; i++) {
                int sessionId = manager.addGameSession(new GameSession());
                assertTrue(sessionId >= 0 && sessionId < 100_000);
                assertTrue(sessionIds.add(sessionId));
            }

            int sessionId = sessionIds.iterator().next();
            GameSession currentSession = manager.getGameSessionBySessionId(sessionId);
            assertFalse(manager.removeGameSession(sessionId, new GameSession()));
            assertSame(currentSession, manager.getGameSessionBySessionId(sessionId));
            assertTrue(manager.removeGameSession(sessionId, currentSession));
            assertNull(manager.getGameSessionBySessionId(sessionId));
        } finally {
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousManager);
        }
    }

    @Test
    void delayedPacketsCannotCrossIntoAReplacementSession() {
        Object previousManager = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        try {
            GameSessionManager manager = new GameSessionManager();
            manager.init();
            GameSession originalSession = new GameSession();
            int sessionId = manager.addGameSession(originalSession);
            FTClient client = new FTClient();
            client.setActiveGameSession(sessionId);
            FTConnection connection = mock(FTConnection.class);
            Packet packet = mock(Packet.class);
            PacketEvent event = new PacketEvent(connection, client, packet, PacketEventType.FIRE_DELAYED,
                    originalSession, false, 0, 1);

            GameSession replacementSession = new GameSession();
            manager.getGameSessionList().put(sessionId, replacementSession);
            client.setActiveGameSession(sessionId);
            event.fire();

            verify(connection, never()).sendTCP(packet);
        } finally {
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousManager);
        }
    }

    @Test
    void detachedSessionPacketsOnlySendWhileTheClientRemainsDetached() {
        Object previousManager = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        try {
            GameSessionManager manager = new GameSessionManager();
            manager.init();
            GameSession originalSession = new GameSession();
            int sessionId = manager.addGameSession(originalSession);
            FTClient client = new FTClient();
            client.setActiveGameSession(sessionId);
            FTConnection connection = mock(FTConnection.class);
            Packet detachedPacket = mock(Packet.class);
            PacketEvent detachedEvent = new PacketEvent(connection, client, detachedPacket,
                    PacketEventType.FIRE_DELAYED, originalSession, true, 0, 1);

            client.setActiveGameSession(null);
            detachedEvent.fire();

            verify(connection).sendTCP(detachedPacket);

            client.setActiveGameSession(sessionId);
            Room originalRoom = new Room();
            client.setActiveRoom(originalRoom);
            Packet movedRoomPacket = mock(Packet.class);
            PacketEvent movedRoomEvent = new PacketEvent(connection, client, movedRoomPacket,
                    PacketEventType.FIRE_DELAYED, originalSession, true, 0, 1);
            client.setActiveGameSession(null);
            client.setActiveRoom(new Room());
            movedRoomEvent.fire();

            verify(connection, never()).sendTCP(movedRoomPacket);

            client.setActiveGameSession(sessionId);
            Packet replacementPacket = mock(Packet.class);
            PacketEvent replacementEvent = new PacketEvent(connection, client, replacementPacket,
                    PacketEventType.FIRE_DELAYED, originalSession, true, 0, 1);
            GameSession replacementSession = new GameSession();
            manager.getGameSessionList().put(sessionId, replacementSession);
            client.setActiveGameSession(sessionId);
            replacementEvent.fire();

            verify(connection, never()).sendTCP(replacementPacket);
        } finally {
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousManager);
        }
    }

    @Test
    void finishTaskCannotFinishAReplacementSession() {
        Object previousManager = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        try {
            GameSessionManager manager = new GameSessionManager();
            manager.init();
            GameSession originalSession = new GameSession();
            MatchplayGame originalGame = mock(MatchplayGame.class);
            MatchplayHandleable originalHandler = mock(MatchplayHandleable.class);
            when(originalGame.getFinished()).thenReturn(new AtomicBoolean(false));
            when(originalGame.getHandleable()).thenReturn(originalHandler);
            ReflectionTestUtils.setField(originalSession, "matchplayGame", originalGame);
            int sessionId = manager.addGameSession(originalSession);

            FTClient client = new FTClient();
            client.setActiveGameSession(sessionId);
            FTConnection connection = mock(FTConnection.class);
            when(connection.getClient()).thenReturn(client);
            FinishGameTask finishGameTask = new FinishGameTask(connection);

            GameSession replacementSession = new GameSession();
            manager.getGameSessionList().put(sessionId, replacementSession);
            client.setActiveGameSession(sessionId);
            finishGameTask.run();

            verify(originalHandler, never()).onEnd(client);
        } finally {
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousManager);
        }
    }

    @Test
    void delayedGameplayTasksCannotMutateAReplacementSession() {
        Object previousSessionManager = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        Object previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        Object previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        try {
            GameSessionManager sessionManager = new GameSessionManager();
            sessionManager.init();
            GameManager gameManager = mock(GameManager.class);
            ReflectionTestUtils.setField(GameManager.class, "instance", gameManager);
            ServiceManager serviceManager = mock(ServiceManager.class);
            when(serviceManager.getGuardianSkillsService())
                    .thenReturn(mock(com.jftse.server.core.service.GuardianSkillsService.class));
            ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

            GameSession originalSession = new GameSession();
            int sessionId = sessionManager.addGameSession(originalSession);
            FTClient client = new FTClient();
            client.setActiveGameSession(sessionId);
            FTConnection connection = mock(FTConnection.class);
            when(connection.getClient()).thenReturn(client);

            PlaceCrystalRandomlyTask crystalTask = new PlaceCrystalRandomlyTask(connection);
            GuardianServeTask serveTask = new GuardianServeTask(connection);
            GuardianAttackTask attackTask = new GuardianAttackTask(connection);
            DefeatTimerTask defeatTimerTask = new DefeatTimerTask(connection, originalSession);

            GameSession replacementSession = new GameSession();
            sessionManager.getGameSessionList().put(sessionId, replacementSession);
            client.setActiveGameSession(sessionId);

            assertDoesNotThrow(crystalTask::run);
            assertDoesNotThrow(serveTask::run);
            assertDoesNotThrow(attackTask::run);
            assertDoesNotThrow(defeatTimerTask::run);
            assertSame(replacementSession, client.getActiveGameSession());
        } finally {
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousSessionManager);
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
        }
    }

    @Test
    void rewardEligibilityAndRemovalAreBoundToTheCompletedMatch() {
        Object previousManager = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        try {
            GameSessionManager manager = new GameSessionManager();
            manager.init();
            MatchplayReward originalReward = new MatchplayReward();
            Map<Short, Long> eligiblePlayers = new java.util.HashMap<>();
            eligiblePlayers.put((short) 0, 100L);
            originalReward.setEligiblePlayerIdsByPosition(eligiblePlayers);
            eligiblePlayers.put((short) 1, 200L);
            manager.addMatchplayReward(10, originalReward);

            MatchplayReward replacementReward = new MatchplayReward();
            assertFalse(manager.removeMatchplayReward(10, replacementReward));
            assertSame(originalReward, manager.getMatchplayReward(10));
            assertEquals(Map.of((short) 0, 100L), originalReward.getEligiblePlayerIdsByPosition());
            assertTrue(manager.removeMatchplayReward(10, originalReward));
            assertNull(manager.getMatchplayReward(10));
        } finally {
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousManager);
        }
    }

    @Test
    void cleanupIsIdempotentAndCannotDetachOrResetAReplacementSession() {
        Object previousSessionManager = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        Object previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        try {
            GameSessionManager sessionManager = new GameSessionManager();
            sessionManager.init();
            MatchRallyStatsConsumer rallyStatsConsumer = mock(MatchRallyStatsConsumer.class);
            RProducerService producerService = mock(RProducerService.class);
            GameManager gameManager = new GameManager();
            gameManager.setGameSessionManager(sessionManager);
            gameManager.setMatchRallyStatsConsumer(rallyStatsConsumer);
            gameManager.setRProducerService(producerService);
            ReflectionTestUtils.setField(GameManager.class, "instance", gameManager);

            GameSession originalSession = new GameSession();
            Fireable fireable = mock(Fireable.class);
            originalSession.getFireables().add(fireable);
            MatchplayBasicGame originalGame = mock(MatchplayBasicGame.class);
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            when(originalGame.getScheduledFutures())
                    .thenReturn(new ConcurrentLinkedDeque<>(List.of(future)));
            originalSession.setMatchplayGame(originalGame);
            int sessionId = sessionManager.addGameSession(originalSession);

            FTClient client = new FTClient();
            originalSession.getClients().add(client);
            client.setActiveGameSession(sessionId);
            Room room = new Room();
            room.setStatus(RoomStatus.Running);

            GameSession replacementSession = new GameSession();
            sessionManager.getGameSessionList().put(sessionId, replacementSession);
            client.setActiveGameSession(sessionId);

            gameManager.cleanupFinishedGameSession(sessionId, originalSession, room);
            gameManager.cleanupFinishedGameSession(sessionId, originalSession, room);

            assertSame(replacementSession, sessionManager.getGameSessionBySessionId(sessionId));
            assertSame(replacementSession, client.getActiveGameSession());
            assertEquals(RoomStatus.Running, room.getStatus());
            assertTrue(originalSession.getFireables().isEmpty());
            assertTrue(originalGame.getScheduledFutures().isEmpty());
            assertTrue(originalSession.getRelayAuthorizationRevoked().get());
            verify(fireable).setCancelled(true);
            verify(future).cancel(false);
            verify(rallyStatsConsumer, never()).clearSession(sessionId);
            verify(producerService, times(1)).sendNow(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString());
        } finally {
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousSessionManager);
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
        }
    }

    @Test
    void invalidRewardClaimsAndAStaleAutoPickerCannotConsumeCompletedMatchRewards() {
        Object previousSessionManager = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        Object previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        try {
            GameSessionManager sessionManager = new GameSessionManager();
            sessionManager.init();
            ServiceManager serviceManager = mock(ServiceManager.class);
            when(serviceManager.getProductService())
                    .thenReturn(mock(com.jftse.server.core.service.ProductService.class));
            ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

            MatchplayReward originalReward = new MatchplayReward();
            MatchplayReward.ItemReward originalItem = new MatchplayReward.ItemReward(1234, 1, 1.0);
            originalReward.assignItemRewardsToSlots(new java.util.ArrayList<>(List.of(originalItem)));
            originalReward.setEligiblePlayerIdsByPosition(Map.of((short) 0, 100L));
            sessionManager.addMatchplayReward(10, originalReward);

            FTPlayer eligiblePlayer = mock(FTPlayer.class);
            when(eligiblePlayer.getId()).thenReturn(100L);
            Room room = mock(Room.class);
            when(room.getRoomId()).thenReturn((short) 10);
            RoomPlayer eligibleRoomPlayer = roomPlayer(100L, (short) 0);
            FTClient eligibleClient = mock(FTClient.class);
            when(eligibleClient.hasPlayer()).thenReturn(true);
            when(eligibleClient.getPlayer()).thenReturn(eligiblePlayer);
            when(eligibleClient.getActiveRoom()).thenReturn(room);
            when(eligibleClient.getRoomPlayer()).thenReturn(eligibleRoomPlayer);
            FTConnection eligibleConnection = mock(FTConnection.class);
            when(eligibleConnection.getClient()).thenReturn(eligibleClient);

            new MatchplayItemRewardPickHandler().handle(eligibleConnection,
                    CMSGPickupItemReward.builder().slot((byte) 9).build());
            assertFalse(originalItem.getClaimed().get());

            FTPlayer spectatorPlayer = mock(FTPlayer.class);
            when(spectatorPlayer.getId()).thenReturn(200L);
            RoomPlayer spectatorRoomPlayer = roomPlayer(200L, (short) 0);
            FTClient spectatorClient = mock(FTClient.class);
            when(spectatorClient.hasPlayer()).thenReturn(true);
            when(spectatorClient.getPlayer()).thenReturn(spectatorPlayer);
            when(spectatorClient.getActiveRoom()).thenReturn(room);
            when(spectatorClient.getRoomPlayer()).thenReturn(spectatorRoomPlayer);
            FTConnection spectatorConnection = mock(FTConnection.class);
            when(spectatorConnection.getClient()).thenReturn(spectatorClient);

            new MatchplayItemRewardPickHandler().handle(spectatorConnection,
                    CMSGPickupItemReward.builder().slot((byte) 0).build());
            assertFalse(originalItem.getClaimed().get());

            new MatchplayItemRewardPickHandler().handle(eligibleConnection,
                    CMSGPickupItemReward.builder().slot((byte) 0).build());
            assertFalse(originalItem.getClaimed().get());

            AutoItemRewardPickerTask invalidProductPicker = new AutoItemRewardPickerTask(
                    new ConcurrentLinkedDeque<>(List.of(eligibleClient)), (short) 10);
            invalidProductPicker.run();
            assertFalse(originalItem.getClaimed().get());

            AutoItemRewardPickerTask stalePicker = new AutoItemRewardPickerTask(
                    new ConcurrentLinkedDeque<>(List.of(eligibleClient)), (short) 10);
            MatchplayReward replacementReward = new MatchplayReward();
            MatchplayReward.ItemReward replacementItem = new MatchplayReward.ItemReward(5678, 1, 1.0);
            replacementReward.assignItemRewardsToSlots(new java.util.ArrayList<>(List.of(replacementItem)));
            replacementReward.setEligiblePlayerIdsByPosition(Map.of((short) 0, 100L));
            sessionManager.addMatchplayReward(10, replacementReward);

            stalePicker.run();

            assertFalse(originalItem.getClaimed().get());
            assertFalse(replacementItem.getClaimed().get());
            assertSame(replacementReward, sessionManager.getMatchplayReward(10));
        } finally {
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousSessionManager);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
        }
    }

    private static FTClient clientFor(RoomPlayer roomPlayer) {
        FTClient client = mock(FTClient.class);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        return client;
    }

    private static RoomPlayer roomPlayer(long playerId, short position) {
        RoomPlayer roomPlayer = mock(RoomPlayer.class);
        when(roomPlayer.getPlayerId()).thenReturn(playerId);
        when(roomPlayer.getPosition()).thenReturn(position);
        return roomPlayer;
    }

    private static Pet pet(long petId, String name) {
        Pet pet = mock(Pet.class);
        PetStatistic statistic = new PetStatistic();
        when(pet.getId()).thenReturn(petId);
        when(pet.getName()).thenReturn(name);
        when(pet.getPetStatistic()).thenReturn(statistic);
        return pet;
    }

    private static class UnknownMatchplayGame extends MatchplayGame {
        @Override
        public MatchplayReward getMatchRewards() {
            return null;
        }

        @Override
        public void addBonusesToRewards(ConcurrentLinkedDeque<RoomPlayer> roomPlayers, List<PlayerReward> playerRewards) {
        }

        @Override
        protected MatchplayHandleable createHandler() {
            return null;
        }
    }
}
