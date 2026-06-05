package com.jftse.emulator.server.core.handler.lobby;

import com.jftse.emulator.server.core.life.event.GameEventBus;
import com.jftse.emulator.server.core.life.event.GameEventType;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.CMSGLobbyJoin;

@PacketId(CMSGLobbyJoin.PACKET_ID)
public class LobbyJoinPacketHandler implements PacketHandler<FTConnection, CMSGLobbyJoin> {
    @Override
    public void handle(FTConnection connection, CMSGLobbyJoin packet) {
        FTClient client = connection.getClient();

        if (!client.getIsJoiningOrLeavingLobby().compareAndSet(false, true)) {
            return;
        }

        if (!client.isInLobby()) {
            client.setInLobby(true);
        } else {
            return;
        }

        client.setLobbyCurrentRoomListPage(-1);

        GameManager.getInstance().handleRoomPlayerChanges(client.getConnection(), true);
        GameManager.getInstance().refreshLobbyPlayerListForAllClients();

        GameEventBus.call(GameEventType.LOBBY_JOINED, client);

        client.getIsJoiningOrLeavingLobby().set(false);
    }
}
