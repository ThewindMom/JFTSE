package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.client.EquippedItemParts;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.handler.lobby.room.GameModeChangePacketHandler;
import com.jftse.emulator.server.core.handler.lobby.room.RoomAllowBattlemonChangePacketHandler;
import com.jftse.emulator.server.core.handler.lobby.room.RoomMapChangeRequestPacketHandler;
import com.jftse.emulator.server.core.handler.lobby.room.RoomPositionChangeRequestPacketHandler;
import com.jftse.emulator.server.core.handler.lobby.room.RoomQuickSlotChangePacketHandler;
import com.jftse.emulator.server.core.handler.lobby.room.RoomReadyChangeRequestPacketHandler;
import com.jftse.emulator.server.core.handler.lobby.room.RoomRequestPetPacketHandler;
import com.jftse.emulator.server.core.handler.lobby.room.RoomSkillFreeChangePacketHandler;
import com.jftse.emulator.server.core.handler.pet.PetPickupRequestPacketHandler;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.gameserver.GameServer;
import com.jftse.entities.database.model.player.EquippedItemStats;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.pet.PetStatistic;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.item.BattlemonController;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.service.AuthenticationService;
import com.jftse.server.core.service.PetService;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.SocialService;
import com.jftse.server.core.shared.ServerConfService;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeAllowBattlemon;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeGameMode;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeMap;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangePosition;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeQuickSlot;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeReady;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeSkillFree;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomChangeReady;
import com.jftse.server.core.shared.packets.matchplay.CMSGStartGame;
import com.jftse.server.core.shared.packets.pet.CMSGPickupPet;
import com.jftse.server.core.shared.packets.pet.CMSGRequestPet;
import com.jftse.server.core.shared.packets.pet.SMSGPickupPet;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import com.jftse.server.core.thread.ThreadManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomStartGamePacketHandlerTest {
    @Test
    void battlemonRoomCreationRejectsMissingPetBeforeSuccessfulBootstrap() {
        GameManager gameManager = new GameManager();
        ServiceManager serviceManager = mock(ServiceManager.class);
        SocialService socialService = mock(SocialService.class);
        when(serviceManager.getSocialService()).thenReturn(socialService);
        ReflectionTestUtils.setField(gameManager, "serviceManager", serviceManager);
        ReflectionTestUtils.setField(gameManager, "clients", new ConcurrentLinkedDeque<FTClient>());
        ReflectionTestUtils.setField(gameManager, "rooms", new ConcurrentLinkedDeque<Room>());

        FTPlayer player = mock(FTPlayer.class);
        when(player.getName()).thenReturn("Player");
        FTClient client = mock(FTClient.class);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        Room room = new Room();
        room.setRoomType((byte) RoomType.BATTLEMON);
        room.setMode((byte) com.jftse.server.core.constants.GameMode.BATTLE);

        gameManager.internalHandleRoomCreate(connection, room);

        assertTrue(room.getRoomPlayerList().isEmpty());
        assertTrue(gameManager.getRooms().isEmpty());
        assertEquals(List.of(0x138A), sentPacketIds(connection));
    }

    @Test
    void validatesSelectedBattlemonPetAgainstCanonicalPersistence() {
        GameManager gameManager = new GameManager();
        ServiceManager serviceManager = mock(ServiceManager.class);
        PetService petService = mock(PetService.class);
        when(serviceManager.getPetService()).thenReturn(petService);
        ReflectionTestUtils.setField(gameManager, "serviceManager", serviceManager);

        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(100L);
        Pet selectedPet = pet(10L, "Selected pet");
        PetView selectedPetView = PetView.of(selectedPet);
        FTClient client = mock(FTClient.class);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getActivePet()).thenReturn(selectedPetView);
        when(petService.findByIdAndPlayerId(10L, 100L)).thenReturn(selectedPet);

        selectedPet.setValidUntil(null);
        assertNull(gameManager.getValidatedActiveBattlemonPet(client));

        selectedPet.setValidUntil(Date.from(Instant.now().plus(30, ChronoUnit.DAYS)));
        assertEquals(selectedPetView, gameManager.getValidatedActiveBattlemonPet(client));
        verify(client).setActivePet(selectedPet);

        selectedPet.setLevel(250);
        assertEquals(selectedPetView, gameManager.getValidatedActiveBattlemonPet(client));

        selectedPet.setEnergy(0);
        assertNull(gameManager.getValidatedActiveBattlemonPet(client));
    }

    @Test
    void startupAbortResynchronizesEveryConnectedClientOnlyOnce() {
        Object previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        Object previousSessionManager = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        try {
            GameManager gameManager = mock(GameManager.class);
            ReflectionTestUtils.setField(GameManager.class, "instance", gameManager);
            GameSessionManager sessionManager = new GameSessionManager();
            sessionManager.init();

            Room room = new Room();
            room.setRoomId((short) 44);
            room.setStatus(RoomStatus.StartingGame);
            GameSession session = new GameSession();
            int sessionId = sessionManager.addGameSession(session);
            FTClient first = mock(FTClient.class);
            FTClient second = mock(FTClient.class);
            FTConnection firstConnection = mock(FTConnection.class);
            FTConnection secondConnection = mock(FTConnection.class);
            RoomPlayer requester = mock(RoomPlayer.class);
            when(requester.getPosition()).thenReturn((short) 0);
            when(first.getRoomPlayer()).thenReturn(requester);
            when(first.getConnection()).thenReturn(firstConnection);
            when(second.getConnection()).thenReturn(secondConnection);
            when(first.getActiveGameSession()).thenReturn(session);
            when(second.getActiveGameSession()).thenReturn(session);
            when(gameManager.getClientsInRoom((short) 44)).thenReturn(List.of(first, second));

            RoomStartGamePacketHandler.abortStartAndNotifyClients(
                    room, sessionId, session, List.of(first, second), first);

            assertEquals(List.of(0x1394, 0x17F3, 0x17E6, 0x17D6), sentPacketIds(firstConnection));
            assertEquals(List.of(0x1394, 0x17F3, 0x17E6, 0x17D6), sentPacketIds(secondConnection));
            assertEquals(RoomStatus.NotRunning, room.getStatus());

            RoomStartGamePacketHandler.abortStartAndNotifyClients(
                    room, sessionId, session, List.of(first, second), first);

            assertEquals(4, sentPacketIds(firstConnection).size());
            assertEquals(4, sentPacketIds(secondConnection).size());
        } finally {
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousSessionManager);
        }
    }

    @Test
    void battlemonRelayAuthorizationMapsPetsToOwnerEndpoints() {
        GameSession session = new GameSession(true);
        FTClient first = client(100L, (short) 0);
        FTClient second = client(200L, (short) 1);
        session.getClients().add(first);
        session.getClients().add(second);
        session.addOwnedPetSeat(first.getRoomPlayer(), pet(10L, "First pet"));
        session.addOwnedPetSeat(second.getRoomPlayer(), pet(20L, "Second pet"));
        session.initializeGameplayActorPositions();

        RelaySessionAuthorizationMessage authorization = RoomStartGamePacketHandler.createRelayAuthorization(
                12345,
                List.of(first, second),
                session
        );

        assertTrue(authorization.getBattlemon());
        assertTrue(authorization.getOwnedPetSession());
        assertEquals(List.of((short) 0, (short) 2), authorization.getActorPositionsByPlayerId().get(100));
        assertEquals(List.of((short) 1, (short) 3), authorization.getActorPositionsByPlayerId().get(200));
        assertEquals(Boolean.FALSE, authorization.getBattlemonControllerByPlayerId().get(100));
        assertEquals(Boolean.FALSE, authorization.getBattlemonControllerByPlayerId().get(200));
    }

    @Test
    void battlemonRelayAuthorizationGatesPetActorsOnControllerPossession() {
        Object previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        try {
            PlayerPocketService playerPocketService = mock(PlayerPocketService.class);
            ServiceManager serviceManager = mock(ServiceManager.class);
            when(serviceManager.getPlayerPocketService()).thenReturn(playerPocketService);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

            FTClient first = client(100L, (short) 0);
            FTClient second = client(200L, (short) 1);
            when(first.getPlayer().getPocketId()).thenReturn(11L);
            when(second.getPlayer().getPocketId()).thenReturn(22L);
            PlayerPocket ownedController = new PlayerPocket();
            ownedController.setCategory(EItemCategory.SPECIAL.getName());
            ownedController.setItemIndex(BattlemonController.SPECIAL_ITEM_INDEX);
            ownedController.setItemCount(1);
            when(playerPocketService.getItemAsPocketByItemIndexAndCategoryAndPocket(
                    BattlemonController.SPECIAL_ITEM_INDEX, EItemCategory.SPECIAL.getName(), 11L))
                    .thenReturn(ownedController);
            when(playerPocketService.getItemAsPocketByItemIndexAndCategoryAndPocket(
                    BattlemonController.SPECIAL_ITEM_INDEX, EItemCategory.SPECIAL.getName(), 22L))
                    .thenReturn(null);

            GameSession session = new GameSession(true);
            session.getClients().add(first);
            session.getClients().add(second);
            session.addOwnedPetSeat(first.getRoomPlayer(), pet(10L, "First pet"));
            session.addOwnedPetSeat(second.getRoomPlayer(), pet(20L, "Second pet"));
            session.initializeGameplayActorPositions();

            RelaySessionAuthorizationMessage authorization = RoomStartGamePacketHandler.createRelayAuthorization(
                    12345,
                    List.of(first, second),
                    session
            );

            assertEquals(Boolean.TRUE, authorization.getBattlemonControllerByPlayerId().get(100));
            assertEquals(Boolean.FALSE, authorization.getBattlemonControllerByPlayerId().get(200));
        } finally {
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
        }
    }

    @Test
    void guardianRelayAuthorizationMapsOptionalPetsWithoutBecomingBattlemonSession() {
        GameSession session = new GameSession();
        FTClient first = client(100L, (short) 0);
        FTClient second = client(200L, (short) 1);
        session.getClients().add(first);
        session.getClients().add(second);
        session.addOwnedPetSeat(first.getRoomPlayer(), pet(10L, "First pet"));
        session.addOwnedPetSeat(second.getRoomPlayer(), pet(20L, "Second pet"));
        session.initializeGameplayActorPositions();

        RelaySessionAuthorizationMessage authorization = RoomStartGamePacketHandler.createRelayAuthorization(
                12345,
                List.of(first, second),
                session
        );

        assertFalse(authorization.getBattlemon());
        assertTrue(authorization.getOwnedPetSession());
        assertEquals(List.of((short) 0, (short) 2), authorization.getActorPositionsByPlayerId().get(100));
        assertEquals(List.of((short) 1, (short) 3), authorization.getActorPositionsByPlayerId().get(200));
        assertEquals(List.of((short) 0, (short) 1, (short) 2, (short) 3),
                session.getGameplayActorPositions());
    }

    @Test
    void plainGuardianStartInitializesHumanActorsForRelayConnections() {
        Object previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        Object previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        Object previousSessionManager = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        Object previousThreadManager = ReflectionTestUtils.getField(ThreadManager.class, "instance");
        try {
            AuthenticationService authenticationService = mock(AuthenticationService.class);
            ServiceManager serviceManager = mock(ServiceManager.class);
            when(serviceManager.getAuthenticationService()).thenReturn(authenticationService);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

            ServerConfService serverConfService = mock(ServerConfService.class);
            when(serverConfService.get("RelayPort", Integer.class)).thenReturn(5896);
            GameManager gameManager = mock(GameManager.class);
            when(gameManager.getServerConfService()).thenReturn(serverConfService);
            ReflectionTestUtils.setField(GameManager.class, "instance", gameManager);

            GameSessionManager sessionManager = new GameSessionManager();
            sessionManager.init();
            ThreadManager threadManager = mock(ThreadManager.class);
            ReflectionTestUtils.setField(ThreadManager.class, "instance", threadManager);

            Room room = new Room();
            room.setRoomType((byte) RoomType.MATCH);
            room.setMode((byte) com.jftse.server.core.constants.GameMode.GUARDIAN);
            room.setAllowBattlemon((byte) 0);
            room.setPlayers((byte) 2);
            FTClient first = guardianClientInRoom(room, 100L, (short) 0, pet(10L, "First pet"));
            FTClient second = guardianClientInRoom(room, 200L, (short) 1, pet(20L, "Second pet"));
            first.getRoomPlayer().setMaster(true);
            second.getRoomPlayer().setReady(true);
            when(gameManager.getClientsInRoom(room.getRoomId())).thenReturn(List.of(first, second));

            GameServer relayServer = new GameServer();
            relayServer.setHost("127.0.0.1");
            relayServer.setPort(5896);
            when(authenticationService.getGameServerByPort(5896)).thenReturn(relayServer);

            new RoomStartGamePacketHandler().handle(first.getConnection(), CMSGStartGame.builder().build());

            assertEquals(RoomStatus.StartingGame, room.getStatus());
            assertEquals(1, sessionManager.getGameSessionList().size());
            GameSession session = sessionManager.getGameSessionList().values().iterator().next();
            assertEquals(List.of((short) 0, (short) 1), session.getGameplayActorPositions());
            assertTrue(session.isGameplayEndpoint(first));
            assertTrue(session.isGameplayEndpoint(second));
            verify(threadManager).schedule(any(Runnable.class), eq(0L), eq(TimeUnit.SECONDS));
        } finally {
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousSessionManager);
            ReflectionTestUtils.setField(ThreadManager.class, "instance", previousThreadManager);
        }
    }

    @Test
    void guardianBattlemonStartRequiresBothPetsAndAcceptsBothOwners() {
        Object previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        Object previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        Object previousSessionManager = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        Object previousThreadManager = ReflectionTestUtils.getField(ThreadManager.class, "instance");
        try {
            AuthenticationService authenticationService = mock(AuthenticationService.class);
            PetService petService = mock(PetService.class);
            ServiceManager serviceManager = mock(ServiceManager.class);
            when(serviceManager.getAuthenticationService()).thenReturn(authenticationService);
            when(serviceManager.getPetService()).thenReturn(petService);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

            ServerConfService serverConfService = mock(ServerConfService.class);
            when(serverConfService.get("RelayPort", Integer.class)).thenReturn(5896);
            RProducerService producer = mock(RProducerService.class);
            GameManager gameManager = mock(GameManager.class);
            when(gameManager.getServerConfService()).thenReturn(serverConfService);
            when(gameManager.getRProducerService()).thenReturn(producer);
            ReflectionTestUtils.setField(GameManager.class, "instance", gameManager);

            GameSessionManager sessionManager = new GameSessionManager();
            sessionManager.init();
            ThreadManager threadManager = mock(ThreadManager.class);
            ReflectionTestUtils.setField(ThreadManager.class, "instance", threadManager);

            Room room = new Room();
            room.setRoomType((byte) RoomType.MATCH);
            room.setMode((byte) com.jftse.server.core.constants.GameMode.GUARDIAN);
            room.setAllowBattlemon((byte) 1);
            room.setPlayers((byte) 4);
            room.getPositions().set(0, RoomPositionState.InUse);
            room.getPositions().set(1, RoomPositionState.InUse);
            room.getPositions().set(2, RoomPositionState.InUse);
            room.getPositions().set(3, RoomPositionState.InUse);

            Pet firstPet = pet(10L, "First pet");
            Pet secondPet = pet(20L, "Second pet");
            FTClient first = guardianClientInRoom(room, 100L, (short) 0, firstPet);
            FTClient second = guardianClientInRoom(room, 200L, (short) 1, secondPet);
            first.getRoomPlayer().setMaster(true);
            second.getRoomPlayer().setReady(true);
            first.getRoomPlayer().setPet(PetView.of(firstPet));
            when(gameManager.getClientsInRoom(room.getRoomId())).thenReturn(List.of(first, second));
            when(first.getConnection().getRemoteAddressTCP())
                    .thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), 10000));
            when(second.getConnection().getRemoteAddressTCP())
                    .thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), 10001));
            when(petService.findByIdAndPlayerId(10L, 100L)).thenReturn(firstPet);
            when(petService.findByIdAndPlayerId(20L, 200L)).thenReturn(secondPet);

            RoomStartGamePacketHandler handler = new RoomStartGamePacketHandler();
            handler.handle(first.getConnection(), CMSGStartGame.builder().build());

            assertEquals(RoomStatus.NotRunning, room.getStatus());
            assertEquals(List.of(0x17E6), sentPacketIds(first.getConnection()));
            verify(producer, never()).sendRelayActorPolicy(
                    any(RelaySessionAuthorizationMessage.class),
                    eq("MatchplaySystem(GameServer)"));

            first.getRoomPlayer().setPet(PetView.of(firstPet));
            second.getRoomPlayer().setPet(PetView.of(secondPet));
            secondPet.setEnergy(0);
            handler.handle(first.getConnection(), CMSGStartGame.builder().build());

            assertEquals(RoomStatus.NotRunning, room.getStatus());
            assertTrue(sessionManager.getGameSessionList().isEmpty());
            verify(producer, never()).sendRelayActorPolicy(
                    any(RelaySessionAuthorizationMessage.class),
                    eq("MatchplaySystem(GameServer)"));

            secondPet.setEnergy(50);
            GameServer relayServer = new GameServer();
            relayServer.setHost("127.0.0.1");
            relayServer.setPort(5896);
            when(authenticationService.getGameServerByPort(5896)).thenReturn(relayServer);

            handler.handle(first.getConnection(), CMSGStartGame.builder().build());

            assertEquals(RoomStatus.NotRunning, room.getStatus());
            assertTrue(sessionManager.getGameSessionList().isEmpty());
            verify(producer).sendRelayActorPolicy(
                    any(RelaySessionAuthorizationMessage.class),
                    eq("MatchplaySystem(GameServer)"));
            verify(threadManager, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

            when(producer.sendRelayActorPolicy(
                    any(RelaySessionAuthorizationMessage.class),
                    eq("MatchplaySystem(GameServer)"))).thenReturn(true);
            second.getRoomPlayer().setReady(true);

            handler.handle(first.getConnection(), CMSGStartGame.builder().build());

            assertEquals(RoomStatus.StartingGame, room.getStatus(),
                    () -> "sent packets: " + sentPacketIds(first.getConnection()) +
                            ", sessions: " + sessionManager.getGameSessionList().size());
            assertEquals(1, sessionManager.getGameSessionList().size());
            GameSession session = sessionManager.getGameSessionList().values().iterator().next();
            assertFalse(session.isDedicatedBattlemonRoom());
            assertEquals(List.of((short) 0, (short) 1, (short) 2, (short) 3),
                    session.getGameplayActorPositions());
            assertNotNull(session.getOwnedPetSeat(100L));
            assertNotNull(session.getOwnedPetSeat(200L));
            verify(producer, times(2)).sendRelayActorPolicy(
                    any(RelaySessionAuthorizationMessage.class),
                    eq("MatchplaySystem(GameServer)"));
            verify(threadManager).schedule(any(Runnable.class), eq(0L), eq(TimeUnit.SECONDS));
        } finally {
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousSessionManager);
            ReflectionTestUtils.setField(ThreadManager.class, "instance", previousThreadManager);
        }
    }

    @Test
    void repeatedStartupAbortCannotResetOrDetachAReplacementSession() {
        Object previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        Object previousSessionManager = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        try {
            ReflectionTestUtils.setField(GameManager.class, "instance", mock(GameManager.class));
            GameSessionManager sessionManager = new GameSessionManager();
            sessionManager.init();

            Room room = new Room();
            room.setStatus(RoomStatus.StartingGame);
            RoomPlayer roomPlayer = mock(RoomPlayer.class);
            AtomicBoolean connectedToRelay = new AtomicBoolean(true);
            when(roomPlayer.getConnectedToRelay()).thenReturn(connectedToRelay);
            room.getRoomPlayerList().add(roomPlayer);

            GameSession abortedSession = new GameSession();
            int sessionId = sessionManager.addGameSession(abortedSession);
            FTClient client = new FTClient();
            client.setActiveGameSession(sessionId);

            RoomStartGamePacketHandler.abortStart(
                    room, sessionId, abortedSession, List.of(client));

            assertEquals(RoomStatus.NotRunning, room.getStatus());
            assertNull(client.getGameSessionId());
            assertFalse(connectedToRelay.get());

            GameSession replacementSession = new GameSession();
            sessionManager.getGameSessionList().put(sessionId, replacementSession);
            client.setActiveGameSession(sessionId);
            room.setStatus(RoomStatus.StartingGame);

            RoomStartGamePacketHandler.abortStart(
                    room, sessionId, abortedSession, List.of(client));

            assertEquals(RoomStatus.StartingGame, room.getStatus());
            assertEquals(sessionId, client.getGameSessionId());
            assertEquals(replacementSession, client.getActiveGameSession());
        } finally {
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousSessionManager);
        }
    }

    @Test
    void relayStartupUsesOneSynchronizedStatusSnapshotPerPoll() {
        Object previousSessionManager = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        try {
            GameSessionManager sessionManager = new GameSessionManager();
            sessionManager.init();
            GameSession session = new GameSession();
            int sessionId = sessionManager.addGameSession(session);
            AtomicInteger statusReads = new AtomicInteger();
            Room room = new Room() {
                @Override
                public int getStatus() {
                    return statusReads.getAndIncrement() == 0
                            ? RoomStatus.StartingGame
                            : RoomStatus.RelayConnectionSuccess;
                }
            };
            FTClient client = mock(FTClient.class);
            when(client.getActiveRoom()).thenReturn(room);

            RoomStartGamePacketHandler.RelayStartupPollResult firstPoll =
                    RoomStartGamePacketHandler.pollRelayStartup(
                            room, 0, client, sessionId, session);
            RoomStartGamePacketHandler.RelayStartupPollResult secondPoll =
                    RoomStartGamePacketHandler.pollRelayStartup(
                            room, 0, client, sessionId, session);

            assertEquals(RoomStartGamePacketHandler.RelayStartupPollResult.WAITING, firstPoll);
            assertEquals(RoomStartGamePacketHandler.RelayStartupPollResult.CONNECTED, secondPoll);
            assertEquals(2, statusReads.get());
        } finally {
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousSessionManager);
        }
    }

    @Test
    void ordinaryRelayEndpointRostersKeepDevelopmentRotation() {
        List<FTClient> clients = List.of(client(100L, (short) 0), client(200L, (short) 1),
                client(300L, (short) 2));
        assertEquals(List.of(100L, 200L, 300L), playerIds(
                RoomStartGamePacketHandler.ordinaryRelayEndpointRoster(clients, 0)));
        assertEquals(List.of(300L, 100L, 200L), playerIds(
                RoomStartGamePacketHandler.ordinaryRelayEndpointRoster(clients, 1)));
    }

    @Test
    void battlemonRewardPositionsUseOnlyOwnerSeats() {
        GameSession battlemonSession = new GameSession(true);
        battlemonSession.setPlayers(4);
        battlemonSession.getClients().add(client(100L, (short) 0));
        battlemonSession.getClients().add(client(200L, (short) 1));

        assertEquals(List.of(0, 1), RoomStartGamePacketHandler.rewardPlayerPositions(battlemonSession));
    }

    @Test
    void duplicateRelayReadyIsIdempotentWhileWaitingForAnotherPlayer() {
        Room room = new Room();
        room.setRoomType((byte) RoomType.MATCH);
        room.setStatus(RoomStatus.StartingGame);
        FTClient first = client(100L, (short) 0);
        FTClient second = client(200L, (short) 1);
        AtomicBoolean firstConnected = new AtomicBoolean(false);
        AtomicBoolean secondConnected = new AtomicBoolean(false);
        when(first.getRoomPlayer().getConnectedToRelay()).thenReturn(firstConnected);
        when(second.getRoomPlayer().getConnectedToRelay()).thenReturn(secondConnected);
        room.getRoomPlayerList().add(first.getRoomPlayer());
        room.getRoomPlayerList().add(second.getRoomPlayer());

        GameSession session = mock(GameSession.class);
        when(session.hasOwnedPetSeats()).thenReturn(true);
        when(session.getClients()).thenReturn(new java.util.concurrent.ConcurrentLinkedDeque<>(List.of(first, second)));
        when(session.getGameplayActorPositions()).thenReturn(List.of((short) 0, (short) 1, (short) 2));
        when(first.getActiveRoom()).thenReturn(room);
        when(second.getActiveRoom()).thenReturn(room);
        when(first.getActiveGameSession()).thenReturn(session);
        when(second.getActiveGameSession()).thenReturn(session);
        when(first.getConnection().getClient()).thenReturn(first);
        when(second.getConnection().getClient()).thenReturn(second);

        ConnectedToRelayHandler handler = new ConnectedToRelayHandler();
        handler.handle(first.getConnection(), mock(com.jftse.server.core.shared.packets.matchplay.CMSGConnectedToRelay.class));
        handler.handle(first.getConnection(), mock(com.jftse.server.core.shared.packets.matchplay.CMSGConnectedToRelay.class));

        assertEquals(RoomStatus.StartingGame, room.getStatus());
        assertTrue(firstConnected.get());

        handler.handle(second.getConnection(), mock(com.jftse.server.core.shared.packets.matchplay.CMSGConnectedToRelay.class));

        assertEquals(RoomStatus.RelayConnectionSuccess, room.getStatus());
    }

    @Test
    void battlemonActorChangesAreRejectedOnceRelayStartupBegins() {
        Object previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        Object previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        try {
            ReflectionTestUtils.setField(GameManager.class, "instance", mock(GameManager.class));
            ServiceManager serviceManager = mock(ServiceManager.class);
            when(serviceManager.getPetService()).thenReturn(mock(com.jftse.server.core.service.PetService.class));
            ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

            Room room = new Room();
            room.setRoomType((byte) RoomType.BATTLEMON);
            room.setStatus(RoomStatus.StartingGame);
            RoomPlayer roomPlayer = new RoomPlayer(mock(FTPlayer.class));
            roomPlayer.setMaster(true);
            roomPlayer.setPet(new PetView(10L, 1, "Pet", 1, 100, 1, 1, 1, 1, 100, 100));

            FTClient client = mock(FTClient.class);
            when(client.hasPlayer()).thenReturn(true);
            when(client.getActiveRoom()).thenReturn(room);
            when(client.getRoomPlayer()).thenReturn(roomPlayer);
            when(client.getIsGoingReady()).thenReturn(new AtomicBoolean(false));
            when(client.getIsChangingSlot()).thenReturn(new AtomicBoolean(false));
            FTConnection connection = mock(FTConnection.class);
            when(connection.getClient()).thenReturn(client);

            new RoomReadyChangeRequestPacketHandler().handle(connection,
                    CMSGRoomChangeReady.builder().ready(true).build());
            new RoomPositionChangeRequestPacketHandler().handle(connection,
                    CMSGRoomChangePosition.builder().position((short) 1).build());
            new RoomRequestPetPacketHandler().handle(connection,
                    CMSGRequestPet.builder().slot((byte) 0).build());

            assertFalse(roomPlayer.isReady());
            assertEquals(0, roomPlayer.getPosition());
            assertEquals(10L, roomPlayer.getPet().id());
        } finally {
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
        }
    }

    @Test
    void battlemonRoomPetRequestCannotDetachTheAdmittedPet() {
        Object previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        Object previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        try {
            ReflectionTestUtils.setField(GameManager.class, "instance", mock(GameManager.class));
            ServiceManager serviceManager = mock(ServiceManager.class);
            when(serviceManager.getPetService()).thenReturn(mock(PetService.class));
            ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

            Room room = new Room();
            room.setRoomType((byte) RoomType.BATTLEMON);
            room.setAllowBattlemon((byte) 1);
            RoomPlayer roomPlayer = new RoomPlayer(mock(FTPlayer.class));
            PetView admittedPet = new PetView(10L, 1, "Pet", 1, 100, 1, 1, 1, 1, 100, 100);
            roomPlayer.setPet(admittedPet);

            FTClient client = mock(FTClient.class);
            when(client.getActiveRoom()).thenReturn(room);
            when(client.getRoomPlayer()).thenReturn(roomPlayer);
            FTConnection connection = mock(FTConnection.class);
            when(connection.getClient()).thenReturn(client);

            new RoomRequestPetPacketHandler().handle(connection,
                    CMSGRequestPet.builder().slot((byte) 0).build());

            assertEquals(admittedPet, roomPlayer.getPet());
            assertEquals(List.of(0x1D57), sentPacketIds(connection));
        } finally {
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
        }
    }

    @Test
    void guardianRoomPetRequestDetachesThePetAndFreesItsReservedSlot() {
        Object previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        Object previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        try {
            ReflectionTestUtils.setField(GameManager.class, "instance", mock(GameManager.class));
            ServiceManager serviceManager = mock(ServiceManager.class);
            when(serviceManager.getPetService()).thenReturn(mock(PetService.class));
            ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

            Room room = new Room();
            room.setMode((byte) com.jftse.server.core.constants.GameMode.GUARDIAN);
            room.setAllowBattlemon((byte) 1);
            room.getPositions().set(0, RoomPositionState.InUse);
            room.getPositions().set(2, RoomPositionState.InUse);
            RoomPlayer roomPlayer = new RoomPlayer(mock(FTPlayer.class));
            roomPlayer.setPosition((short) 0);
            roomPlayer.setPet(new PetView(10L, 1, "Pet", 1, 100, 1, 1, 1, 1, 100, 100));
            room.getRoomPlayerList().add(roomPlayer);

            FTClient client = mock(FTClient.class);
            when(client.getActiveRoom()).thenReturn(room);
            when(client.getRoomPlayer()).thenReturn(roomPlayer);
            FTConnection connection = mock(FTConnection.class);
            when(connection.getClient()).thenReturn(client);

            new RoomRequestPetPacketHandler().handle(connection,
                    CMSGRequestPet.builder().slot((byte) 0).build());

            assertNull(roomPlayer.getPet());
            assertEquals(RoomPositionState.Free, room.getPositions().get(2));
        } finally {
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
        }
    }

    @Test
    void battlemonRoomRejectsActivePetDetachAndChangeButAllowsIdempotentSelection() {
        Object previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        try {
            PetService petService = mock(PetService.class);
            ServiceManager serviceManager = mock(ServiceManager.class);
            when(serviceManager.getPetService()).thenReturn(petService);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

            Room room = new Room();
            room.setRoomType((byte) RoomType.BATTLEMON);
            RoomPlayer roomPlayer = new RoomPlayer(mock(FTPlayer.class));
            PetView admittedPet = new PetView(10L, 1, "Pet", 1, 100, 1, 1, 1, 1, 100, 100);
            roomPlayer.setPet(admittedPet);

            FTClient client = mock(FTClient.class);
            when(client.hasPlayer()).thenReturn(true);
            when(client.getActiveRoom()).thenReturn(room);
            when(client.getRoomPlayer()).thenReturn(roomPlayer);
            when(client.getActivePet()).thenReturn(admittedPet);
            FTConnection connection = mock(FTConnection.class);
            when(connection.getClient()).thenReturn(client);

            PetPickupRequestPacketHandler handler = new PetPickupRequestPacketHandler();
            handler.handle(connection, CMSGPickupPet.builder().petType(-1).build());
            handler.handle(connection, CMSGPickupPet.builder().petType(2).build());
            handler.handle(connection, CMSGPickupPet.builder().petType(1).build());

            verify(client, never()).setActivePet(any(Pet.class));
            verify(petService, never()).findAllByPlayerId(anyLong());
            List<SMSGPickupPet> answers = sentPackets(connection).stream()
                    .map(SMSGPickupPet.class::cast)
                    .toList();
            assertEquals(List.of((short) 1, (short) 1, (short) 0),
                    answers.stream().map(SMSGPickupPet::getResult).toList());
            assertEquals(admittedPet, roomPlayer.getPet());
        } finally {
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
        }
    }

    @Test
    void guardianOwnersCanReserveBothPetSlots() {
        Object previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        Object previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        try {
            ReflectionTestUtils.setField(GameManager.class, "instance", mock(GameManager.class));
            PetService petService = mock(PetService.class);
            ServiceManager serviceManager = mock(ServiceManager.class);
            when(serviceManager.getPetService()).thenReturn(petService);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

            Room room = new Room();
            room.setRoomType((byte) RoomType.MATCH);
            room.setMode((byte) com.jftse.server.core.constants.GameMode.GUARDIAN);
            room.setAllowBattlemon((byte) 1);
            room.getPositions().set(0, RoomPositionState.InUse);
            room.getPositions().set(1, RoomPositionState.InUse);

            FTClient first = guardianClientInRoom(room, 100L, (short) 0, pet(10L, "First pet"));
            FTClient second = guardianClientInRoom(room, 200L, (short) 1, pet(20L, "Second pet"));
            when(petService.findByIdAndPlayerId(10L, 100L)).thenReturn(pet(10L, "First pet"));
            when(petService.findByIdAndPlayerId(20L, 200L)).thenReturn(pet(20L, "Second pet"));

            RoomRequestPetPacketHandler handler = new RoomRequestPetPacketHandler();
            handler.handle(first.getConnection(), CMSGRequestPet.builder().slot((byte) 0).build());
            handler.handle(second.getConnection(), CMSGRequestPet.builder().slot((byte) 1).build());

            assertNotNull(first.getRoomPlayer().getPet());
            assertNotNull(second.getRoomPlayer().getPet());
            assertEquals(10L, first.getRoomPlayer().getPet().id());
            assertEquals(20L, second.getRoomPlayer().getPet().id());
            assertEquals(RoomPositionState.InUse, room.getPositions().get(2));
            assertEquals(RoomPositionState.InUse, room.getPositions().get(3));
        } finally {
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
        }
    }

    @Test
    void disablingGuardianPetsDetachesBothAndFreesReservations() {
        Object previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        try {
            GameManager gameManager = mock(GameManager.class);
            ReflectionTestUtils.setField(GameManager.class, "instance", gameManager);
            Room room = guardianRoomWithTwoAttachedPets();
            RoomPlayer master = room.getRoomPlayerList().getFirst();
            master.setMaster(true);
            FTClient client = mock(FTClient.class);
            when(client.hasPlayer()).thenReturn(true);
            when(client.getActiveRoom()).thenReturn(room);
            when(client.getRoomPlayer()).thenReturn(master);
            FTConnection connection = mock(FTConnection.class);
            when(connection.getClient()).thenReturn(client);

            new RoomAllowBattlemonChangePacketHandler().handle(connection,
                    CMSGRoomChangeAllowBattlemon.builder().allowBattlemon((byte) 0).build());

            assertEquals(0, room.getAllowBattlemon());
            assertTrue(room.getRoomPlayerList().stream().allMatch(player -> player.getPet() == null));
            assertEquals(RoomPositionState.Free, room.getPositions().get(2));
            assertEquals(RoomPositionState.Free, room.getPositions().get(3));
        } finally {
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
        }
    }

    @Test
    void changingGuardianToBasicDetachesPetsAndFreesReservations() {
        Object previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        try {
            GameManager gameManager = mock(GameManager.class);
            when(gameManager.getClientsInRoom((short) 0)).thenReturn(List.of());
            when(gameManager.getClientsInLobby()).thenReturn(List.of());
            ReflectionTestUtils.setField(GameManager.class, "instance", gameManager);
            Room room = guardianRoomWithTwoAttachedPets();
            RoomPlayer master = room.getRoomPlayerList().getFirst();
            master.setMaster(true);
            FTPlayer player = mock(FTPlayer.class);
            FTClient client = mock(FTClient.class);
            when(client.hasPlayer()).thenReturn(true);
            when(client.getPlayer()).thenReturn(player);
            when(client.getActiveRoom()).thenReturn(room);
            when(client.getRoomPlayer()).thenReturn(master);
            FTConnection connection = mock(FTConnection.class);
            when(connection.getClient()).thenReturn(client);

            new GameModeChangePacketHandler().handle(connection,
                    CMSGRoomChangeGameMode.builder()
                            .mode((byte) com.jftse.server.core.constants.GameMode.BASIC)
                            .build());

            assertEquals(com.jftse.server.core.constants.GameMode.BASIC, room.getMode());
            assertTrue(room.getRoomPlayerList().stream().allMatch(roomPlayer -> roomPlayer.getPet() == null));
            assertEquals(RoomPositionState.Free, room.getPositions().get(2));
            assertEquals(RoomPositionState.Free, room.getPositions().get(3));
        } finally {
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
        }
    }

    @Test
    void changingBattlemonModeClearsAndBroadcastsEveryReadyState() {
        Object previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        try {
            GameManager gameManager = mock(GameManager.class);
            when(gameManager.getClientsInRoom((short) 0)).thenReturn(List.of());
            when(gameManager.getClientsInLobby()).thenReturn(List.of());
            ReflectionTestUtils.setField(GameManager.class, "instance", gameManager);

            Room room = new Room();
            room.setRoomType((byte) RoomType.BATTLEMON);
            room.setMode((byte) com.jftse.server.core.constants.GameMode.BASIC);
            RoomPlayer master = new RoomPlayer(mock(FTPlayer.class));
            master.setPosition((short) 0);
            master.setMaster(true);
            master.setReady(true);
            RoomPlayer guest = new RoomPlayer(mock(FTPlayer.class));
            guest.setPosition((short) 1);
            guest.setReady(true);
            room.getRoomPlayerList().addAll(List.of(master, guest));

            FTClient client = mock(FTClient.class);
            when(client.hasPlayer()).thenReturn(true);
            when(client.getPlayer()).thenReturn(mock(FTPlayer.class));
            when(client.getActiveRoom()).thenReturn(room);
            when(client.getRoomPlayer()).thenReturn(master);
            FTConnection connection = mock(FTConnection.class);
            when(connection.getClient()).thenReturn(client);

            new GameModeChangePacketHandler().handle(connection,
                    CMSGRoomChangeGameMode.builder()
                            .mode((byte) com.jftse.server.core.constants.GameMode.BATTLE)
                            .build());

            assertFalse(master.isReady());
            assertFalse(guest.isReady());
            verify(gameManager, times(2)).sendPacketToAllClientsInSameRoom(
                    any(SMSGRoomChangeReady.class), eq(connection));
        } finally {
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
        }
    }

    @Test
    void guardianAttachedPetAlsoLocksInventorySelection() {
        Object previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        try {
            PetService petService = mock(PetService.class);
            ServiceManager serviceManager = mock(ServiceManager.class);
            when(serviceManager.getPetService()).thenReturn(petService);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

            Room room = new Room();
            room.setMode((byte) com.jftse.server.core.constants.GameMode.GUARDIAN);
            PetView attachedPet = new PetView(10L, 1, "Pet", 1, 100, 1, 1, 1, 1, 100, 100);
            RoomPlayer roomPlayer = new RoomPlayer(mock(FTPlayer.class));
            roomPlayer.setPet(attachedPet);
            FTClient client = mock(FTClient.class);
            when(client.hasPlayer()).thenReturn(true);
            when(client.getActiveRoom()).thenReturn(room);
            when(client.getRoomPlayer()).thenReturn(roomPlayer);
            when(client.getActivePet()).thenReturn(attachedPet);
            FTConnection connection = mock(FTConnection.class);
            when(connection.getClient()).thenReturn(client);

            PetPickupRequestPacketHandler handler = new PetPickupRequestPacketHandler();
            handler.handle(connection, CMSGPickupPet.builder().petType(-1).build());
            handler.handle(connection, CMSGPickupPet.builder().petType(2).build());
            handler.handle(connection, CMSGPickupPet.builder().petType(1).build());

            verify(client, never()).setActivePet(any(Pet.class));
            verify(petService, never()).findAllByPlayerId(anyLong());
            List<SMSGPickupPet> answers = sentPackets(connection).stream()
                    .map(SMSGPickupPet.class::cast)
                    .toList();
            assertEquals(List.of((short) 1, (short) 1, (short) 0),
                    answers.stream().map(SMSGPickupPet::getResult).toList());
            assertEquals(attachedPet, roomPlayer.getPet());
        } finally {
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
        }
    }

    private static List<Long> playerIds(List<FTClient> clients) {
        return clients.stream().map(client -> client.getPlayer().getId()).toList();
    }

    private static List<Integer> sentPacketIds(FTConnection connection) {
        return sentPackets(connection).stream()
                .map(packet -> (int) packet.getPacketId())
                .toList();
    }

    private static List<IPacket> sentPackets(FTConnection connection) {
        return org.mockito.Mockito.mockingDetails(connection).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("sendTCP"))
                .flatMap(invocation -> Arrays.stream(invocation.getArguments()))
                .flatMap(argument -> argument instanceof IPacket[] packets
                        ? Arrays.stream(packets)
                        : argument instanceof IPacket packet
                        ? java.util.stream.Stream.of(packet)
                        : java.util.stream.Stream.empty())
                .toList();
    }

    private static FTClient client(long playerId, short position) {
        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(playerId);
        RoomPlayer roomPlayer = mock(RoomPlayer.class);
        when(roomPlayer.getPlayerId()).thenReturn(playerId);
        when(roomPlayer.getPosition()).thenReturn(position);
        FTClient client = mock(FTClient.class);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        FTConnection connection = mock(FTConnection.class);
        when(connection.getRemoteAddressTCP()).thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), 10000));
        when(client.getConnection()).thenReturn(connection);
        return client;
    }

    private static FTClient guardianClientInRoom(Room room, long playerId, short position, Pet pet) {
        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(playerId);
        when(player.getItemStats()).thenReturn(new EquippedItemStats());
        when(player.getItemPartsItemIndex()).thenReturn(
                new EquippedItemParts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        RoomPlayer roomPlayer = new RoomPlayer(player);
        roomPlayer.setPosition(position);
        room.getRoomPlayerList().add(roomPlayer);
        FTClient client = mock(FTClient.class);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getActiveRoom()).thenReturn(room);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        when(client.getActivePet()).thenReturn(PetView.of(pet));
        when(client.getGameSessionId()).thenReturn(null);
        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        when(client.getConnection()).thenReturn(connection);
        return client;
    }

    private static Room guardianRoomWithTwoAttachedPets() {
        Room room = new Room();
        room.setRoomType((byte) RoomType.MATCH);
        room.setMode((byte) com.jftse.server.core.constants.GameMode.GUARDIAN);
        room.setAllowBattlemon((byte) 1);
        room.getPositions().set(0, RoomPositionState.InUse);
        room.getPositions().set(1, RoomPositionState.InUse);
        room.getPositions().set(2, RoomPositionState.InUse);
        room.getPositions().set(3, RoomPositionState.InUse);
        RoomPlayer first = new RoomPlayer(mock(FTPlayer.class));
        first.setPosition((short) 0);
        first.setPet(new PetView(10L, 1, "First pet", 1, 100, 1, 1, 1, 1, 100, 100));
        RoomPlayer second = new RoomPlayer(mock(FTPlayer.class));
        second.setPosition((short) 1);
        second.setPet(new PetView(20L, 2, "Second pet", 1, 100, 1, 1, 1, 1, 100, 100));
        room.getRoomPlayerList().add(first);
        room.getRoomPlayerList().add(second);
        return room;
    }

    private static Pet pet(long id, String name) {
        Pet pet = new Pet();
        pet.setId(id);
        pet.setType((byte) 1);
        pet.setName(name);
        pet.setLevel(1);
        pet.setHp(100);
        pet.setStrength((byte) 1);
        pet.setStamina((byte) 1);
        pet.setDexterity((byte) 1);
        pet.setWillpower((byte) 1);
        pet.setHunger(100);
        pet.setEnergy(100);
        pet.setAlive(true);
        pet.setValidUntil(Date.from(Instant.now().plus(30, ChronoUnit.DAYS)));
        pet.setPetStatistic(new PetStatistic());
        return pet;
    }
}
