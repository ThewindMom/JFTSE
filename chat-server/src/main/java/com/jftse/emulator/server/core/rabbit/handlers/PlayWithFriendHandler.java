package com.jftse.emulator.server.core.rabbit.handlers;

import com.jftse.emulator.common.exception.ValidationException;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.MiscConstants;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomJoinResult;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.RoomManager;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomListAnswerPacket;
import com.jftse.emulator.server.core.rabbit.MessageTypes;
import com.jftse.emulator.server.core.rabbit.messages.PlayWithFriendMessage;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.player.Player;
import com.jftse.server.core.rabbit.AbstractMessageHandler;
import com.jftse.server.core.rabbit.MessageHandlerRegistry;
import com.jftse.server.core.service.PlayerService;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomJoin;
import com.jftse.server.core.shared.packets.messenger.SMSGPlayWith;
import com.jftse.server.core.shared.rabbit.messages.PacketMessage;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Log4j2
public class PlayWithFriendHandler extends AbstractMessageHandler<PlayWithFriendMessage> {
    private static final short RESULT_SUCCESS_0 = 0;
    private static final short RESULT_SUCCESS_1 = 1;
    private static final short RESULT_IN_MATCH = -1;
    private static final short RESULT_CANT = -2;
    private static final short RESULT_NO_GAME_ROOM = -3;
    private static final short RESULT_FULL_ROOM = -4;
    private static final short RESULT_ALREADY_IN_ROOM = -5;

    @Autowired
    private GameManager gameManager;
    @Autowired
    private RProducerService rProducerService;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private RoomManager roomManager;

    @Override
    public void register(MessageHandlerRegistry registry) {
        registry.register(MessageTypes.PLAY_WITH_FRIEND.getValue(), this);
    }

    @Override
    public void handle(PlayWithFriendMessage message) {
        final String targetPlayerName = message.getPlayerName();
        final int targetServerId = message.getServerId();
        final long senderId = message.getSenderId();

        final FTConnection senderConnection = gameManager.getConnectionByPlayerId(senderId);

        final List<FTClient> clientList = new ArrayList<>(gameManager.getClients());
        final FTClient destClient = clientList.stream()
                .filter(client -> client.hasPlayer() && client.getPlayer().getName().equals(targetPlayerName))
                .findFirst()
                .orElse(null);

        if (senderConnection != null && destClient != null) {
            try {
                handleLocalPlayWith(senderConnection, destClient, targetServerId);
            } catch (ValidationException e) {
                handleOops(message);
            }
        }

        if (senderConnection != null && destClient == null) {
            handleOutgoingPlayWith(message, senderConnection);
        }

        if (senderConnection == null && destClient != null) {
            try {
                handleIncomingPlayWith(senderId, destClient, targetServerId);
            } catch (ValidationException e) {
                handleOops(message);
            }
        }

        if (senderConnection == null && destClient == null) {
            handleNotOnline(message);
        }
    }

