package com.jftse.emulator.server.core.handler.messenger;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.messenger.EFriendshipState;
import com.jftse.entities.database.model.messenger.Friend;
import com.jftse.entities.database.model.player.Player;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.FriendService;
import com.jftse.server.core.service.PlayerService;
import com.jftse.server.core.shared.packets.messenger.CMSGPlayWith;
import com.jftse.server.core.shared.packets.messenger.SMSGPlayWith;

@PacketId(CMSGPlayWith.PACKET_ID)
public class PlayWithRequestHandler implements PacketHandler<FTConnection, CMSGPlayWith> {
    private static final short RESULT_SUCCESS = 1;
    private static final short RESULT_CANT = -2;
    private static final short RESULT_NO_GAME_ROOM = -3;
    private static final short RESULT_FULL = -4;
    private static final short RESULT_ALREADY_JOINED = -5;

    private final PlayerService playerService;
    private final FriendService friendService;

    public PlayWithRequestHandler() {
        playerService = ServiceManager.getInstance().getPlayerService();
        friendService = ServiceManager.getInstance().getFriendService();
    }

    @Override
    public void handle(FTConnection connection, CMSGPlayWith packet) {
        FTClient ftClient = connection.getClient();
        if (ftClient == null || !ftClient.hasPlayer()) {
            return;
        }

        FTPlayer player = ftClient.getPlayer();
        final String hostName = packet.getPlayerName();
        final short roomId = packet.getServerId();

        Player hostPlayer = playerService.findByName(hostName);
        if (hostPlayer == null || hostPlayer.getId().equals(player.getId())) {
            connection.sendTCP(answer(RESULT_CANT, hostName, roomId));
            return;
        }

        Friend friendship = friendService.findByPlayerIdAndFriendId(player.getId(), hostPlayer.getId());
        if (friendship == null
                || (friendship.getEFriendshipState() != EFriendshipState.Friends
                && friendship.getEFriendshipState() != EFriendshipState.Relationship)) {
            connection.sendTCP(answer(RESULT_CANT, hostName, roomId));
            return;
        }

        Room room = findRoomForMessengerId(roomId);
        if (room == null || room.getStatus() != RoomStatus.NotRunning || !isHostInRoom(room, hostPlayer.getId())) {
            connection.sendTCP(answer(RESULT_NO_GAME_ROOM, hostName, roomId));
            return;
        }

        if (isAlreadyInRoom(room, player.getId())) {
            connection.sendTCP(answer(RESULT_ALREADY_JOINED, hostName, roomId));
            return;
        }

        if (!hasFreeSlot(room)) {
            connection.sendTCP(answer(RESULT_FULL, hostName, roomId));
            return;
        }

        if (!room.getInvitedPlayerIds().contains(player.getId())) {
            room.getInvitedPlayerIds().add(player.getId());
        }

        connection.sendTCP(answer(RESULT_SUCCESS, hostPlayer.getName(), roomId));
    }

    private static SMSGPlayWith answer(short result, String playerName, short roomId) {
        return SMSGPlayWith.builder()
                .result(result)
                .playerName(playerName != null ? playerName : "")
                .roomId(roomId)
                .serverId((short) 0)
                .build();
    }

    private static boolean isHostInRoom(Room room, Long hostPlayerId) {
        return isAlreadyInRoom(room, hostPlayerId);
    }

    private static boolean isAlreadyInRoom(Room room, Long playerId) {
        return room.getRoomPlayerList().stream().anyMatch(roomPlayer -> roomPlayer.getPlayerId() == playerId);
    }

    private static boolean hasFreeSlot(Room room) {
        final boolean isTownSquare = room.getRoomType() == 1 && room.getMode() == 2;
        if (isTownSquare) {
            return room.getRoomPlayerList().size() < room.getPlayers();
        }
        return room.getPositions().stream().anyMatch(position -> position == RoomPositionState.Free);
    }

    private static Room findRoomForMessengerId(short roomId) {
        var rooms = GameManager.getInstance().getRooms();
        Room exact = rooms.stream().filter(candidate -> candidate.getRoomId() == roomId).findFirst().orElse(null);
        if (exact != null) {
            return exact;
        }
        if (roomId > 0) {
            return rooms.stream().filter(candidate -> candidate.getRoomId() == roomId - 1).findFirst().orElse(null);
        }
        return null;
    }
}
