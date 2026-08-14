package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.constants.MiscConstants;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.GameplayActor;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.matchplay.MatchplayGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.core.packets.lobby.room.S2CPetRequestRoomAnswerPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomPlayerListInformationPacket;
import com.jftse.emulator.server.core.packets.matchplay.S2CGameNetworkSettingsPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.gameserver.GameServer;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.item.BattlemonController;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import com.jftse.server.core.service.AuthenticationService;
import com.jftse.server.core.service.PetService;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.shared.ServerConfService;
import com.jftse.server.core.shared.packets.matchplay.*;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import com.jftse.server.core.thread.ThreadManager;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Log4j2
@PacketId(CMSGStartGame.PACKET_ID)
public class RoomStartGamePacketHandler implements PacketHandler<FTConnection, CMSGStartGame> {
    private final AuthenticationService authenticationService;
    private final PetService petService;
    private final ServerConfService serverConfService;

    public RoomStartGamePacketHandler() {
        this.authenticationService = ServiceManager.getInstance().getAuthenticationService();
        this.petService = ServiceManager.getInstance().getPetService();
        this.serverConfService = GameManager.getInstance().getServerConfService();
    }

    @Override
    public void handle(FTConnection connection, CMSGStartGame packet) {
        Packet roomStartGameAck = new Packet(PacketOperations.S2CRoomStartGameAck);
        roomStartGameAck.write((char) 0);

        FTClient ftClient = connection.getClient();

        if (!ftClient.hasPlayer()) {
            connection.sendTCP(roomStartGameAck);
            return;
        }

        Room room = ftClient.getActiveRoom();
        if (room == null) {
            connection.sendTCP(roomStartGameAck);
            return;
        }
        RoomPlayer requestingPlayer = ftClient.getRoomPlayer();
        if (requestingPlayer == null || !requestingPlayer.isMaster()) {
            connection.sendTCP(roomStartGameAck);
            return;
        }

        int requestedRoomType = room.getRoomType();
        int requestedMode = room.getMode();
        boolean isBattlemon = requestedRoomType == RoomType.BATTLEMON;
        if (requestedMode != GameMode.BASIC && requestedMode != GameMode.BATTLE && requestedMode != GameMode.GUARDIAN ||
                isBattlemon && requestedMode == GameMode.GUARDIAN) {
            connection.sendTCP(roomStartGameAck);
            return;
        }

        Map<Long, Pet> selectedBattlemonPets = new HashMap<>();
        List<RoomPlayer> activeRoomPlayers = room.getRoomPlayerList().stream()
                .filter(roomPlayer -> roomPlayer.getPosition() < 4)
                .toList();
        if (activeRoomPlayers.stream().filter(roomPlayer -> !roomPlayer.isMaster()).anyMatch(roomPlayer -> !roomPlayer.isReady())) {
            connection.sendTCP(roomStartGameAck);
            return;
        }
        if (isBattlemon) {
            boolean hasRequiredOwners = room.getRoomPlayerList().size() == 2 && activeRoomPlayers.size() == 2 &&
                    activeRoomPlayers.stream().anyMatch(roomPlayer -> roomPlayer.getPosition() == 0) &&
                    activeRoomPlayers.stream().anyMatch(roomPlayer -> roomPlayer.getPosition() == 1);
            if (!hasRequiredOwners) {
                connection.sendTCP(roomStartGameAck);
                return;
            }
        }

        boolean allowsGuardianBattlemon = !isBattlemon && requestedMode == GameMode.GUARDIAN &&
                room.getAllowBattlemon() != 0;
        for (RoomPlayer roomPlayer : activeRoomPlayers) {
            PetView petView = roomPlayer.getPet();
            if (petView == null) {
                continue;
            }
            int petPosition = roomPlayer.getPosition() + 2;
            boolean petPositionHasPlayer = activeRoomPlayers.stream()
                    .anyMatch(player -> player.getPosition() == petPosition);
            if (!isBattlemon && !allowsGuardianBattlemon ||
                    roomPlayer.getPosition() < 0 || roomPlayer.getPosition() > 1 ||
                    petPositionHasPlayer ||
                    !isBattlemon && room.getPositions().get(petPosition) != RoomPositionState.InUse) {
                connection.sendTCP(roomStartGameAck);
                return;
            }
            Pet pet = petService.findByIdAndPlayerId(petView.id(), roomPlayer.getPlayerId());
            if (pet == null || !Boolean.TRUE.equals(pet.getAlive()) ||
                    pet.getValidUntil() == null || pet.getValidUntil().before(new Date())) {
                connection.sendTCP(roomStartGameAck);
                return;
            }
            selectedBattlemonPets.put(roomPlayer.getPlayerId(), pet);
        }
        if ((isBattlemon || allowsGuardianBattlemon) &&
                selectedBattlemonPets.size() != activeRoomPlayers.size()) {
            connection.sendTCP(roomStartGameAck);
            return;
        }

        GameServer relayServer = authenticationService.getGameServerByPort(this.serverConfService.get("RelayPort", Integer.class));
        if (relayServer == null) {
            connection.sendTCP(roomStartGameAck);
            return;
        }

        List<FTClient> roomClients = new ArrayList<>(GameManager.getInstance().getClientsInRoom(room.getRoomId()));
        List<FTClient> clientsInRoom = isBattlemon
                ? roomClients.stream()
                        .filter(client -> client.getRoomPlayer() != null && client.getRoomPlayer().getPosition() < 2)
                        .toList()
                : roomClients;
        if (isBattlemon) {
            boolean hasOwnerEndpoints = roomClients.size() == 2 && clientsInRoom.size() == 2 &&
                    clientsInRoom.stream().map(client -> client.getRoomPlayer().getPosition()).distinct().count() == 2;
            if (!hasOwnerEndpoints) {
                connection.sendTCP(roomStartGameAck);
                return;
            }
        }
        if (clientsInRoom.stream().anyMatch(client ->
                client.getConnection() == null || client.getGameSessionId() != null)) {
            connection.sendTCP(roomStartGameAck);
            return;
        }

        GameSession gameSession = new GameSession(isBattlemon);
        MatchplayGame game;
        try {
            gameSession.setPlayers(isBattlemon ? 4 : room.getPlayers());
            gameSession.getClients().addAll(clientsInRoom);
            if (!selectedBattlemonPets.isEmpty()) {
                activeRoomPlayers.stream()
                        .filter(roomPlayer -> selectedBattlemonPets.containsKey(roomPlayer.getPlayerId()))
                        .forEach(roomPlayer -> gameSession.addOwnedPetSeat(
                        roomPlayer,
                        selectedBattlemonPets.get(roomPlayer.getPlayerId())
                ));
            }
            gameSession.initializeGameplayActorPositions();
            List<Integer> rewardPlayerPositions = rewardPlayerPositions(gameSession);
            game = switch (requestedMode) {
                case GameMode.BASIC -> new MatchplayBasicGame(
                        (byte) gameSession.getPlayers(),
                        rewardPlayerPositions
                );
                case GameMode.BATTLE -> new MatchplayBattleGame(
                        (byte) gameSession.getPlayers(),
                        rewardPlayerPositions
                );
                case GameMode.GUARDIAN -> new MatchplayGuardianGame();
                default -> throw new IllegalStateException("room mode not supported: " + requestedMode);
            };
            gameSession.setMatchplayGame(game);
        } catch (RuntimeException e) {
            log.warn("Unable to build game session for room {}", room.getRoomId(), e);
            connection.sendTCP(roomStartGameAck);
            return;
        }

        synchronized (room) {
            boolean battlemonLayoutChanged = isBattlemon &&
                    (room.getRoomPlayerList().size() != 2 || clientsInRoom.stream().anyMatch(client ->
                            client.getActiveRoom() != room || client.getRoomPlayer() == null ||
                                    client.getRoomPlayer().getPosition() < 0 || client.getRoomPlayer().getPosition() > 1));
            List<RoomPlayer> currentActiveRoomPlayers = room.getRoomPlayerList().stream()
                    .filter(roomPlayer -> roomPlayer.getPosition() < 4)
                    .toList();
            boolean battlemonSelectionChanged = currentActiveRoomPlayers.size() != activeRoomPlayers.size() ||
                    currentActiveRoomPlayers.stream().anyMatch(current -> {
                        Pet selectedPet = selectedBattlemonPets.get(current.getPlayerId());
                        Long selectedPetId = selectedPet == null ? null : selectedPet.getId();
                        Long currentPetId = current.getPet() == null ? null : current.getPet().id();
                        int petPosition = current.getPosition() + 2;
                        return !Objects.equals(currentPetId, selectedPetId) || currentPetId != null &&
                                (!isBattlemon && room.getPositions().get(petPosition) != RoomPositionState.InUse ||
                                        currentActiveRoomPlayers.stream()
                                                .anyMatch(player -> player.getPosition() == petPosition));
                    });
            boolean readinessChanged = activeRoomPlayers.stream()
                    .anyMatch(roomPlayer -> !roomPlayer.isMaster() && !roomPlayer.isReady());
            if (room.getStatus() != RoomStatus.NotRunning ||
                    room.getRoomType() != requestedRoomType || room.getMode() != requestedMode ||
                    GameSessionManager.getInstance().hasMatchplayReward(room.getRoomId()) ||
                    !selectedBattlemonPets.isEmpty() && room.getAllowBattlemon() == 0 ||
                    readinessChanged || battlemonLayoutChanged || battlemonSelectionChanged) {
                connection.sendTCP(roomStartGameAck);
                return;
            }
            room.setStatus(RoomStatus.StartingGame);
        }

        Integer gameSessionId = GameSessionManager.getInstance().addGameSession(gameSession);
        try {
            RelaySessionAuthorizationMessage relayAuthorization = createRelayAuthorization(
                    gameSessionId,
                    clientsInRoom,
                    gameSession
            );
            GameManager.getInstance().getRProducerService().sendNow(
                    relayAuthorization,
                    RelaySessionAuthorizationMessage.ROUTING_KEY,
                    "MatchplaySystem(GameServer)"
            );
        } catch (RuntimeException e) {
            abortStart(room, gameSessionId, gameSession, clientsInRoom);
            connection.sendTCP(roomStartGameAck);
            log.warn("Unable to authorize relay session {} for room {}", gameSessionId, room.getRoomId(), e);
            return;
        }

        SMSGUnsetHost unsetHostPacket = SMSGUnsetHost.builder().result((byte) 0).build();
        try {
            clientsInRoom.forEach(c -> {
                c.setActiveGameSession(gameSessionId);
            });

            if (game instanceof MatchplayGuardianGame guardianGame) {
                List<RoomPlayer> guardianPlayers = room.getRoomPlayerList().stream()
                        .filter(roomPlayer -> roomPlayer.getPosition() < 4)
                        .toList();
                Map<RoomPlayer, Integer> playerMaxHealth = guardianPlayers.stream()
                        .collect(Collectors.toMap(
                                roomPlayer -> roomPlayer,
                                roomPlayer -> MatchplayGuardianGame.calculatePlayerMaxHealth(
                                        roomPlayer,
                                        guardianPlayers
                                )
                        ));
                guardianGame.setInitialPlayerMaxHealth(Map.copyOf(playerMaxHealth));

                clientsInRoom.forEach(client -> {
                    boolean isInvisibleGm = room.getRoomPlayerList().stream()
                            .anyMatch(roomPlayer -> roomPlayer.getPosition() == MiscConstants.InvisibleGmSlot &&
                                    client.hasPlayer() && roomPlayer.getPlayerId() == client.getPlayer().getId());
                    List<RoomPlayer> visibleRoomPlayers = isInvisibleGm
                            ? room.getRoomPlayerList().stream().toList()
                            : room.getRoomPlayerList().stream()
                                    .filter(roomPlayer -> roomPlayer.getPosition() != MiscConstants.InvisibleGmSlot)
                                    .toList();
                    client.getConnection().sendTCP(
                            new S2CRoomPlayerListInformationPacket(visibleRoomPlayers, playerMaxHealth));
                });
            }

            for (int recipientIndex = 0; recipientIndex < clientsInRoom.size(); recipientIndex++) {
                FTClient c = clientsInRoom.get(recipientIndex);
                c.getConnection().sendTCP(unsetHostPacket);

                List<FTClient> relayEndpointRoster = relayEndpointRoster(clientsInRoom, recipientIndex);
                S2CGameNetworkSettingsPacket networkSettingsPacket = new S2CGameNetworkSettingsPacket(
                        relayServer.getHost(), relayServer.getPort(),
                        gameSessionId, gameSession, relayEndpointRoster);
                c.getConnection().sendTCP(networkSettingsPacket);
            }
        } catch (RuntimeException e) {
            abortStartAndNotifyClients(room, gameSessionId, gameSession, clientsInRoom, ftClient);
            log.warn("Unable to connect room {} to the relay", room.getRoomId(), e);
            return;
        }

        int initialRoomPlayerSize = room.getRoomPlayerList().size();

        Runnable relayConnectionTask = () -> {
            Room threadRoom = room;
            long relayDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (true) {
                RelayStartupPollResult pollResult = pollRelayStartup(
                        threadRoom,
                        initialRoomPlayerSize,
                        ftClient,
                        gameSessionId,
                        gameSession,
                        relayDeadline
                );
                if (pollResult == RelayStartupPollResult.CONNECTED) {
                    break;
                }
                if (pollResult == RelayStartupPollResult.ABORT) {
                    abortStartAndNotifyClients(threadRoom, gameSessionId, gameSession, clientsInRoom, ftClient);
                    return;
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    abortStartAndNotifyClients(threadRoom, gameSessionId, gameSession, clientsInRoom, ftClient);
                    return;
                }
            }

            if (ftClient.getActiveRoom() != room ||
                    GameSessionManager.getInstance().getGameSessionBySessionId(gameSessionId) != gameSession) {
                abortStartAndNotifyClients(room, gameSessionId, gameSession, clientsInRoom, ftClient);
                return;
            }

            try {
                synchronized (room) {
                    boolean clientsStillAttached = clientsInRoom.stream().allMatch(client ->
                            client.getActiveRoom() == room && client.getActiveGameSession() == gameSession);
                    if (room.getStatus() != RoomStatus.RelayConnectionSuccess || !clientsStillAttached) {
                        throw new IllegalStateException("Relay startup state changed before match initialization");
                    }
                    room.setStatus(RoomStatus.InitializingGame);
                }

                RoomPlayer playerInSlot0 = room.getRoomPlayerList().stream()
                        .filter(x -> x.getPosition() == 0)
                        .findFirst().orElse(null);
                FTClient clientToHostGame = clientsInRoom.stream()
                        .filter(x -> playerInSlot0 != null && x.hasPlayer() && x.getPlayer().getId() == playerInSlot0.getPlayerId())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Room has no client for gameplay position 0"));
                SMSGSetHost setHostPacket = SMSGSetHost.builder().result((byte) 1).build();
                clientToHostGame.getConnection().sendTCP(setHostPacket);

                SMSGSetHostUnknown setHostUnknownPacket = SMSGSetHostUnknown.builder().build();
                clientToHostGame.getConnection().sendTCP(setHostUnknownPacket);

                game.getHandleable().onPrepare(ftClient);
                if (game instanceof MatchplayBasicGame basicGame && basicGame.getMap() == null ||
                        game instanceof MatchplayBattleGame battleGame &&
                                (battleGame.getMap() == null || !battleGame.hasExactBattleStates(
                                        gameSession.getGameplayActorPositions())) ||
                        game instanceof MatchplayGuardianGame guardianGame &&
                                (guardianGame.getMap() == null || !guardianGame.hasExactBattleStates(
                                        gameSession.getGameplayActorPositions()))) {
                    throw new IllegalStateException("Matchplay preparation did not create all gameplay actors");
                }

                SMSGStartGame startGamePacket = SMSGStartGame.builder().result((char) 0).build();
                GameManager.getInstance().sendPacketToAllClientsInSameGameSession(startGamePacket, ftClient.getConnection());
            } catch (RuntimeException e) {
                abortStartAndNotifyClients(room, gameSessionId, gameSession, clientsInRoom, ftClient);
                log.warn("Unable to initialize game session {} for room {}", gameSessionId, room.getRoomId(), e);
            }
        };
        try {
            ThreadManager.getInstance().schedule(relayConnectionTask, 0, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            abortStartAndNotifyClients(room, gameSessionId, gameSession, clientsInRoom, ftClient);
            log.warn("Unable to schedule relay startup for session {} in room {}", gameSessionId, room.getRoomId(), e);
        }
    }

    static List<Integer> rewardPlayerPositions(GameSession gameSession) {
        return gameSession.getClients().stream()
                .map(FTClient::getRoomPlayer)
                .filter(Objects::nonNull)
                .map(RoomPlayer::getPosition)
                .filter(position -> position >= 0 && position < 4)
                .map(Short::intValue)
                .distinct()
                .sorted()
                .toList();
    }

    static List<FTClient> relayEndpointRoster(List<FTClient> clients, int recipientIndex) {
        List<FTClient> roster = new ArrayList<>(clients.size());
        for (int offset = 0; offset < clients.size(); offset++) {
            roster.add(clients.get((recipientIndex + offset) % clients.size()));
        }
        return roster;
    }

    static RelayStartupPollResult pollRelayStartup(Room room, int initialRoomPlayerSize, FTClient client,
                                                   Integer gameSessionId, GameSession gameSession,
                                                   long relayDeadline) {
        synchronized (room) {
            int status = room.getStatus();
            if (status == RoomStatus.RelayConnectionSuccess) {
                return RelayStartupPollResult.CONNECTED;
            }

            boolean allReady = room.getRoomPlayerList().stream()
                    .filter(rp -> !rp.isMaster())
                    .collect(Collectors.toList())
                    .stream()
                    .filter(rp -> rp.getPosition() < 4)
                    .allMatch(RoomPlayer::isReady);
            boolean roomPlayerSizeChanged = initialRoomPlayerSize != room.getRoomPlayerList().size();
            boolean sessionChanged = GameSessionManager.getInstance().getGameSessionBySessionId(gameSessionId) != gameSession;
            boolean roomChanged = client.getActiveRoom() != room;
            boolean timedOut = System.nanoTime() >= relayDeadline;

            return !allReady || roomPlayerSizeChanged || sessionChanged || roomChanged || timedOut ||
                    status != RoomStatus.StartingGame
                    ? RelayStartupPollResult.ABORT
                    : RelayStartupPollResult.WAITING;
        }
    }

    enum RelayStartupPollResult {
        WAITING,
        CONNECTED,
        ABORT
    }

    static RelaySessionAuthorizationMessage createRelayAuthorization(Integer gameSessionId,
                                                                      List<FTClient> clients,
                                                                      GameSession gameSession) {
        Map<Integer, List<Short>> actorPositionsByPlayerId = new LinkedHashMap<>();
        Map<Integer, String> playerAddresses = new LinkedHashMap<>();
        Map<Integer, Boolean> battlemonControllerByPlayerId = new LinkedHashMap<>();
        for (FTClient client : clients) {
            if (!client.hasPlayer() || client.getRoomPlayer() == null || client.getConnection() == null) {
                throw new IllegalArgumentException("Relay clients must have a room player");
            }

            int playerId = Math.toIntExact(client.getPlayer().getId());
            short playerPosition = client.getRoomPlayer().getPosition();
            List<Short> actorPositions = new ArrayList<>();
            if (playerPosition >= 0 && playerPosition < 4) {
                actorPositions.add(playerPosition);
            }
            GameplayActor ownedPet = gameSession.getOwnedPetSeat(client.getPlayer().getId());
            if (ownedPet != null) {
                actorPositions.add(ownedPet.position());
            }
            actorPositions = actorPositions.stream().distinct().sorted().toList();
            if (actorPositionsByPlayerId.putIfAbsent(playerId, actorPositions) != null) {
                throw new IllegalArgumentException("Relay session contains duplicate player IDs");
            }
            InetSocketAddress remoteAddress = client.getConnection().getRemoteAddressTCP();
            if (remoteAddress == null || remoteAddress.getAddress() == null) {
                throw new IllegalArgumentException("Relay client has no remote address");
            }
            playerAddresses.put(playerId, remoteAddress.getAddress().getHostAddress());
            battlemonControllerByPlayerId.put(playerId, ownsBattlemonController(client));
        }

        long distinctActorCount = actorPositionsByPlayerId.values().stream()
                .flatMap(List::stream)
                .distinct()
                .count();
        long actorCount = actorPositionsByPlayerId.values().stream().mapToLong(List::size).sum();
        if (distinctActorCount != actorCount) {
            throw new IllegalArgumentException("Relay actor ownership overlaps");
        }

        return RelaySessionAuthorizationMessage.builder()
                .gameSessionId(gameSessionId)
                .generation(gameSession.getRelayAuthorizationGeneration())
                .battlemon(gameSession.isDedicatedBattlemonRoom())
                .revoked(false)
                .actorPositionsByPlayerId(actorPositionsByPlayerId)
                .playerAddresses(playerAddresses)
                .battlemonControllerByPlayerId(battlemonControllerByPlayerId)
                .expiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .build();
    }

    static boolean ownsBattlemonController(FTClient client) {
        if (client == null || !client.hasPlayer()) {
            return false;
        }
        PlayerPocketService playerPocketService = ServiceManager.getInstance() == null
                ? null
                : ServiceManager.getInstance().getPlayerPocketService();
        if (playerPocketService == null) {
            return false;
        }
        PlayerPocket controller = playerPocketService.getItemAsPocketByItemIndexAndCategoryAndPocket(
                BattlemonController.SPECIAL_ITEM_INDEX,
                EItemCategory.SPECIAL.getName(),
                client.getPlayer().getPocketId()
        );
        return BattlemonController.isPossessed(controller);
    }

    static void abortStartAndNotifyClients(Room room, Integer gameSessionId, GameSession gameSession,
                                           List<FTClient> clients, FTClient requestingClient) {
        if (!abortStart(room, gameSessionId, gameSession, clients)) {
            return;
        }

        RoomPlayer requestingRoomPlayer = requestingClient.getRoomPlayer();
        List<RoomPlayer> visibleRoomPlayers = requestingRoomPlayer == null ||
                requestingRoomPlayer.getPosition() == MiscConstants.InvisibleGmSlot
                ? room.getRoomPlayerList().stream().toList()
                : room.getRoomPlayerList().stream()
                        .filter(roomPlayer -> roomPlayer.getPosition() != MiscConstants.InvisibleGmSlot)
                        .toList();
        S2CRoomPlayerListInformationPacket roomPlayerInformationPacket =
                new S2CRoomPlayerListInformationPacket(visibleRoomPlayers);
        SMSGCancelStartGame cancelStartGamePacket = SMSGCancelStartGame.builder().result((char) 0).build();
        Packet roomStartGameAck = new Packet(PacketOperations.S2CRoomStartGameAck);
        roomStartGameAck.write((char) 0);
        SMSGUnsetHost unsetHostPacket = SMSGUnsetHost.builder().result((byte) 0).build();
        List<FTClient> roomClients = GameManager.getInstance().getClientsInRoom(room.getRoomId()).stream().toList();

        for (FTClient client : roomClients) {
            if (client.getConnection() == null) {
                continue;
            }
            try {
                client.getConnection().sendTCP(roomPlayerInformationPacket);
                for (RoomPlayer roomPlayer : visibleRoomPlayers) {
                    PetView pet = roomPlayer.getPet();
                    if (pet != null) {
                        byte slot = roomPlayer.getPosition() == 0 ? (byte) 0 : (byte) 1;
                        client.getConnection().sendTCP(new S2CPetRequestRoomAnswerPacket(
                                S2CPetRequestRoomAnswerPacket.SUCCESS, true, slot, pet));
                    }
                }
                client.getConnection().sendTCP(cancelStartGamePacket);
                client.getConnection().sendTCP(roomStartGameAck);
                client.getConnection().sendTCP(unsetHostPacket);
            } catch (RuntimeException e) {
                log.warn("Unable to notify a client that startup for room {} was aborted", room.getRoomId(), e);
            }
        }

        if (requestingClient.getConnection() != null) {
            try {
                GameManager.getInstance().updateRoomForAllClientsInMultiplayer(
                        requestingClient.getConnection(), room);
            } catch (RuntimeException e) {
                log.warn("Unable to publish the restored state for room {}", room.getRoomId(), e);
            }
        }
    }

    static boolean abortStart(Room room, Integer gameSessionId, GameSession gameSession, List<FTClient> clients) {
        boolean removed = GameSessionManager.getInstance().removeGameSession(gameSessionId, gameSession);
        GameManager.getInstance().revokeRelaySession(gameSessionId, gameSession);

        gameSession.getCompletionHandled().set(true);
        gameSession.clearCountDownRunnable();
        gameSession.getFireables().forEach(fireable -> fireable.setCancelled(true));
        gameSession.getFireables().clear();
        MatchplayGame game = gameSession.getMatchplayGame();
        if (game != null) {
            game.getScheduledFutures().forEach(future -> future.cancel(false));
            game.getScheduledFutures().clear();
        }
        gameSession.getActors().clear();
        clients.forEach(client -> {
            GameSession activeSession = client.getActiveGameSession();
            if (activeSession == gameSession) {
                client.setActiveGameSession(null);
            }
        });
        if (!removed) {
            return false;
        }
        synchronized (room) {
            room.setStatus(RoomStatus.NotRunning);
            room.getRoomPlayerList().forEach(roomPlayer -> {
                roomPlayer.setReady(false);
                roomPlayer.getConnectedToRelay().set(false);
            });
        }
        return true;
    }
}
