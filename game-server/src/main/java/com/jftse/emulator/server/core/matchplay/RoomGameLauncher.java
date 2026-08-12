package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.constants.MiscConstants;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.ClubMatchRules;
import com.jftse.emulator.server.core.life.room.ClubMatchState;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.core.matchplay.handler.MatchplayBasicModeHandler;
import com.jftse.emulator.server.core.packets.lobby.room.S2CPetRequestRoomAnswerPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomPlayerListInformationPacket;
import com.jftse.emulator.server.core.packets.matchplay.S2CClubMatchGameTimePacket;
import com.jftse.emulator.server.core.packets.matchplay.S2CGameNetworkSettingsPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.gameserver.GameServer;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import com.jftse.server.core.service.AuthenticationService;
import com.jftse.server.core.shared.ServerConfService;
import com.jftse.server.core.shared.packets.matchplay.SMSGCancelStartGame;
import com.jftse.server.core.shared.packets.matchplay.SMSGSetHost;
import com.jftse.server.core.shared.packets.matchplay.SMSGSetHostUnknown;
import com.jftse.server.core.shared.packets.matchplay.SMSGStartGame;
import com.jftse.server.core.shared.packets.matchplay.SMSGUnsetHost;
import com.jftse.server.core.thread.ThreadManager;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Log4j2
public class RoomGameLauncher {
    private final AuthenticationService authenticationService;
    private final ServerConfService serverConfService;

    public RoomGameLauncher() {
        authenticationService = ServiceManager.getInstance().getAuthenticationService();
        serverConfService = GameManager.getInstance().getServerConfService();
    }

    public boolean launchOrdinary(FTConnection connection) {
        Packet roomStartGameAck = startGameAck();
        FTClient client = connection.getClient();
        if (client == null || !client.hasPlayer() || client.getActiveRoom() == null) {
            connection.sendTCP(roomStartGameAck);
            return false;
        }

        Room room = client.getActiveRoom();
        synchronized (room) {
            if (room.getStatus() != RoomStatus.NotRunning) {
                connection.sendTCP(roomStartGameAck);
                return false;
            }
            room.setStatus(RoomStatus.StartingGame);
        }

        launchClaimed(room, client);
        return true;
    }

    void launchClaimed(Room room) {
        launchClaimed(room, null);
    }

