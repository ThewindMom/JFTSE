package com.jftse.emulator.server.core.rabbit.handlers;

import com.jftse.emulator.common.exception.ValidationException;
import com.jftse.emulator.server.core.constants.MiscConstants;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.rabbit.MessageTypes;
import com.jftse.emulator.server.core.rabbit.messages.InviteFriendMessage;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.player.Player;
import com.jftse.server.core.rabbit.AbstractMessageHandler;
import com.jftse.server.core.rabbit.MessageHandlerRegistry;
import com.jftse.server.core.service.PlayerService;
import com.jftse.server.core.shared.packets.messenger.SMSGInviteFriend;
import com.jftse.server.core.shared.packets.messenger.SMSGInviteFriendNotify;
import com.jftse.server.core.shared.rabbit.messages.PacketMessage;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Log4j2
public class InviteFriendHandler extends AbstractMessageHandler<InviteFriendMessage> {
    private static final short RESULT_SUCCESS = 0;
    private static final short RESULT_IN_MATCH = -1;
    private static final short RESULT_NOT_CONNECTED = -2;
    private static final short RESULT_CANT = -3;
    private static final short RESULT_FULL_ROOM = -4;
    private static final short RESULT_ALREADY_IN_ROOM = -5;

    @Autowired
    private GameManager gameManager;
    @Autowired
    private RProducerService rProducerService;
    @Autowired
    private PlayerService playerService;

    @Override
    public void register(MessageHandlerRegistry registry) {
        registry.register(MessageTypes.INVITE_FRIEND.getValue(), this);
    }

    @Override
    public void handle(InviteFriendMessage message) {
        final String targetPlayerName = message.getPlayerName();
        final int targetServerId = message.getServerId();
        final long senderId = message.getSenderId();
        final int targetRoomId = message.getSenderRoomId();

        final FTConnection senderConnection = gameManager.getConnectionByPlayerId(senderId);

        final List<FTClient> clientList = new ArrayList<>(gameManager.getClients());
        final FTClient targetClient = clientList.stream()
                .filter(client -> client.hasPlayer() && client.getPlayer().getName().equals(targetPlayerName))
                .findFirst()
                .orElse(null);

        if (senderConnection != null && targetClient != null) {
            try {
                handleLocalInvite(senderConnection, targetClient, targetServerId);
            } catch (ValidationException e) {
                handleOops(message);
            }
        }

        if (senderConnection != null && targetClient == null) {
            handleOutgoingInvite(message, senderConnection);
        }

        if (senderConnection == null && targetClient != null) {
            try {
                handleIncomingInvite(senderId, targetClient, targetServerId, targetRoomId);
            } catch (ValidationException e) {
                handleOops(message);
            }
        }

        if (senderConnection == null && targetClient == null) {
            handleNotOnline(message);
        }
    }

    /**
     * Handles friend invites on the same server.
     *
     * @param senderConn     The connection of the sender
     * @param targetClient   The target client to be invited
     * @param targetServerId The server ID of the target client
     */
    private void handleLocalInvite(final FTConnection senderConn, final FTClient targetClient, int targetServerId) throws ValidationException {
        final FTClient senderClient = senderConn.getClient();
        if (senderClient != null && targetClient != null && senderClient.hasPlayer() && targetClient.hasPlayer()) {
            Room room = senderClient.getActiveRoom();
            if (room == null || room.getStatus() != RoomStatus.NotRunning) {
                senderConn.sendTCP(SMSGInviteFriend.builder().result(RESULT_CANT).build());
                return;
            }

            Room targetRoom = targetClient.getActiveRoom();
            if (targetRoom != null && targetRoom.getStatus() == RoomStatus.NotRunning && targetRoom.getRoomId() == room.getRoomId()) {
                senderConn.sendTCP(SMSGInviteFriend.builder().result(RESULT_ALREADY_IN_ROOM).build());
                return;
            }

            if (targetRoom != null && targetRoom.getStatus() != RoomStatus.NotRunning) {
                senderConn.sendTCP(SMSGInviteFriend.builder().result(RESULT_IN_MATCH).build());
                return;
            }

            if (hasRoomNoFreeSlot(room)) {
                senderConn.sendTCP(SMSGInviteFriend.builder().result(RESULT_FULL_ROOM).build());
                return;
            }

            SMSGInviteFriendNotify notification = SMSGInviteFriendNotify.builder()
                    .playerName(senderClient.getPlayer().getName())
                    .serverId((short) targetServerId)
                    .roomId(room.getRoomId())
                    .build();
            final FTConnection targetConnection = targetClient.getConnection();
            if (targetConnection != null) {
                targetConnection.sendTCP(notification);
            } else {
                throw new ValidationException("Target connection is null.");
            }

            senderConn.sendTCP(SMSGInviteFriend.builder().result(RESULT_SUCCESS).build());
        } else {
            throw new ValidationException("Something went wrong.");
        }
    }

