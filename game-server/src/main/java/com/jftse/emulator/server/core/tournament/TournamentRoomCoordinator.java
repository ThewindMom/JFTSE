package com.jftse.emulator.server.core.tournament;

import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.tournament.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class TournamentRoomCoordinator {
    private static final Pattern ROOM_REQUEST = Pattern.compile("T#([1-9][0-9]*)", Pattern.CASE_INSENSITIVE);
    private static TournamentRoomCoordinator instance;

    private final TournamentService tournamentService;

    @PostConstruct
    public void init() {
        instance = this;
    }

    public static TournamentRoomCoordinator getInstance() {
        return instance;
    }

    public boolean isTournamentRoomRequest(String roomName) {
        return roomName != null && ROOM_REQUEST.matcher(roomName.trim()).matches();
    }

    public Optional<TournamentService.AssignedMatch> requestedMatch(String roomName, long playerId) {
        if (roomName == null) {
            return Optional.empty();
        }
        Matcher matcher = ROOM_REQUEST.matcher(roomName.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int tournamentId;
        try {
            tournamentId = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
        return tournamentService.assignedMatch(tournamentId, playerId);
    }

    public void configureRoom(Room room, TournamentService.AssignedMatch match, int hostLevel) {
        room.setTournamentMatchId(match.matchId());
        room.setTournamentSpectatorsAllowed(true);
        room.setRoomName("Tournament " + match.tournamentId() + "-" + (match.stage() == 0 ? "Q" : "F")
                + (match.round() + 1) + "-" + (match.slot() + 1));
        room.setRoomType((byte) RoomType.MATCH);
        room.setMode((byte) GameMode.BASIC);
        room.setRule((byte) 0);
        room.setPlayers((byte) 2);
        room.setPrivate(false);
        room.setPassword(null);
        room.setSkillFree(false);
        room.setQuickSlot(false);
        room.setAllowBattlemon((byte) 0);
        room.setLevel((byte) hostLevel);
        room.setLevelRange((byte) 60);
        room.setBettingType((char) 0);
        room.setBettingAmount(0);
        room.setBall(0);
        room.setMap((byte) 0);
    }

    public boolean bindRoom(Room room, TournamentService.AssignedMatch match, long playerId) {
        return room.getTournamentMatchId() != null
                && room.getTournamentMatchId() == match.matchId()
                && tournamentService.bindRoom(match.matchId(), room.getRoomId(), playerId);
    }

    public int joinPosition(Room room, long playerId) {
        Optional<TournamentService.AssignedMatch> assigned = tournamentService.matchForRoom(room.getRoomId());
        if (assigned.isEmpty() || room.getTournamentMatchId() == null
                || room.getTournamentMatchId() != assigned.get().matchId()) {
            return -1;
        }
        synchronized (room) {
            if (room.getRoomPlayerList().stream().anyMatch(player -> player.getPlayerId() == playerId)) {
                return -1;
            }

            int position;
            if (assigned.get().contains(playerId)) {
                position = firstFree(room, 0, 2);
            } else if (room.isTournamentSpectatorsAllowed()
                    && activeTournamentPlayerIds(room).equals(
                            Set.of(assigned.get().playerOneId(), assigned.get().playerTwoId()))) {
                position = firstFree(room, 5, 9);
            } else {
                position = -1;
            }
            if (position >= 0) {
                room.getPositions().set(position, RoomPositionState.InUse);
            }
            return position;
        }
    }

    public boolean shouldBecomeMaster(Room room, long playerId) {
        if (room.getRoomPlayerList().stream().anyMatch(RoomPlayer::isMaster)) {
            return false;
        }
        return tournamentService.matchForRoom(room.getRoomId())
                .map(match -> match.contains(playerId))
                .orElse(false);
    }

    public boolean canStart(Room room, RoomPlayer requester) {
        if (requester == null || !requester.isMaster()) {
            return false;
        }
        Optional<TournamentService.AssignedMatch> assigned = tournamentService.matchForRoom(room.getRoomId());
        if (assigned.isEmpty() || !assigned.get().contains(requester.getPlayerId())) {
            return false;
        }
        List<RoomPlayer> participants = room.getRoomPlayerList().stream()
                .filter(player -> player.getPosition() < 4)
                .toList();
        Set<Long> activePlayers = participants.stream().map(RoomPlayer::getPlayerId).collect(Collectors.toSet());
        return participants.size() == 2
                && activePlayers.equals(Set.of(assigned.get().playerOneId(), assigned.get().playerTwoId()))
                && participants.stream().filter(player -> !player.isMaster()).allMatch(RoomPlayer::isReady);
    }

    public boolean canContinueStart(Room room, Set<Long> participantIds) {
        List<RoomPlayer> participants = room.getRoomPlayerList().stream()
                .filter(player -> player.getPosition() < 4)
                .toList();
        return participants.stream().map(RoomPlayer::getPlayerId).collect(Collectors.toSet()).equals(participantIds)
                && participants.stream().filter(player -> !player.isMaster()).allMatch(RoomPlayer::isReady);
    }

    public boolean activate(Room room, int gameSessionId) {
        if (room.getTournamentMatchId() == null) {
            return false;
        }
        List<Long> activePlayers = activeTournamentPlayerIds(room).stream().toList();
        return tournamentService.activateMatch(
                room.getTournamentMatchId(), room.getRoomId(), gameSessionId, activePlayers);
    }

    public void release(Room room) {
        if (room != null && room.isTournamentRoom()) {
            tournamentService.releaseRoom(room.getRoomId());
        }
    }

    public boolean deactivate(Room room, int gameSessionId) {
        return room != null
                && room.isTournamentRoom()
                && tournamentService.deactivateMatch(room.getRoomId(), gameSessionId);
    }

    public boolean onPlayerLeaving(
            Room room,
            long playerId,
            Integer gameSessionId,
            boolean completionStarted
    ) {
        if (room == null || !room.isTournamentRoom()) {
            return false;
        }
        Optional<TournamentService.AssignedMatch> assigned = tournamentService.matchForRoom(room.getRoomId())
                .filter(match -> match.contains(playerId));
        if (assigned.isEmpty()) {
            return isLastPlayerSlotOccupant(room, playerId);
        }
        boolean lastParticipant = room.getRoomPlayerList().stream()
                .filter(player -> player.getPosition() < 4)
                .filter(player -> assigned.get().contains(player.getPlayerId()))
                .allMatch(player -> player.getPlayerId() == playerId);
        if (!completionStarted && gameSessionId != null) {
            tournamentService.deactivateMatch(room.getRoomId(), gameSessionId);
        }
        if (!completionStarted && lastParticipant) {
            tournamentService.releaseRoom(room.getRoomId());
        }
        return lastParticipant;
    }

    public TournamentService.CompletionResult completeBasicMatch(
            Room room,
            int gameSessionId,
            GameSession gameSession,
            long reporterPlayerId,
            boolean redTeamWon
    ) {
        if (room == null
                || !room.isTournamentRoom()
                || gameSession == null
                || gameSession.getTournamentMatchId() == null
                || !gameSession.getTournamentMatchId().equals(room.getTournamentMatchId())) {
            return TournamentService.CompletionResult.NOT_FOUND;
        }
        Optional<TournamentService.AssignedMatch> assigned = tournamentService.matchForRoom(room.getRoomId());
        if (assigned.isEmpty()
                || room.getTournamentMatchId() != assigned.get().matchId()
                || gameSession.getTournamentParticipantPositions().size() != 2
                || !gameSession.getTournamentParticipantPositions().keySet().equals(
                        Set.of(assigned.get().playerOneId(), assigned.get().playerTwoId()))) {
            return TournamentService.CompletionResult.NOT_FOUND;
        }
        Optional<Long> winner = gameSession.getTournamentParticipantPositions().entrySet().stream()
                .filter(entry -> isRedTeam(entry.getValue()) == redTeamWon)
                .map(java.util.Map.Entry::getKey)
                .findFirst();
        if (winner.isEmpty()) {
            return TournamentService.CompletionResult.UNAUTHORIZED;
        }
        TournamentService.CompletionResult result = tournamentService.completeMatch(
                assigned.get().matchId(),
                room.getRoomId(),
                gameSessionId,
                reporterPlayerId,
                winner.get());
        log.info("Tournament match {} completion result: {}", assigned.get().matchId(), result);
        return result;
    }

    private Set<Long> activeTournamentPlayerIds(Room room) {
        return room.getRoomPlayerList().stream()
                .filter(player -> player.getPosition() < 4)
                .map(RoomPlayer::getPlayerId)
                .collect(Collectors.toSet());
    }

    private boolean isLastPlayerSlotOccupant(Room room, long playerId) {
        return room.getRoomPlayerList().stream()
                .filter(player -> player.getPosition() < 4)
                .allMatch(player -> player.getPlayerId() == playerId);
    }

    private int firstFree(Room room, int fromInclusive, int toExclusive) {
        for (int position = fromInclusive; position < toExclusive; position++) {
            if (room.getPositions().get(position) == RoomPositionState.Free) {
                return position;
            }
        }
        return -1;
    }

    private boolean isRedTeam(int position) {
        return position == 0 || position == 2;
    }
}
