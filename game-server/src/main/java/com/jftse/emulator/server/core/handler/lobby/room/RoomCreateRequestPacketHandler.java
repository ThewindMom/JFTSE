package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomCreateResult;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomCreateAnswerPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomInformationPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomPlayerListInformationPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreate;

import java.util.ArrayList;

@PacketId(CMSGRoomCreate.PACKET_ID)
public class RoomCreateRequestPacketHandler implements PacketHandler<FTConnection, CMSGRoomCreate> {
    @Override
    public void handle(FTConnection connection, CMSGRoomCreate packet) {
        FTClient client = connection.getClient();
        // prevent multiple room creations, this might have to be adjusted into a "room join answer"
        if (client.getActiveRoom() != null || !client.hasPlayer())
            return;

        if (packet.getRoomType() == RoomType.BATTLEMON
                && packet.getMode() != GameMode.BASIC
                && packet.getMode() != GameMode.BATTLE) {
            return;
        }

        // Mode 3 social rooms are server-managed Club Houses on the chat server.
        if (packet.getRoomType() == 1 && packet.getMode() == 3) {
            return;
        }

        if (!client.getIsJoiningOrLeavingRoom().compareAndSet(false, true)) {
            return;
        }

        RoomCreateResult created = GameManager.getInstance().getRoomManager().createRoom(packet, client);
        if (created.result() != 0 || created.room() == null) {
            connection.sendTCP(new S2CRoomCreateAnswerPacket(created.result(), (byte) 0, 0, (byte) 0));
            client.getIsJoiningOrLeavingRoom().set(false);
            return;
        }

        Room room = created.room();
        client.setActiveRoom(room);
        client.setInLobby(false);

        S2CRoomCreateAnswerPacket roomCreateAnswerPacket = new S2CRoomCreateAnswerPacket((char) 0, room.getRoomType(), room.getMode(), room.getMap());
        S2CRoomInformationPacket roomInformationPacket = new S2CRoomInformationPacket(room);
        S2CRoomPlayerListInformationPacket roomPlayerInformationPacket = new S2CRoomPlayerListInformationPacket(new ArrayList<>(room.getRoomPlayerList()));

        connection.sendTCP(roomCreateAnswerPacket);
        connection.sendTCP(roomInformationPacket);
        connection.sendTCP(roomPlayerInformationPacket);

        GameManager.getInstance().updateLobbyRoomListForAllClients(connection);
        GameManager.getInstance().refreshLobbyPlayerListForAllClients();

        client.getIsJoiningOrLeavingRoom().set(false);
    }
}