    private void launchClaimed(Room room, FTClient preferredClient) {
        boolean clubMatch;
        List<ClubMatchState.Participant> claimedClubParticipants;
        long claimedGeneration;
        synchronized (room) {
            clubMatch = ClubMatchRules.isClubMatch(room);
            claimedClubParticipants = clubMatch
                    ? room.getClubMatchState().getParticipants()
                    : List.of();
            claimedGeneration = clubMatch ? room.getClubMatchState().getGeneration() : 0;
            if (room.getStatus() != RoomStatus.StartingGame || ClubMatchRules.isClubServerRoom(room)
                    && (!clubMatch || !ClubMatchRules.isImplementedWireMode(room.getMode())
                    || !ClubMatchRules.hasValidTeams(room)
                    || !room.getClubMatchState().isStartTriggered()
                    || !room.getClubMatchState().matchesParticipants(clubParticipants(room)))) {
                room.setStatus(RoomStatus.NotRunning);
                if (ClubMatchRules.isClubServerRoom(room)) {
                    room.getClubMatchState().cancelCountdown();
                }
                return;
            }
        }

        List<FTClient> allClientsInRoom = new ArrayList<>(
                GameManager.getInstance().getClientsInRoom(room.getRoomId()));
        List<FTClient> clientsInRoom = selectSessionClients(
                clubMatch, claimedClubParticipants, allClientsInRoom);
        if (clubMatch && clientsInRoom.size() != claimedClubParticipants.size()) {
            resetClaimedLaunch(room);
            return;
        }
        FTClient launchClient = selectLaunchClient(room, clientsInRoom, preferredClient);
        if (launchClient == null) {
            resetClaimedLaunch(room);
            return;
        }

        FTConnection launchConnection = launchClient.getConnection();
        GameServer relayServer = authenticationService.getGameServerByPort(serverConfService.get("RelayPort", Integer.class));
        if (relayServer == null) {
            resetClaimedLaunch(room);
            log.error("Cannot start room {} because no relay server is configured", room.getRoomId());
            return;
        }

        MatchplayGame game = switch (room.getMode()) {
            case GameMode.BASIC -> new MatchplayBasicGame(room.getPlayers());
            case GameMode.BATTLE -> new MatchplayBattleGame(room.getPlayers());
            case GameMode.GUARDIAN -> new MatchplayGuardianGame();
            default -> null;
        };
        if (game == null) {
            resetClaimedLaunch(room);
            log.warn("Cannot start room {} because mode {} is unsupported", room.getRoomId(), room.getMode());
            return;
        }

        GameSession gameSession = new GameSession();
        Integer gameSessionId = GameSessionManager.getInstance().addGameSession(gameSession);
        SMSGUnsetHost unsetHostPacket = SMSGUnsetHost.builder().result((byte) 0).build();
        synchronized (room) {
            try {
                if (clubMatch && (room.getStatus() != RoomStatus.StartingGame
                        || !room.getClubMatchState().ownsGeneration(claimedGeneration)
                        || !room.getClubMatchState().isStartTriggered())) {
                    rollbackLaunch(room, launchClient, clientsInRoom, gameSession, gameSessionId,
                            startGameAck(), unsetHostPacket, true, claimedGeneration);
                    return;
                }
                gameSession.setPlayers(room.getPlayers());
                gameSession.setMatchplayGame(game);

                for (FTClient client : clientsInRoom) {
                    RoomPlayer roomPlayer = client.getRoomPlayer();
                    FTConnection connection = client.getConnection();
                    if (roomPlayer == null || connection == null) {
                        throw new IllegalStateException("Club Match participant disconnected during launch setup");
                    }
                    roomPlayer.getConnectedToRelay().set(false);
                    client.setActiveGameSession(gameSessionId);
                    gameSession.getClients().add(client);
                }

                List<FTClient> shiftedClients = new ArrayList<>(clientsInRoom);
                for (FTClient client : clientsInRoom) {
                    FTConnection connection = client.getConnection();
                    if (connection == null) {
                        throw new IllegalStateException("Club Match participant disconnected during relay setup");
                    }
                    connection.sendTCP(unsetHostPacket);
                    connection.sendTCP(new S2CGameNetworkSettingsPacket(
                            relayServer.getHost(), relayServer.getPort(), gameSessionId, room, shiftedClients));
                    shiftedClients.add(0, shiftedClients.remove(shiftedClients.size() - 1));
                }

                int initialRoomPlayerSize = room.getRoomPlayerList().size();
                ThreadManager.getInstance().schedule(() -> finishLaunchSafely(room, launchClient, launchConnection,
                        clientsInRoom, gameSession, gameSessionId, game, unsetHostPacket, clubMatch,
                        claimedClubParticipants, claimedGeneration, initialRoomPlayerSize),
                        0, TimeUnit.SECONDS);
            } catch (RuntimeException exception) {
                log.warn("Rolling back room {} after launch setup failed", room.getRoomId(), exception);
                rollbackLaunch(room, launchClient, clientsInRoom, gameSession, gameSessionId,
                        startGameAck(), unsetHostPacket, clubMatch, claimedGeneration);
            }
        }
    }

    private void finishLaunchSafely(Room room, FTClient launchClient, FTConnection launchConnection,
                                    List<FTClient> clientsInRoom, GameSession gameSession,
                                    int gameSessionId, MatchplayGame game,
                                    SMSGUnsetHost unsetHostPacket, boolean clubMatch,
                                    List<ClubMatchState.Participant> claimedClubParticipants,
                                    long claimedGeneration, int initialRoomPlayerSize) {
        try {
            finishLaunch(room, launchClient, launchConnection, clientsInRoom, gameSession,
                    gameSessionId, game, unsetHostPacket, clubMatch, claimedClubParticipants,
                    claimedGeneration, initialRoomPlayerSize);
        } catch (RuntimeException exception) {
            log.warn("Rolling back room {} after asynchronous launch failed", room.getRoomId(), exception);
            rollbackLaunch(room, launchClient, clientsInRoom, gameSession, gameSessionId,
                    startGameAck(), unsetHostPacket, clubMatch, claimedGeneration);
        }
    }

