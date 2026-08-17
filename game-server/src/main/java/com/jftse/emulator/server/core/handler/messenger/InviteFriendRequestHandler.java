package com.jftse.emulator.server.core.handler.messenger;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.messenger.EFriendshipState;
import com.jftse.entities.database.model.messenger.Friend;
import com.jftse.entities.database.model.player.Player;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.FriendService;
import com.jftse.server.core.service.PlayerService;
import com.jftse.server.core.shared.packets.messenger.CMSGInviteFriend;
import com.jftse.server.core.shared.packets.messenger.SMSGInviteFriend;
import com.jftse.server.core.shared.packets.messenger.SMSGInviteFriendNotify;
import com.jftse.server.core.shared.rabbit.messages.PacketMessage;

@PacketId(CMSGInviteFriend.PACKET_ID)
public class InviteFriendRequestHandler implements PacketHandler<FTConnection, CMSGInviteFriend> {
    private static final short RESULT_SUCCESS = 0;
    private static final short RESULT_NONE = -2;
    private static final short RESULT_CANT = -3;

    private final PlayerService playerService;
    private final FriendService friendService;
    private final RProducerService rProducerService;

    public InviteFriendRequestHandler() {
        playerService = ServiceManager.getInstance().getPlayerService();
        friendService = ServiceManager.getInstance().getFriendService();
        rProducerService = RProducerService.getInstance();
    }

    @Override
    public void handle(FTConnection connection, CMSGInviteFriend packet) {
        FTClient ftClient = connection.getClient();
        if (ftClient == null || !ftClient.hasPlayer()) {
            return;
        }

        FTPlayer player = ftClient.getPlayer();
        Room room = ftClient.getActiveRoom();
        if (room == null || room.getStatus() != RoomStatus.NotRunning) {
            connection.sendTCP(SMSGInviteFriend.builder().result(RESULT_CANT).build());
            return;
        }

        Player targetPlayer = playerService.findByName(packet.getPlayerName());
        if (targetPlayer == null || targetPlayer.getId().equals(player.getId()) || !Boolean.TRUE.equals(targetPlayer.getOnline())) {
            connection.sendTCP(SMSGInviteFriend.builder().result(RESULT_NONE).build());
            return;
        }

        Friend friendship = friendService.findByPlayerIdAndFriendId(player.getId(), targetPlayer.getId());
        if (friendship == null
                || (friendship.getEFriendshipState() != EFriendshipState.Friends
                && friendship.getEFriendshipState() != EFriendshipState.Relationship)) {
            connection.sendTCP(SMSGInviteFriend.builder().result(RESULT_CANT).build());
            return;
        }

        if (!room.getInvitedPlayerIds().contains(targetPlayer.getId())) {
            room.getInvitedPlayerIds().add(targetPlayer.getId());
        }

        // 0x466dc0 matches channel +0x8a (Free=1), not the 0-based game-room
        // list. Official 0x2347 sends the 1-based display id, which hits Free.
        // Do not send room.getRoomId() 0 — that misses the channel lookup.
        // 0x4a6e20 skips only if unk0 is signed-negative, not if it is 0.
        final short clientRoomId = packet.getRoomId() > 0 ? packet.getRoomId() : (short) (room.getRoomId() + 1);
        SMSGInviteFriendNotify notification = SMSGInviteFriendNotify.builder()
                .playerName(player.getName())
                .roomId(clientRoomId)
                .unk0(clientRoomId)
                .build();
        rProducerService.send(
                PacketMessage.builder()
                        .receivingPlayerId(targetPlayer.getId())
                        .packet(notification)
                        .build(),
                "game.messenger.friendList chat.messenger.friendList",
                player.getName() + "(GameServer)");

        connection.sendTCP(SMSGInviteFriend.builder().result(RESULT_SUCCESS).build());
    }
}