    /**
     * Cross-server friend invite handling. Send message to the game queue only to avoid looping of the handler.
     *
     * @param message The message to be sent
     * @param senderConn The connection of the sender
     */
    private void handleOutgoingInvite(InviteFriendMessage message, final FTConnection senderConn) {
        if (senderConn.getClient() == null || !senderConn.getClient().hasPlayer()) {
            handleNotOnline(message);
            return;
        }

        final FTClient senderClient = senderConn.getClient();
        Room room = senderClient.getActiveRoom();
        if (room == null || room.getStatus() != RoomStatus.NotRunning) {
            senderConn.sendTCP(SMSGInviteFriend.builder().result(RESULT_CANT).build());
            return;
        }

        if (hasRoomNoFreeSlot(room)) {
            senderConn.sendTCP(SMSGInviteFriend.builder().result(RESULT_FULL_ROOM).build());
            return;
        }

        rProducerService.send(message, "chat.messenger.friendList", senderConn.getClient().getPlayer().getName() + "(GameServer)");
    }

    /**
     * Cross-server friend invite handling. Here we send the message to the receiving player.
     *
     * @param senderId The player ID of the sender
     * @param targetClient The target client to be invited
     * @param senderServerId The server ID of the sender
     * @param targetRoomId The room ID of the senders room
     */
    private void handleIncomingInvite(long senderId, final FTClient targetClient, int senderServerId, int targetRoomId) throws ValidationException {
        if (targetClient.hasPlayer()) {
            Room targetRoom = targetClient.getActiveRoom();

            if (targetRoom != null && targetRoom.getStatus() != RoomStatus.NotRunning) {
                PacketMessage packetMessage = PacketMessage.builder()
                        .packet(SMSGInviteFriend.builder().result(RESULT_IN_MATCH).build())
                        .receivingPlayerId(senderId)
                        .build();
                rProducerService.send(packetMessage, "game.messenger.friendList chat.messenger.friendList", targetClient.getPlayer().getName() + "(GameServer)");
                return;
            }

            Player sendingPlayer = playerService.findById(senderId);
            SMSGInviteFriendNotify notification = SMSGInviteFriendNotify.builder()
                    .playerName(sendingPlayer.getName())
                    .serverId((short) senderServerId)
                    .roomId(targetRoomId)
                    .build();
            final FTConnection targetConnection = targetClient.getConnection();
            if (targetConnection != null) {
                targetConnection.sendTCP(notification);
            } else {
                throw new ValidationException("Target connection is null.");
            }

            PacketMessage packetMessage = PacketMessage.builder()
                    .packet(SMSGInviteFriend.builder().result(RESULT_SUCCESS).build())
                    .receivingPlayerId(senderId)
                    .build();
            rProducerService.send(packetMessage, "game.messenger.friendList chat.messenger.friendList", sendingPlayer.getName() + "(GameServer)");
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
    private void handleNotOnline(InviteFriendMessage message) {
        Player sendingPlayer = playerService.findById(message.getSenderId());
        PacketMessage packetMessage = PacketMessage.builder()
                .packet(SMSGInviteFriend.builder().result(RESULT_NOT_CONNECTED).build())
                .receivingPlayerId(message.getSenderId())
                .build();
        rProducerService.send(packetMessage, "game.messenger.friendList chat.messenger.friendList", sendingPlayer.getName() + "(GameServer)");
    }

    private void handleOops(InviteFriendMessage message) {
        Player sendingPlayer = playerService.findById(message.getSenderId());
        PacketMessage packetMessage = PacketMessage.builder()
                .packet(SMSGInviteFriend.builder().result(RESULT_CANT).build())
                .receivingPlayerId(message.getSenderId())
                .build();
        rProducerService.send(packetMessage, "game.messenger.friendList chat.messenger.friendList", sendingPlayer.getName() + "(GameServer)");
    }
}
