package com.jftse.emulator.server.core.handler.messenger;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.rabbit.messages.InviteFriendMessage;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.messenger.CMSGInviteFriend;
import lombok.extern.log4j.Log4j2;

@Log4j2
@PacketId(CMSGInviteFriend.PACKET_ID)
public class InviteFriendRequestHandler implements PacketHandler<FTConnection, CMSGInviteFriend> {
    private final RProducerService rProducerService;

    public InviteFriendRequestHandler() {
        rProducerService = RProducerService.getInstance();
    }

    @Override
    public void handle(FTConnection connection, CMSGInviteFriend packet) {
        FTClient ftClient = connection.getClient();
        if (ftClient == null || !ftClient.hasPlayer() || ftClient.getActiveRoom() == null) {
            return;
        }

        FTPlayer player = ftClient.getPlayer();
        Room room = ftClient.getActiveRoom();

        InviteFriendMessage inviteFriendMessage = InviteFriendMessage.builder()
                .senderId(player.getId())
                .senderRoomId(room.getRoomId())
                .playerName(packet.getPlayerName())
                .serverId(packet.getServerId())
                .build();
        rProducerService.send(inviteFriendMessage, "game.messenger.friendList", player.getName() + "(GameServer)");
    }
}
