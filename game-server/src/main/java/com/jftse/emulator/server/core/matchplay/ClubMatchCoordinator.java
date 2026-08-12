package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.ClubMatchRules;
import com.jftse.emulator.server.core.life.room.ClubMatchState;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.packets.matchplay.S2CClubMatchReadyPacket;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayBackToRoom;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomChangeReady;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ClubMatchCoordinator {
    private static final ClubMatchCoordinator INSTANCE = new ClubMatchCoordinator(
            Clock.systemUTC(),
            room -> new RoomGameLauncher().launchClaimed(room),
            room -> GameManager.getInstance().getRooms().contains(room),
            room -> GameManager.getInstance().getClientsInRoom(room.getRoomId()));

    private final Clock clock;
    private final Launcher launcher;
    private final Predicate<Room> roomExists;
    private final Function<Room, List<FTClient>> clientProvider;

    ClubMatchCoordinator(Clock clock, Launcher launcher,
                         Predicate<Room> roomExists,
                         Function<Room, List<FTClient>> clientProvider) {
        this.clock = clock;
        this.launcher = launcher;
        this.roomExists = roomExists;
        this.clientProvider = clientProvider;
    }

    public static ClubMatchCoordinator getInstance() {
        return INSTANCE;
    }

    public void updateReady(FTConnection connection, boolean ready, Duration countdownDuration) {
        FTClient client = connection.getClient();
        if (client == null) {
            return;
        }
        Room room = client.getActiveRoom();
        RoomPlayer roomPlayer = client.getRoomPlayer();
        if (!client.hasPlayer() || !ClubMatchRules.isClubMatch(room) || roomPlayer == null) {
            return;
        }

        synchronized (room) {
            if (room.getStatus() != RoomStatus.NotRunning
                    || roomPlayer.getPosition() < 0
                    || roomPlayer.getPosition() >= room.getPlayers()) {
                connection.sendTCP(S2CClubMatchReadyPacket.cancelled());
                return;
            }

            roomPlayer.setReady(ready);
            broadcastRoomReady(room, roomPlayer);
            ClubMatchState state = room.getClubMatchState();
            if (!ready) {
                state.cancelCountdown();
                broadcastCountdownCancelled(room);
                return;
            }

            boolean allReady = isLaunchable(room);
            if (!allReady) {
                connection.sendTCP(S2CClubMatchReadyPacket.cancelled());
                return;
            }

            Instant now = clock.instant();
            if (state.startCountdown(now, countdownDuration, roomPlayer.getPlayerId(),
                    participants(room))) {
                Countdown countdown = new Countdown(state.getCountdownStartedAt(),
                        state.getCountdownEndsAt(), state.getDesignatedPlayerId());
                broadcastCountdown(room, countdown, now);
            }
        }
    }

    public boolean startFromClient(Room room, long playerId) {
        if (room == null) {
            return false;
        }
        boolean claimed;
        boolean cancelled = false;
        synchronized (room) {
            List<ClubMatchState.Participant> participants = participants(room);
            claimed = roomExists.test(room)
                    && room.getStatus() == RoomStatus.NotRunning
                    && isLaunchable(room)
                    && room.getClubMatchState().tryStart(playerId, clock.instant(), participants);
            if (claimed) {
                room.setStatus(RoomStatus.StartingGame);
            } else if (room.getClubMatchState().isCountdownActive()
                    && !room.getClubMatchState().matchesParticipants(participants)) {
                cancelled = room.getClubMatchState().cancelCountdown();
            }
        }
        if (cancelled) {
            broadcastCountdownCancelled(room);
        }
        if (claimed) {
            launcher.launch(room);
        }
        return claimed;
    }

    public boolean cancelForCompositionChange(Room room) {
        if (room == null || !ClubMatchRules.isClubMatch(room)) {
            return false;
        }
        boolean cancelled;
        synchronized (room) {
            ClubMatchState state = room.getClubMatchState();
            if (state.isTerminal()) {
                return room.getStatus() == RoomStatus.NotRunning;
            }
            if (state.hasGameSession()) {
                return false;
            }
            cancelled = state.cancelCountdown();
            if (cancelled && room.getStatus() != RoomStatus.NotRunning) {
                room.setStatus(RoomStatus.StartCancelled);
            }
        }
        if (cancelled) {
            broadcastCountdownCancelled(room);
        }
        return true;
    }

    public boolean abortGame(Room room, GameSession gameSession, int gameSessionId) {
        if (room == null || gameSession == null || !ClubMatchRules.isClubMatch(room)) {
            return false;
        }
        synchronized (room) {
            if (!room.getClubMatchState().tryAbort(gameSessionId)) {
                return false;
            }
            room.setStatus(RoomStatus.StartCancelled);
            gameSession.getFireables().forEach(fireable -> fireable.setCancelled(true));
            gameSession.getFireables().clear();
            if (gameSession.getMatchplayGame() != null) {
                gameSession.getMatchplayGame().getScheduledFutures().forEach(future -> future.cancel(false));
                gameSession.getMatchplayGame().getScheduledFutures().clear();
            }
            gameSession.getClients().forEach(client -> {
                RoomPlayer roomPlayer = client.getRoomPlayer();
                if (roomPlayer != null) {
                    roomPlayer.setReady(false);
                    roomPlayer.getConnectedToRelay().set(false);
                }
                if (client.getConnection() != null) {
                    client.getConnection().sendTCP(new S2CMatchplayBackToRoom());
                }
                client.clearActiveGameSession(gameSessionId);
            });
            GameManager.getInstance().getMatchRallyStatsConsumer().clearSession(gameSessionId);
            GameSessionManager.getInstance().removeGameSession(gameSessionId, gameSession);
            if (room.getClubMatchState().ownsGameSession(gameSessionId)) {
                room.setStatus(RoomStatus.NotRunning);
            }
            return true;
        }
    }

    private boolean isLaunchable(Room room) {
        return ClubMatchRules.isClubMatch(room)
                && ClubMatchRules.isImplementedWireMode(room.getMode())
                && ClubMatchRules.hasValidTeams(room)
                && room.getRoomPlayerList().stream()
                .filter(player -> player.getPosition() >= 0 && player.getPosition() < room.getPlayers())
                .allMatch(RoomPlayer::isReady);
    }

    private List<ClubMatchState.Participant> participants(Room room) {
        return room.getRoomPlayerList().stream()
                .filter(player -> player.getPosition() >= 0 && player.getPosition() < room.getPlayers())
                .sorted(Comparator.comparingInt(RoomPlayer::getPosition))
                .map(player -> new ClubMatchState.Participant(player.getPlayerId(), player.getPosition(),
                        player.getGuild() == null ? null : player.getGuild().id()))
                .toList();
    }

    private void broadcastRoomReady(Room room, RoomPlayer roomPlayer) {
        SMSGRoomChangeReady packet = SMSGRoomChangeReady.builder()
                .position(roomPlayer.getPosition())
                .ready(roomPlayer.isReady())
                .build();
        clientsInRoom(room).forEach(client -> {
            if (client.getConnection() != null) {
                client.getConnection().sendTCP(packet);
            }
        });
    }

    private void broadcastCountdown(Room room, Countdown countdown, Instant currentTime) {
        clientsInRoom(room).forEach(client -> {
            if (client.getConnection() == null) {
                return;
            }
            RoomPlayer target = client.getRoomPlayer();
            boolean autoStart = target != null && target.getPlayerId() == countdown.designatedPlayerId();
            client.getConnection().sendTCP(S2CClubMatchReadyPacket.countdown(autoStart,
                    countdown.startedAt(), countdown.endsAt(), currentTime));
        });
    }

    private void broadcastCountdownCancelled(Room room) {
        S2CClubMatchReadyPacket packet = S2CClubMatchReadyPacket.cancelled();
        clientsInRoom(room).forEach(client -> {
            if (client.getConnection() != null) {
                client.getConnection().sendTCP(packet);
            }
        });
    }

    private List<FTClient> clientsInRoom(Room room) {
        List<FTClient> clients = clientProvider.apply(room);
        return clients == null ? List.of() : clients;
    }

    private record Countdown(Instant startedAt, Instant endsAt, long designatedPlayerId) {
    }

    @FunctionalInterface
    interface Launcher {
        void launch(Room room);
    }
}
