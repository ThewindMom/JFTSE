package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomCreateAnswerPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomInformationPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomPlayerInformationPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreateQuick;

@PacketId(CMSGRoomCreateQuick.PACKET_ID)
public class RoomCreateQuickRequestPacketHandler implements PacketHandler<FTConnection, CMSGRoomCreateQuick> {
    @Override
    public void handle(FTConnection connection, CMSGRoomCreateQuick packet) {
        FTClient client = connection.getClient();
        // prevent multiple room creations, this might have to be adjusted into a "room join answer"
        if (client.getActiveRoom() != null || !client.hasPlayer())
            return;

        if (!client.getIsJoiningOrLeavingRoom().compareAndSet(false, true)) {
            return;
        }

        Room room = GameManager.getInstance().getRoomManager().createRoom(packet, client);
        client.setActiveRoom(room);
        client.setInLobby(false);

        RoomPlayer roomPlayer = client.getRoomPlayer();
        float spawnX = roomPlayer.getLastX();
        float spawnY = roomPlayer.getLastY();

        S2CRoomCreateAnswerPacket roomCreateAnswerPacket = new S2CRoomCreateAnswerPacket((char) 0, room.getRoomType(), room.getMode(), room.getMap());
        S2CRoomInformationPacket roomInformationPacket = new S2CRoomInformationPacket(room);
        S2CRoomPlayerInformationPacket roomPlayerInformationPacket = new S2CRoomPlayerInformationPacket(roomPlayer, spawnX, spawnY, room.getMode() == 2 ? 0.0f : spawnX, room.getMode() == 2 ? 0.0f : spawnY, roomPlayer.getLastMapLayer());

        connection.sendTCP(roomCreateAnswerPacket);
        connection.sendTCP(roomInformationPacket);
        connection.sendTCP(roomPlayerInformationPacket);

        if (room.getMode() == 1) {
            GameManager.getInstance().getFishManager().registerRoom(room.getRoomId());
        }

        GameManager.getInstance().updateLobbyRoomListForAllClients(connection);
        GameManager.getInstance().refreshLobbyPlayerListForAllClients();

        client.getIsJoiningOrLeavingRoom().set(false);
    }
}
