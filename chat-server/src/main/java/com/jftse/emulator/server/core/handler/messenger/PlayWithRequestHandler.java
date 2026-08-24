package com.jftse.emulator.server.core.handler.messenger;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.rabbit.messages.PlayWithFriendMessage;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.messenger.CMSGPlayWith;

@PacketId(CMSGPlayWith.PACKET_ID)
public class PlayWithRequestHandler implements PacketHandler<FTConnection, CMSGPlayWith> {
    private final RProducerService rProducerService;

    public PlayWithRequestHandler() {
        rProducerService = RProducerService.getInstance();
    }

    @Override
    public void handle(FTConnection connection, CMSGPlayWith packet) {
        FTClient ftClient = connection.getClient();
        if (ftClient == null || !ftClient.hasPlayer()) {
            return;
        }

        FTPlayer player = ftClient.getPlayer();
        PlayWithFriendMessage playWithFriendMessage = PlayWithFriendMessage.playWithFriendBuilder()
                .senderId(player.getId())
                .playerName(packet.getPlayerName())
                .serverId(packet.getServerId())
                .build();
        rProducerService.send(playWithFriendMessage, "chat.messenger.friendList", player.getName() + "(ChatServer)");
    }
}