    /**
     * Handles play with friend on the same server.
     *
     * @param senderConn     The connection of the sender
     * @param destClient   The target client to be invited
     * @param targetServerId The server ID of the target client
     */
    private void handleLocalPlayWith(final FTConnection senderConn, final FTClient destClient, int targetServerId) throws ValidationException {
        final FTClient senderClient = senderConn.getClient();
        if (senderClient != null && destClient != null && senderClient.hasPlayer() && destClient.hasPlayer()) {
            FTPlayer destPlayer = destClient.getPlayer();

            Room room = senderClient.getActiveRoom();
            Room destRoom = destClient.getActiveRoom();

            if (destRoom == null) {
                senderConn.sendTCP(answer(RESULT_NO_GAME_ROOM, destPlayer.getName(), targetServerId, 0));
                return;
            }

            if (destRoom.getStatus() == RoomStatus.NotRunning && room != null && destRoom.getRoomId() == room.getRoomId()) {
                senderConn.sendTCP(answer(RESULT_ALREADY_IN_ROOM, destPlayer.getName(), targetServerId, room.getRoomId()));
                return;
            }

            if (destRoom.getStatus() != RoomStatus.NotRunning) {
                senderConn.sendTCP(answer(RESULT_IN_MATCH, destPlayer.getName(), targetServerId, destRoom.getRoomId()));
                return;
            }

            if (hasRoomNoFreeSlot(destRoom)) {
                senderConn.sendTCP(answer(RESULT_FULL_ROOM, destPlayer.getName(), targetServerId, destRoom.getRoomId()));
                return;
            }

            if (!senderClient.getIsJoiningOrLeavingRoom().compareAndSet(false, true)) {
                senderConn.sendTCP(answer(RESULT_CANT, destPlayer.getName(), targetServerId, destRoom.getRoomId()));
                return;
            }

            RoomJoinResult joinResult = roomManager.joinRoom(senderClient, destRoom.getRoomId(), (byte) 0, destRoom.getPassword());
            if (joinResult.result() == 1) {
                resetIsJoiningOrLeavingRoom(senderClient);
                return;
            }

            SMSGRoomJoin.Builder roomJoinBuilder = SMSGRoomJoin.builder()
                    .result(joinResult.result())
                    .roomType((byte) 0)
                    .mode((byte) 0)
                    .mapId((byte) 0);

            if (joinResult.result() != 0) {
                SMSGRoomJoin roomJoinAnswerPacket = roomJoinBuilder.build();
                senderConn.sendTCP(roomJoinAnswerPacket);

                resetIsJoiningOrLeavingRoom(senderClient);

                if (joinResult.room() == null) {
                    S2CRoomListAnswerPacket roomListAnswerPacket = new S2CRoomListAnswerPacket(new ArrayList<>(roomManager.getRooms()));
                    senderConn.sendTCP(roomListAnswerPacket);
                } else {
                    GameManager.getInstance().updateRoomForAllClientsInMultiplayer(senderConn, joinResult.room());
                }

                return;
            }

            room = joinResult.room();
            List<FTClient> clientsInRoom = GameManager.getInstance().getClientsInRoom(room.getRoomId());

            SMSGRoomJoin roomJoinAnswerPacket = roomJoinBuilder
                    .roomType(room.getRoomType())
                    .mode(room.getMode())
                    .mapId(room.getMap())
                    .build();
            SMSGPlayWith playWithPacket = answer(RESULT_SUCCESS_0, destPlayer.getName(), targetServerId, room.getRoomId());
            senderConn.sendTCP(playWithPacket, roomJoinAnswerPacket);

            roomManager.sendRoomInformation(senderConn, room, clientsInRoom);

            GameManager.getInstance().updateLobbyRoomListForAllClients(senderConn);
            GameManager.getInstance().refreshLobbyPlayerListForAllClients();

            resetIsJoiningOrLeavingRoom(senderClient);
        } else {
            throw new ValidationException("Something went wrong.");
        }
    }

    /**
     * Cross-server play with friend handling. Send message to the game queue only to avoid looping of the handler.
     *
     * @param message The message to be sent
     * @param senderConn The connection of the sender
     */
    private void handleOutgoingPlayWith(PlayWithFriendMessage message, final FTConnection senderConn) {
        if (senderConn.getClient() == null || !senderConn.getClient().hasPlayer()) {
            handleNotOnline(message);
            return;
        }

        rProducerService.send(message, "game.messenger.friendList", senderConn.getClient().getPlayer().getName() + "(ChatServer)");
    }

