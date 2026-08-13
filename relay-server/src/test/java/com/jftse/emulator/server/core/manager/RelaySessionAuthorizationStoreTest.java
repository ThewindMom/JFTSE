package com.jftse.emulator.server.core.manager;

import com.jftse.emulator.server.core.handler.RelayPacketRequestHandler;
import com.jftse.emulator.server.core.handler.SpiderMineExplodeHandler;
import com.jftse.emulator.server.core.handler.SpiderMinePlacedHandler;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.item.BattlemonController;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.shared.packets.relay.CMSGRelay;
import com.jftse.server.core.shared.packets.relay.CMSGSpiderMineExplode;
import com.jftse.server.core.shared.packets.relay.CMSGSpiderMinePlaced;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelaySessionAuthorizationStoreTest {
    private RelaySessionAuthorizationStore store;

    @BeforeEach
    void setUp() {
        store = new RelaySessionAuthorizationStore();
        store.init();
    }

    @Test
    void battlemonOwnersCanOnlyControlTheirHumanAndPetActors() {
        store.put(authorization(100, true, Map.of(
                1000, List.of((short) 0, (short) 2),
                2000, List.of((short) 1, (short) 3)
        ), Map.of(
                1000, true,
                2000, true
        )));
        FTClient firstOwner = registeredClient(100, 1000);
        FTClient secondOwner = registeredClient(100, 2000);

        assertTrue(store.canAct(firstOwner, 0));
        assertTrue(store.canAct(firstOwner, 2));
        assertFalse(store.canAct(firstOwner, 1));
        assertFalse(store.canAct(firstOwner, 3));
        assertTrue(store.canAct(secondOwner, 1));
        assertTrue(store.canAct(secondOwner, 3));
        assertFalse(store.canAct(secondOwner, 0));
        assertFalse(store.canAct(secondOwner, 2));
    }

    @Test
    void possessionRequiresSpecialItemElevenWithAPositiveCount() {
        assertFalse(BattlemonController.isPossessed(null));
        assertFalse(BattlemonController.isPossessed(pocket("SPECIAL", 11, 0)));
        assertFalse(BattlemonController.isPossessed(pocket("SPECIAL", 10, 1)));
        assertFalse(BattlemonController.isPossessed(pocket("PET_ITEM", 11, 1)));
        assertTrue(BattlemonController.isPossessed(pocket("SPECIAL", 11, 1)));
        assertFalse(BattlemonController.isPetActor(0));
        assertTrue(BattlemonController.isPetActor(2));
    }

    @Test
    void battlemonPetCommandsRequireControllerPossession() {
        store.put(authorization(100, true, Map.of(
                1000, List.of((short) 0, (short) 2),
                2000, List.of((short) 1, (short) 3)
        ), Map.of(
                1000, true,
                2000, false
        )));
        FTClient firstOwner = registeredClient(100, 1000);
        FTClient secondOwner = registeredClient(100, 2000);

        assertTrue(store.canAct(firstOwner, 0));
        assertTrue(store.canAct(firstOwner, 2));
        assertTrue(store.canAct(secondOwner, 1));
        assertFalse(store.canAct(secondOwner, 3));
        assertFalse(store.canAct(firstOwner, 3));
    }

    @Test
    void missingControllerFlagsRejectPetCommandsWithoutAffectingHumanActors() {
        store.put(authorization(100, true, Map.of(
                1000, List.of((short) 0, (short) 2),
                2000, List.of((short) 1, (short) 3)
        )));
        FTClient firstOwner = registeredClient(100, 1000);

        assertTrue(store.canAct(firstOwner, 0));
        assertFalse(store.canAct(firstOwner, 2));
    }

    @Test
    void battlemonPetActorsCannotPlaceSpiderMines() {
        Object previousRelayManager = ReflectionTestUtils.getField(RelayManager.class, "instance");
        try {
            store.put(authorization(100, true, Map.of(
                    1000, List.of((short) 0, (short) 2),
                    2000, List.of((short) 1, (short) 3)
            )));
            FTClient owner = registeredClient(100, 1000);
            FTConnection connection = mock(FTConnection.class);
            when(connection.getClient()).thenReturn(owner);
            RelayManager relayManager = mock(RelayManager.class);
            ReflectionTestUtils.setField(RelayManager.class, "instance", relayManager);

            CMSGSpiderMinePlaced packet = CMSGSpiderMinePlaced.builder()
                    .position(2)
                    .isActive(true)
                    .mineId((short) 1)
                    .posX((short) 10)
                    .posY((short) 20)
                    .build();
            new SpiderMinePlacedHandler().handle(connection, packet);

            verify(relayManager, never()).broadcastToSessionGeneration(anyInt(), anyString(), any());
        } finally {
            ReflectionTestUtils.setField(RelayManager.class, "instance", previousRelayManager);
        }
    }

    @Test
    void battlemonSpiderMineExplosionsReachEveryClient() {
        Object previousRelayManager = ReflectionTestUtils.getField(RelayManager.class, "instance");
        try {
            store.put(authorization(100, true, Map.of(
                    1000, List.of((short) 0, (short) 2),
                    2000, List.of((short) 1, (short) 3)
            )));
            FTClient owner = registeredClient(100, 1000);
            FTConnection connection = mock(FTConnection.class);
            when(connection.getClient()).thenReturn(owner);
            RelayManager relayManager = mock(RelayManager.class);
            ReflectionTestUtils.setField(RelayManager.class, "instance", relayManager);

            CMSGSpiderMineExplode packet = CMSGSpiderMineExplode.builder()
                    .targetPosition((byte) 1)
                    .spiderMineId((short) 123)
                    .build();
            new SpiderMineExplodeHandler().handle(connection, packet);

            verify(relayManager).broadcastToSessionGeneration(eq(100), anyString(), any());
        } finally {
            ReflectionTestUtils.setField(RelayManager.class, "instance", previousRelayManager);
        }
    }

    @Test
    void unknownPlayersSpectatorsAndExpiredSessionsFailClosed() {
        store.put(authorization(100, false, Map.of(1000, List.of((short) 0))));
        FTClient player = registeredClient(100, 1000);
        FTClient unknownPlayer = registeredClient(100, 2000);

        assertTrue(store.canRegister(100, 1000, false, "127.0.0.1"));
        assertFalse(store.canRegister(100, 2000, false, "127.0.0.1"));
        assertFalse(store.canRegister(100, 1000, true, "127.0.0.1"));
        assertFalse(store.canRegister(100, 1000, false, "127.0.0.2"));
        assertFalse(store.canAct(unknownPlayer, 0));

        player.setSpectator(true);
        assertFalse(store.canAct(player, 0));

        assertThrows(IllegalArgumentException.class, () -> store.put(
                RelaySessionAuthorizationMessage.builder()
                        .gameSessionId(200)
                        .generation(UUID.randomUUID().toString())
                        .battlemon(false)
                        .revoked(false)
                        .actorPositionsByPlayerId(Map.of(1000, List.of((short) 0)))
                        .playerAddresses(Map.of(1000, "127.0.0.1"))
                        .expiresAt(Instant.now().minusSeconds(1))
                        .build()
        ));
    }

    @Test
    void authorizationRejectsOverlappingActorsAndCopiesInput() {
        assertThrows(IllegalArgumentException.class, () -> store.put(authorization(100, true, Map.of(
                1000, List.of((short) 0, (short) 2),
                2000, List.of((short) 1, (short) 2)
        ))));

        List<Short> mutableActors = new ArrayList<>(List.of((short) 0));
        Map<Integer, List<Short>> mutableMap = new LinkedHashMap<>();
        mutableMap.put(1000, mutableActors);
        store.put(authorization(200, false, mutableMap));
        mutableActors.add((short) 1);
        mutableMap.put(2000, List.of((short) 2));

        FTClient player = registeredClient(200, 1000);
        assertTrue(store.canAct(player, 0));
        assertFalse(store.canAct(player, 1));
        assertFalse(store.canRegister(200, 2000, false, "127.0.0.1"));
    }

    @Test
    void revocationWinsRegardlessOfRabbitDeliveryOrder() {
        String generation = UUID.randomUUID().toString();
        RelaySessionAuthorizationMessage create = authorization(100, generation, true, Map.of(
                1000, List.of((short) 0, (short) 2),
                2000, List.of((short) 1, (short) 3)
        ));
        RelaySessionAuthorizationMessage revoke = RelaySessionAuthorizationMessage.builder()
                .gameSessionId(100)
                .generation(generation)
                .battlemon(true)
                .revoked(true)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        store.put(revoke);
        store.put(create);
        assertTrue(store.find(100).isEmpty());

        String secondGeneration = UUID.randomUUID().toString();
        RelaySessionAuthorizationMessage secondCreate = authorization(200, secondGeneration, true, Map.of(
                1000, List.of((short) 0, (short) 2),
                2000, List.of((short) 1, (short) 3)
        ));
        RelaySessionAuthorizationMessage secondRevoke = RelaySessionAuthorizationMessage.builder()
                .gameSessionId(200)
                .generation(secondGeneration)
                .battlemon(true)
                .revoked(true)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        store.put(secondCreate);
        store.put(secondRevoke);
        assertTrue(store.find(200).isEmpty());
    }

    @Test
    void reusedSessionIdDoesNotAuthorizeClientsFromAnOlderGeneration() {
        String oldGeneration = UUID.randomUUID().toString();
        store.put(authorization(300, oldGeneration, false, Map.of(
                1000, List.of((short) 0)
        )));
        FTClient staleClient = registeredClient(300, 1000);

        store.put(RelaySessionAuthorizationMessage.builder()
                .gameSessionId(300)
                .generation(oldGeneration)
                .battlemon(false)
                .revoked(true)
                .expiresAt(Instant.now().plusSeconds(60))
                .build());
        store.put(authorization(300, UUID.randomUUID().toString(), false, Map.of(
                1000, List.of((short) 0)
        )));
        FTClient currentClient = registeredClient(300, 1000);

        assertFalse(store.canParticipate(staleClient));
        assertFalse(store.canAct(staleClient, 0));
        assertTrue(store.canParticipate(currentClient));
        assertTrue(store.canAct(currentClient, 0));

        currentClient.setBattlemonSession(true);
        assertFalse(store.canParticipate(currentClient));
    }

    @Test
    void genericRelayTrafficFailsClosedAfterAuthorizationIsRevoked() {
        String generation = UUID.randomUUID().toString();
        store.put(authorization(400, generation, false, Map.of(
                1000, List.of((short) 0)
        )));
        FTClient client = registeredClient(400, 1000);
        store.put(RelaySessionAuthorizationMessage.builder()
                .gameSessionId(400)
                .generation(generation)
                .battlemon(false)
                .revoked(true)
                .expiresAt(Instant.now().plusSeconds(60))
                .build());
        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        byte[] unknownInnerPacket = new byte[8];
        unknownInnerPacket[4] = (byte) 0xfe;
        unknownInnerPacket[5] = (byte) 0x7f;

        new RelayPacketRequestHandler().handle(connection,
                CMSGRelay.builder().packet(unknownInnerPacket).build());

        verify(connection, never()).queuePacket(any(IPacket.class));
    }

    @Test
    void revokingAnOldGenerationDisconnectsItWithoutRemovingAReplacement() {
        RelayManager relayManager = new RelayManager();
        ReflectionTestUtils.setField(relayManager, "relaySessionAuthorizationStore", store);
        ReflectionTestUtils.setField(relayManager, "sessionMap", new ConcurrentHashMap<>());
        ReflectionTestUtils.setField(relayManager, "playerCount", new AtomicInteger());

        String oldGeneration = UUID.randomUUID().toString();
        store.put(authorization(500, oldGeneration, false, Map.of(
                1000, List.of((short) 0)
        )));
        FTClient staleClient = new FTClient();
        FTConnection staleConnection = mock(FTConnection.class);
        staleClient.setConnection(staleConnection);
        assertTrue(relayManager.registerClient(
                500, 1000, false, false, oldGeneration, staleClient));

        store.put(RelaySessionAuthorizationMessage.builder()
                .gameSessionId(500)
                .generation(oldGeneration)
                .battlemon(false)
                .revoked(true)
                .expiresAt(Instant.now().plusSeconds(60))
                .build());
        relayManager.revokeSessionGeneration(500, oldGeneration);

        String replacementGeneration = UUID.randomUUID().toString();
        store.put(authorization(500, replacementGeneration, false, Map.of(
                1000, List.of((short) 0)
        )));
        FTClient replacementClient = new FTClient();
        assertTrue(relayManager.registerClient(
                500, 1000, false, false, replacementGeneration, replacementClient));

        assertTrue(staleClient.getGameSessionId().isEmpty());
        assertEquals(List.of(replacementClient),
                relayManager.getClientsInSession(500, replacementGeneration));
        assertTrue(relayManager.getClientsInSession(500, oldGeneration).isEmpty());
        assertEquals(1, relayManager.getPlayerCount().get());
        verify(staleConnection).close();
    }

    @Test
    void generationAwareBroadcastExcludesStaleClientsAndStopsAsSoonAsAuthorizationIsRevoked() {
        RelayManager relayManager = relayManager();
        String generation = UUID.randomUUID().toString();
        store.put(authorization(600, generation, false, Map.of(
                1000, List.of((short) 0)
        )));
        FTClient currentClient = new FTClient();
        FTConnection currentConnection = mock(FTConnection.class);
        currentClient.setConnection(currentConnection);
        assertTrue(relayManager.registerClient(
                600, 1000, false, false, generation, currentClient));

        FTClient staleClient = new FTClient();
        FTConnection staleConnection = mock(FTConnection.class);
        staleClient.setConnection(staleConnection);
        staleClient.setGameSessionId(600);
        staleClient.setRelayAuthorizationGeneration(UUID.randomUUID().toString());
        relayManager.getSessionMap().get(600).add(staleClient);

        IPacket packet = mock(IPacket.class);
        relayManager.broadcastToSessionGeneration(600, generation, packet);

        verify(currentConnection).sendTCP(packet);
        verify(staleConnection, never()).sendTCP(packet);

        store.put(RelaySessionAuthorizationMessage.builder()
                .gameSessionId(600)
                .generation(generation)
                .battlemon(false)
                .revoked(true)
                .expiresAt(Instant.now().plusSeconds(60))
                .build());
        relayManager.broadcastToSessionGeneration(600, generation, packet);

        verify(currentConnection).sendTCP(packet);
        assertTrue(relayManager.getClientsInSession(600, null).isEmpty());
    }

    @Test
    void concurrentRegistrationAndRevocationCannotLeaveAnOldGenerationRegistered() throws Exception {
        RelayManager relayManager = relayManager();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < 50; attempt++) {
                int sessionId = 700 + attempt;
                String generation = UUID.randomUUID().toString();
                store.put(authorization(sessionId, generation, false, Map.of(
                        1000, List.of((short) 0)
                )));
                FTClient client = new FTClient();
                client.setConnection(mock(FTConnection.class));
                CountDownLatch start = new CountDownLatch(1);

                Future<Boolean> registration = executor.submit(() -> {
                    start.await();
                    return relayManager.registerClient(
                            sessionId, 1000, false, false, generation, client);
                });
                Future<?> revocation = executor.submit(() -> {
                    start.await();
                    store.put(RelaySessionAuthorizationMessage.builder()
                            .gameSessionId(sessionId)
                            .generation(generation)
                            .battlemon(false)
                            .revoked(true)
                            .expiresAt(Instant.now().plusSeconds(60))
                            .build());
                    relayManager.revokeSessionGeneration(sessionId, generation);
                    return null;
                });

                start.countDown();
                registration.get();
                revocation.get();

                assertTrue(relayManager.getClientsInSession(sessionId, generation).isEmpty());
                assertTrue(client.getGameSessionId().isEmpty());
            }
            assertEquals(0, relayManager.getPlayerCount().get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiredAuthorizationCanBePurgedWithItsRegisteredRelayClients() {
        RelayManager relayManager = relayManager();
        String generation = UUID.randomUUID().toString();
        store.put(RelaySessionAuthorizationMessage.builder()
                .gameSessionId(800)
                .generation(generation)
                .battlemon(false)
                .revoked(false)
                .actorPositionsByPlayerId(Map.of(1000, List.of((short) 0)))
                .playerAddresses(Map.of(1000, "127.0.0.1"))
                .expiresAt(Instant.now().plusSeconds(60))
                .build());
        FTClient client = new FTClient();
        FTConnection connection = mock(FTConnection.class);
        client.setConnection(connection);
        assertTrue(relayManager.registerClient(
                800, 1000, false, false, generation, client));

        store.removeExpired(Instant.now().plusSeconds(120)).forEach(expiredAuthorization ->
                relayManager.revokeSessionGeneration(expiredAuthorization.gameSessionId(),
                        expiredAuthorization.generation()));

        assertTrue(store.find(800).isEmpty());
        assertTrue(relayManager.getClientsInSession(800, generation).isEmpty());
        assertTrue(client.getGameSessionId().isEmpty());
        assertEquals(0, relayManager.getPlayerCount().get());
        verify(connection).close();
    }

    private RelayManager relayManager() {
        RelayManager relayManager = new RelayManager();
        ReflectionTestUtils.setField(relayManager, "relaySessionAuthorizationStore", store);
        ReflectionTestUtils.setField(relayManager, "sessionMap", new ConcurrentHashMap<>());
        ReflectionTestUtils.setField(relayManager, "playerCount", new AtomicInteger());
        return relayManager;
    }

    private static RelaySessionAuthorizationMessage authorization(int sessionId, boolean battlemon,
                                                                   Map<Integer, List<Short>> actors) {
        return authorization(sessionId, UUID.randomUUID().toString(), battlemon, actors, null);
    }

    private static RelaySessionAuthorizationMessage authorization(int sessionId, boolean battlemon,
                                                                   Map<Integer, List<Short>> actors,
                                                                   Map<Integer, Boolean> controllers) {
        return authorization(sessionId, UUID.randomUUID().toString(), battlemon, actors, controllers);
    }

    private static RelaySessionAuthorizationMessage authorization(int sessionId, String generation, boolean battlemon,
                                                                   Map<Integer, List<Short>> actors) {
        return authorization(sessionId, generation, battlemon, actors, null);
    }

    private static RelaySessionAuthorizationMessage authorization(int sessionId, String generation, boolean battlemon,
                                                                   Map<Integer, List<Short>> actors,
                                                                   Map<Integer, Boolean> controllers) {
        return RelaySessionAuthorizationMessage.builder()
                .gameSessionId(sessionId)
                .generation(generation)
                .battlemon(battlemon)
                .revoked(false)
                .actorPositionsByPlayerId(actors)
                .playerAddresses(actors.keySet().stream().collect(java.util.stream.Collectors.toMap(
                        playerId -> playerId,
                        playerId -> "127.0.0.1"
                )))
                .battlemonControllerByPlayerId(controllers)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    private static PlayerPocket pocket(String category, int itemIndex, int count) {
        PlayerPocket playerPocket = new PlayerPocket();
        playerPocket.setCategory(category);
        playerPocket.setItemIndex(itemIndex);
        playerPocket.setItemCount(count);
        return playerPocket;
    }

    private FTClient registeredClient(int sessionId, int playerId) {
        RelaySessionAuthorizationStore.SessionAuthorization authorization = store.find(sessionId).orElseThrow();
        FTClient client = new FTClient();
        client.setGameSessionId(sessionId);
        client.setPlayerId(playerId);
        client.setBattlemonSession(authorization.battlemon());
        client.setRelayAuthorizationGeneration(authorization.generation());
        return client;
    }
}