    private void finishLaunch(Room room, FTClient launchClient, FTConnection launchConnection,
                              List<FTClient> clientsInRoom, GameSession gameSession,
                              int gameSessionId, MatchplayGame game,
                              SMSGUnsetHost unsetHostPacket, boolean clubMatch,
                              List<ClubMatchState.Participant> claimedClubParticipants,
                              long claimedGeneration, int initialRoomPlayerSize) {
        Packet roomStartGameAck = startGameAck();
        long relayDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(
                Math.max(1, Integer.getInteger("RelayConnectionTimeoutSeconds", 30)));
        while (true) {
            boolean launchReady = false;
            boolean rollback = false;
            synchronized (room) {
                if (clubMatch && room.getClubMatchState().isTerminal()
                        && room.getClubMatchState().ownsGameSession(gameSessionId)) {
                    return;
                }
                boolean allReady = clubMatch
                        ? room.getRoomPlayerList().stream()
                        .filter(player -> player.getPosition() >= 0
                                && player.getPosition() < room.getPlayers())
                        .allMatch(RoomPlayer::isReady)
                        : room.getRoomPlayerList().stream()
                        .filter(player -> !player.isMaster() && player.getPosition() < 4)
                        .allMatch(RoomPlayer::isReady);
                boolean roomPlayersChanged = clubMatch
                        ? !claimedClubParticipants.equals(clubParticipants(room))
                        || !room.getClubMatchState().matchesParticipants(claimedClubParticipants)
                        || !room.getClubMatchState().ownsGeneration(claimedGeneration)
                        || !ClubMatchRules.hasValidTeams(room)
                        : initialRoomPlayerSize != room.getRoomPlayerList().size();
                int status = room.getStatus();
                rollback = !allReady || roomPlayersChanged || status == RoomStatus.StartCancelled
                        || status == RoomStatus.RelayConnectionFailed
                        || clubMatch && System.nanoTime() >= relayDeadline;
                if (!rollback && status == RoomStatus.RelayConnectionSuccess) {
                    if (clubMatch) {
                        int gameTimeSeconds = Math.multiplyExact(room.getClubMatchMaxPlayTimeMinutes(), 60);
                        launchReady = room.getClubMatchState().markGameStarted(gameSessionId, Instant.now(),
                                Duration.ofSeconds(gameTimeSeconds), claimedGeneration);
                        rollback = !launchReady;
                    } else {
                        launchReady = true;
                    }
                    if (launchReady) {
                        room.setStatus(RoomStatus.InitializingGame);
                    }
                }
            }

            if (rollback) {
                rollbackLaunch(room, launchClient, clientsInRoom, gameSession, gameSessionId,
                        roomStartGameAck, unsetHostPacket, clubMatch, claimedGeneration);
                return;
            }
            if (launchReady) {
                break;
            }

            try {
                TimeUnit.MILLISECONDS.sleep(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for relay connection success", exception);
                rollbackLaunch(room, launchClient, clientsInRoom, gameSession, gameSessionId,
                        roomStartGameAck, unsetHostPacket, clubMatch, claimedGeneration);
                return;
            }
        }

        RoomPlayer playerInSlot0 = room.getRoomPlayerList().stream()
                .filter(player -> player.getPosition() == 0)
                .findFirst()
                .orElse(null);
        FTClient hostClient = clientsInRoom.stream()
                .filter(client -> playerInSlot0 != null && client.hasPlayer()
                        && client.getPlayer().getId() == playerInSlot0.getPlayerId())
                .findFirst()
                .orElse(launchClient);
        synchronized (room) {
            if (clubMatch && (room.getStatus() != RoomStatus.InitializingGame
                    || !room.getClubMatchState().ownsGameSession(gameSessionId)
                    || room.getClubMatchState().isTerminal())) {
                return;
            }
            if (hostClient.getConnection() == null) {
                rollbackLaunch(room, launchClient, clientsInRoom, gameSession, gameSessionId,
                        roomStartGameAck, unsetHostPacket, clubMatch, claimedGeneration);
                return;
            }

            hostClient.getConnection().sendTCP(SMSGSetHost.builder().result((byte) 1).build());
            hostClient.getConnection().sendTCP(SMSGSetHostUnknown.builder().build());
            game.getHandleable().onPrepare(launchClient);

            GameManager.getInstance().sendPacketToAllClientsInSameGameSession(
                    SMSGStartGame.builder().result((char) 0).build(), launchConnection);
            if (clubMatch) {
                int gameTimeSeconds = Math.multiplyExact(room.getClubMatchMaxPlayTimeMinutes(), 60);
                GameManager.getInstance().sendPacketToAllClientsInSameGameSession(
                        new S2CClubMatchGameTimePacket(gameTimeSeconds), launchConnection);
                scheduleClubMatchExpiry(room, gameSession, gameSessionId);
            }
        }
    }

    private void scheduleClubMatchExpiry(Room room, GameSession gameSession, int gameSessionId) {
        long delayMillis = room.getClubMatchState().millisUntilExpiry(gameSessionId, Instant.now());
        if (delayMillis < 0) {
            return;
        }
        ThreadManager.getInstance().schedule(
                () -> expireClubMatch(room, gameSession, gameSessionId),
                delayMillis, TimeUnit.MILLISECONDS);
    }

    private void expireClubMatch(Room room, GameSession gameSession, int gameSessionId) {
        long remainingMillis = room.getClubMatchState().millisUntilExpiry(gameSessionId, Instant.now());
        if (remainingMillis > 0) {
            scheduleClubMatchExpiry(room, gameSession, gameSessionId);
            return;
        }
        if (gameSession.getMatchplayGame() instanceof MatchplayBasicGame
                && gameSession.getMatchplayGame().getHandleable()
                instanceof MatchplayBasicModeHandler handler) {
            handler.onClubMatchTimerExpired(gameSession, gameSessionId, room);
        }
    }

    private void rollbackLaunch(Room room, FTClient launchClient, List<FTClient> clientsInRoom,
                                GameSession gameSession, int gameSessionId, Packet roomStartGameAck,
                                SMSGUnsetHost unsetHostPacket, boolean clubMatch,
                                long claimedGeneration) {
        boolean ownsLifecycle;
        boolean completesCancelledLifecycle;
        long cleanupGeneration = claimedGeneration;
        synchronized (room) {
            ownsLifecycle = !clubMatch || room.getClubMatchState().ownsGeneration(claimedGeneration);
            completesCancelledLifecycle = clubMatch && !ownsLifecycle
                    && room.getStatus() == RoomStatus.StartCancelled
                    && room.getClubMatchState().ownsGeneration(claimedGeneration + 1);
            if (ownsLifecycle) {
                room.setStatus(RoomStatus.StartCancelled);
            }
            if (clubMatch && ownsLifecycle) {
                room.getClubMatchState().cancelCountdown();
                cleanupGeneration = room.getClubMatchState().getGeneration();
            } else if (completesCancelledLifecycle) {
                cleanupGeneration = room.getClubMatchState().getGeneration();
            }
        }
        boolean cleanupLifecycle = ownsLifecycle || completesCancelledLifecycle;
        if (cleanupLifecycle) {
            room.getRoomPlayerList().forEach(player -> {
                player.setReady(false);
                player.getConnectedToRelay().set(false);
            });
        }
        clientsInRoom.forEach(client -> client.clearActiveGameSession(gameSessionId));
        GameSessionManager.getInstance().removeGameSession(gameSessionId, gameSession);

        if (!cleanupLifecycle) {
            return;
        }

        RoomPlayer launchRoomPlayer = room.getRoomPlayerList().stream()
                .filter(player -> launchClient.hasPlayer()
                        && player.getPlayerId() == launchClient.getPlayer().getId())
                .findFirst()
                .orElse(null);
        List<RoomPlayer> visiblePlayers = launchRoomPlayer == null
                || launchRoomPlayer.getPosition() == MiscConstants.InvisibleGmSlot
                ? room.getRoomPlayerList().stream().toList()
                : room.getRoomPlayerList().stream()
                .filter(player -> player.getPosition() != MiscConstants.InvisibleGmSlot)
                .toList();
        S2CRoomPlayerListInformationPacket playerListPacket =
                new S2CRoomPlayerListInformationPacket(visiblePlayers);
        GameManager.getInstance().getClientsInRoom(room.getRoomId()).forEach(client -> {
            if (client.getConnection() != null) {
                client.getConnection().sendTCP(playerListPacket);
                for (RoomPlayer player : visiblePlayers) {
                    PetView pet = player.getPet();
                    if (pet != null) {
                        byte slot = player.getPosition() == 0 ? (byte) 0 : (byte) 1;
                        client.getConnection().sendTCP(new S2CPetRequestRoomAnswerPacket(
                                S2CPetRequestRoomAnswerPacket.SUCCESS, true, slot, pet));
                    }
                }
            }
        });

        if (launchClient.getConnection() != null) {
            GameManager.getInstance().updateRoomForAllClientsInMultiplayer(launchClient.getConnection(), room);
        }
        SMSGCancelStartGame cancelPacket = SMSGCancelStartGame.builder().result((char) 0).build();
        GameManager.getInstance().getClientsInRoom(room.getRoomId()).forEach(client -> {
            if (client.getConnection() != null) {
                client.getConnection().sendTCP(cancelPacket);
                client.getConnection().sendTCP(roomStartGameAck);
                client.getConnection().sendTCP(unsetHostPacket);
            }
        });
        synchronized (room) {
            if (room.getStatus() == RoomStatus.StartCancelled
                    && (!clubMatch || room.getClubMatchState().ownsGeneration(cleanupGeneration))) {
                room.setStatus(RoomStatus.NotRunning);
            }
        }
    }

    private FTClient selectLaunchClient(Room room, List<FTClient> clientsInRoom, FTClient preferredClient) {
        if (preferredClient != null && preferredClient.getConnection() != null
                && preferredClient.getActiveRoom() == room) {
            return preferredClient;
        }
        return clientsInRoom.stream()
                .filter(client -> client.getConnection() != null && client.getRoomPlayer() != null)
                .filter(client -> client.getRoomPlayer().getPosition() >= 0
                        && client.getRoomPlayer().getPosition() < room.getPlayers())
                .min(Comparator.comparingInt(client -> client.getRoomPlayer().getPosition()))
                .orElse(null);
    }

    static List<FTClient> selectSessionClients(boolean clubMatch,
                                               List<ClubMatchState.Participant> participants,
                                               List<FTClient> clientsInRoom) {
        if (!clubMatch) {
            return clientsInRoom;
        }
        return participants.stream()
                .map(participant -> clientForParticipant(clientsInRoom, participant))
                .filter(client -> client != null)
                .toList();
    }

    private static FTClient clientForParticipant(List<FTClient> clients,
                                                  ClubMatchState.Participant participant) {
        return clients.stream()
                .filter(client -> client.hasPlayer() && client.getConnection() != null
                        && client.getRoomPlayer() != null)
                .filter(client -> client.getPlayer().getId() == participant.playerId()
                        && client.getRoomPlayer().getPosition() == participant.position())
                .filter(client -> client.getRoomPlayer().getGuild() != null
                        && participant.guildId() != null
                        && client.getRoomPlayer().getGuild().id() == participant.guildId())
                .findFirst()
                .orElse(null);
    }

    private void resetClaimedLaunch(Room room) {
        synchronized (room) {
            room.setStatus(RoomStatus.NotRunning);
            if (ClubMatchRules.isClubMatch(room)) {
                room.getClubMatchState().cancelCountdown();
            }
        }
    }

    private Packet startGameAck() {
        Packet packet = new Packet(PacketOperations.S2CRoomStartGameAck);
        packet.write((char) 0);
        return packet;
    }

    private List<ClubMatchState.Participant> clubParticipants(Room room) {
        return room.getRoomPlayerList().stream()
                .filter(player -> player.getPosition() >= 0
                        && player.getPosition() < room.getPlayers())
                .sorted(Comparator.comparingInt(RoomPlayer::getPosition))
                .map(player -> new ClubMatchState.Participant(player.getPlayerId(), player.getPosition(),
                        player.getGuild() == null ? null : player.getGuild().id()))
                .toList();
    }
}