    /**
     * Cross-server playing with friend handling. Here we send the message to the sending player.
     *
     * @param senderId The player ID of the sender
     * @param destClient The destination client to play with
     * @param senderServerId The server ID of the sender
     */
    private void handleIncomingPlayWith(long senderId, final FTClient destClient, int senderServerId) throws ValidationException {
        if (destClient.hasPlayer()) {
            Room destRoom = destClient.getActiveRoom();
            if (destRoom == null) {
                PacketMessage packetMessage = PacketMessage.builder()
                        .packet(answer(RESULT_NO_GAME_ROOM, destClient.getPlayer().getName(), senderServerId, 0))
                        .receivingPlayerId(senderId)
                        .build();
                rProducerService.send(packetMessage, "game.messenger.friendList chat.messenger.friendList", destClient.getPlayer().getName() + "(ChatServer)");
                return;
            }

            if (destRoom.getStatus() != RoomStatus.NotRunning) {
                PacketMessage packetMessage = PacketMessage.builder()
                        .packet(answer(RESULT_IN_MATCH, destClient.getPlayer().getName(), senderServerId, destRoom.getRoomId()))
                        .receivingPlayerId(senderId)
                        .build();
                rProducerService.send(packetMessage, "game.messenger.friendList chat.messenger.friendList", destClient.getPlayer().getName() + "(ChatServer)");
                return;
            }

            if (hasRoomNoFreeSlot(destRoom)) {
                PacketMessage packetMessage = PacketMessage.builder()
                        .packet(answer(RESULT_FULL_ROOM, destClient.getPlayer().getName(), senderServerId, destRoom.getRoomId()))
                        .receivingPlayerId(senderId)
                        .build();
                rProducerService.send(packetMessage, "game.messenger.friendList chat.messenger.friendList", destClient.getPlayer().getName() + "(ChatServer)");
                return;
            }

            Player sendingPlayer = playerService.findById(senderId);
            // TODO: Implement cross-server room joining logic here. For now, we just send a "can't join" message back to the sender.
            PacketMessage packetMessage = PacketMessage.builder()
                    .packet(answer(RESULT_CANT, destClient.getPlayer().getName(), senderServerId, destRoom.getRoomId()))
                    .receivingPlayerId(senderId)
                    .build();
            rProducerService.send(packetMessage, "game.messenger.friendList chat.messenger.friendList", sendingPlayer.getName() + "(ChatServer)");
        } else {
            throw new ValidationException("Something went wrong.");
        }
    }

    private boolean hasRoomNoFreeSlot(Room room) {
        final List<RoomPlayer> roomPlayerList = new ArrayList<>(room.getRoomPlayerList());
        long activePlayersCount = roomPlayerList.stream()
                .filter(rp -> rp.getPosition() < 4)
                .count();
        long spectatorsCount = roomPlayerList.stream()
                .filter(rp -> rp.getPosition() > 4 && rp.getPosition() != MiscConstants.InvisibleGmSlot)
                .count();

        return activePlayersCount >= room.getPlayers() && spectatorsCount >= 4;
    }

    /**
     * Handles the case when both sender and target are offline.
     *
     * @param message The invite friend message
     */
    private void handleNotOnline(PlayWithFriendMessage message) {
        Player sendingPlayer = playerService.findById(message.getSenderId());
        PacketMessage packetMessage = PacketMessage.builder()
                .packet(answer(RESULT_CANT, message.getPlayerName(), message.getServerId(), 0))
                .receivingPlayerId(message.getSenderId())
                .build();
        rProducerService.send(packetMessage, "game.messenger.friendList chat.messenger.friendList", sendingPlayer.getName() + "(ChatServer)");
    }

    private void handleOops(PlayWithFriendMessage message) {
        Player sendingPlayer = playerService.findById(message.getSenderId());
        PacketMessage packetMessage = PacketMessage.builder()
                .packet(answer(RESULT_CANT, message.getPlayerName(), message.getServerId(), 0))
                .receivingPlayerId(message.getSenderId())
                .build();
        rProducerService.send(packetMessage, "game.messenger.friendList chat.messenger.friendList", sendingPlayer.getName() + "(ChatServer)");
    }

    private SMSGPlayWith answer(short result, String playerName, int serverId, int roomId) {
        return SMSGPlayWith.builder()
                .result(result)
                .playerName(playerName != null ? playerName : "")
                .roomId(roomId)
                .serverId((short) serverId)
                .build();
    }

    private void resetIsJoiningOrLeavingRoom(FTClient ftClient) {
        ftClient.getIsJoiningOrLeavingRoom().set(false);
    }
}
