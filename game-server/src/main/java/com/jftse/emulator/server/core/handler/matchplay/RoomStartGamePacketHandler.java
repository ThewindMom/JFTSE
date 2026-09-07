package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.constants.MiscConstants;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.GameplayActor;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.packets.lobby.room.S2CPetRequestRoomAnswerPacket;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.matchplay.MatchplayGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomPlayerListInformationPacket;
import com.jftse.emulator.server.core.packets.matchplay.S2CGameNetworkSettingsPacket;
import com.jftse.emulator.server.core.tournament.TournamentRoomCoordinator;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.gameserver.GameServer;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.item.BattlemonController;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.service.AuthenticationService;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.shared.ServerConfService;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import com.jftse.server.core.shared.packets.matchplay.*;
import com.jftse.server.core.thread.ThreadManager;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Log4j2
@PacketId(CMSGStartGame.PACKET_ID)
public class RoomStartGamePacketHandler implements PacketHandler<FTConnection, CMSGStartGame> {
    private static final long RELAY_CONNECTION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);
    private final AuthenticationService authenticationService;
    private final ServerConfService serverConfService;

    public RoomStartGamePacketHandler() {
        this.authenticationService = ServiceManager.getInstance().getAuthenticationService();
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

        TournamentRoomCoordinator tournamentCoordinator = TournamentRoomCoordinator.getInstance();
        Map<Long, Short> tournamentParticipantPositions;
        synchronized (room) {
            if (room.getStatus() != RoomStatus.NotRunning
                    || (room.isTournamentRoom()
                    && !tournamentCoordinator.canStart(room, ftClient.getRoomPlayer()))) {
                connection.sendTCP(roomStartGameAck);
                return;
            }

            tournamentParticipantPositions = room.isTournamentRoom()
                    ? room.getRoomPlayerList().stream()
                            .filter(player -> player.getPosition() < 4)
                            .collect(Collectors.toUnmodifiableMap(
                                    RoomPlayer::getPlayerId,
                                    RoomPlayer::getPosition))
                    : Map.of();
            room.setStatus(RoomStatus.StartingGame);
        }

        GameServer relayServer = authenticationService.getGameServerByPort(this.serverConfService.get("RelayPort", Integer.class));

        List<FTClient> clientsInRoom = new ArrayList<>(GameManager.getInstance().getClientsInRoom(room.getRoomId()));

        GameSession gameSession = new GameSession(room.getRoomType() == RoomType.BATTLEMON);
        Integer gameSessionId = GameSessionManager.getInstance().addGameSession(gameSession);
        gameSession.setTournamentMatchId(room.getTournamentMatchId());
        gameSession.setTournamentParticipantPositions(tournamentParticipantPositions);

        gameSession.setPlayers(room.getPlayers());
        MatchplayGame game;
        switch (room.getMode()) {
            case GameMode.BASIC -> game = new MatchplayBasicGame(room.getPlayers());
            case GameMode.BATTLE -> game = new MatchplayBattleGame(room.getPlayers());
            case GameMode.GUARDIAN -> game = new MatchplayGuardianGame();
            default -> throw new IllegalStateException("room mode not supported: " + room.getMode());
        }
        gameSession.setMatchplayGame(game);

        clientsInRoom.forEach(c -> {
            c.setActiveGameSession(gameSessionId);
            gameSession.getClients().add(c);
        });

        if (room.isTournamentRoom() && !tournamentCoordinator.activate(room, gameSessionId)) {
            GameSessionManager.getInstance().discardGameSession(gameSessionId, gameSession);
            synchronized (room) {
                room.setStatus(RoomStatus.NotRunning);
            }
            connection.sendTCP(roomStartGameAck);
            return;
        }

        SMSGUnsetHost unsetHostPacket = SMSGUnsetHost.builder().result((byte) 0).build();
        List<FTClient> clientInRoomLeftShiftList = new ArrayList<>(clientsInRoom);
        clientsInRoom.forEach(c -> {
            c.getConnection().sendTCP(unsetHostPacket);

            S2CGameNetworkSettingsPacket gameNetworkSettings = new S2CGameNetworkSettingsPacket(relayServer.getHost(), relayServer.getPort(), gameSessionId, room, clientInRoomLeftShiftList);
            c.getConnection().sendTCP(gameNetworkSettings);

            // shift list to the left, so every client has his player id in the first place when doing session register
            clientInRoomLeftShiftList.add(0, clientInRoomLeftShiftList.remove(clientInRoomLeftShiftList.size() - 1));
        });

        int initialRoomPlayerSize = room.getRoomPlayerList().size();
        Set<Long> initialTournamentParticipantIds = tournamentParticipantPositions.keySet();

        ThreadManager.getInstance().schedule(() -> {
            Room threadRoom = room;
            long relayDeadline = System.nanoTime() + RELAY_CONNECTION_TIMEOUT_NANOS;
            while (threadRoom.getStatus() != RoomStatus.RelayConnectionSuccess
                    || (threadRoom.isTournamentRoom()
                    && !tournamentCoordinator.canContinueStart(threadRoom, initialTournamentParticipantIds))) {
                boolean allReady = threadRoom.isTournamentRoom()
                        ? tournamentCoordinator.canContinueStart(threadRoom, initialTournamentParticipantIds)
                        : threadRoom.getRoomPlayerList().stream()
                            .filter(rp -> !rp.isMaster())
                            .collect(Collectors.toList())
                            .stream()
                            .filter(rp -> rp.getPosition() < 4)
                            .allMatch(RoomPlayer::isReady);

                boolean roomPlayerSizeChanged = !threadRoom.isTournamentRoom()
                        && initialRoomPlayerSize != threadRoom.getRoomPlayerList().size();
                boolean relayTimedOut = System.nanoTime() >= relayDeadline;

                synchronized (threadRoom) {
                    if (!allReady || roomPlayerSizeChanged || relayTimedOut
                            || threadRoom.getStatus() == RoomStatus.StartCancelled
                            || threadRoom.getStatus() == RoomStatus.RelayConnectionFailed) {
                            threadRoom.setStatus(RoomStatus.NotRunning);
                            SMSGCancelStartGame cancelStartGamePacket = SMSGCancelStartGame.builder().result((char) 0).build();

                            threadRoom.getRoomPlayerList().forEach(rp -> {
                                rp.setReady(false);
                                rp.getConnectedToRelay().set(false);
                            });
                            GameSessionManager.getInstance().discardGameSession(gameSessionId, gameSession);
                            tournamentCoordinator.deactivate(threadRoom, gameSessionId);

                            RoomPlayer threadRoomPlayer = threadRoom.getRoomPlayerList().stream()
                                    .filter(x -> x.getPlayerId() == ftClient.getPlayer().getId())
                                    .findFirst()
                                    .orElse(null);

                            List<RoomPlayer> filteredRoomPlayerList = threadRoomPlayer == null || threadRoomPlayer.getPosition() == MiscConstants.InvisibleGmSlot
                                    ? threadRoom.getRoomPlayerList().stream().toList()
                                    : threadRoom.getRoomPlayerList().stream()
                                            .filter(x -> x.getPosition() != MiscConstants.InvisibleGmSlot)
                                            .toList();
                            S2CRoomPlayerListInformationPacket roomPlayerInformationPacket = new S2CRoomPlayerListInformationPacket(filteredRoomPlayerList);
                            GameManager.getInstance().getClientsInRoom(threadRoom.getRoomId()).forEach(c -> {
                                if (c.getConnection() != null) {
                                    c.getConnection().sendTCP(roomPlayerInformationPacket);
                                }
                            });
                            GameManager.getInstance().updateRoomForAllClientsInMultiplayer(ftClient.getConnection(), threadRoom);
                            GameManager.getInstance().getClientsInRoom(threadRoom.getRoomId()).forEach(c -> {
                                if (c.getConnection() != null) {
                                    c.getConnection().sendTCP(cancelStartGamePacket);
                                    c.getConnection().sendTCP(roomStartGameAck);
                                    c.getConnection().sendTCP(unsetHostPacket);
                                }
                            });
                        return;
                    }
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    synchronized (threadRoom) {
                        threadRoom.setStatus(RoomStatus.RelayConnectionFailed);
                    }
                }
            }

            RoomPlayer playerInSlot0 = room.getRoomPlayerList().stream()
                    .filter(x -> x.getPosition() == 0)
                    .findFirst().orElse(null);
            FTClient clientToHostGame = GameManager.getInstance().getClientsInRoom(room.getRoomId()).stream()
                    .filter(x -> playerInSlot0 != null && x.hasPlayer() && x.getPlayer().getId() == playerInSlot0.getPlayerId())
                    .findFirst()
                    .orElse(connection.getClient());
            SMSGSetHost setHostPacket = SMSGSetHost.builder().result((byte) 1).build();
            clientToHostGame.getConnection().sendTCP(setHostPacket);

            SMSGSetHostUnknown setHostUnknownPacket = SMSGSetHostUnknown.builder().build();
            clientToHostGame.getConnection().sendTCP(setHostUnknownPacket);

            game.getHandleable().onPrepare(ftClient);

            SMSGStartGame startGamePacket = SMSGStartGame.builder().result((char) 0).build();

            synchronized (room) {
                room.setStatus(RoomStatus.InitializingGame);
            }
            GameManager.getInstance().sendPacketToAllClientsInSameGameSession(startGamePacket, ftClient.getConnection());
        }, 0, TimeUnit.SECONDS);
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

    static List<FTClient> ordinaryRelayEndpointRoster(List<FTClient> clients, int recipientIndex) {
        List<FTClient> roster = new ArrayList<>(clients.size());
        for (int offset = 0; offset < clients.size(); offset++) {
            roster.add(clients.get(Math.floorMod(offset - recipientIndex, clients.size())));
        }
        return roster;
    }

    static RelayStartupPollResult pollRelayStartup(Room room, int initialRoomPlayerSize, FTClient client,
                                                   Integer gameSessionId, GameSession gameSession) {
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

            return !allReady || roomPlayerSizeChanged || sessionChanged || roomChanged ||
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
                .battlemon(gameSession.isDedicatedBattlemonRoom())
                .ownedPetSession(gameSession.hasOwnedPetSeats())
                .remove(false)
                .actorPositionsByPlayerId(actorPositionsByPlayerId)
                .battlemonControllerByPlayerId(battlemonControllerByPlayerId)
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
        if (removed) {
            GameManager.getInstance().removeRelayActorPolicy(gameSessionId, gameSession);
        }

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
        if (removed) {
            for (FTClient client : clients) {
                if (Objects.equals(client.getGameSessionId(), gameSessionId)) {
                    client.setActiveGameSession(null);
                }
            }
        }
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
