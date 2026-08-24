package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomJoinResult;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.RoomManager;
import com.jftse.emulator.server.core.packets.lobby.room.*;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.player.*;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomJoin;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomJoin;

import java.util.List;

@PacketId(CMSGRoomJoin.PACKET_ID)
public class RoomJoinRequestPacketHandler implements PacketHandler<FTConnection, CMSGRoomJoin> {
    private final RoomManager roomManager;

    public RoomJoinRequestPacketHandler() {
        roomManager = GameManager.getInstance().getRoomManager();
    }

    @Override
    public void handle(FTConnection connection, CMSGRoomJoin roomJoinRequestPacket) {
        FTClient ftClient = connection.getClient();
        if (!ftClient.hasPlayer()) {
            SMSGRoomJoin answer = SMSGRoomJoin.builder()
                    .result((char) -10)
                    .roomType((byte) 0)
                    .mode((byte) 0)
                    .mapId((byte) 0)
                    .build();
            connection.sendTCP(answer);
            return;
        }

        if (!ftClient.getIsJoiningOrLeavingRoom().compareAndSet(false, true)) {
            return;
        }

        RoomJoinResult joinResult = roomManager.joinRoom(ftClient, roomJoinRequestPacket.getRoomId(), roomJoinRequestPacket.getUnk0(), roomJoinRequestPacket.getPassword());
        if (joinResult.result() == 1) {
            resetIsJoiningOrLeavingRoom(ftClient);
            return;
        }

        SMSGRoomJoin.Builder roomJoinBuilder = SMSGRoomJoin.builder()
                .result(joinResult.result())
                .roomType((byte) 0)
                .mode((byte) 0)
                .mapId((byte) 0);

        if (joinResult.result() != 0) {
            SMSGRoomJoin roomJoinAnswerPacket = roomJoinBuilder.build();
            connection.sendTCP(roomJoinAnswerPacket);

            resetIsJoiningOrLeavingRoom(ftClient);

            if (joinResult.room() == null) {
                S2CRoomListAnswerPacket roomListAnswerPacket = new S2CRoomListAnswerPacket(roomManager.getRooms().stream().filter(r -> !roomManager.isTownSquare(r)).toList());
                connection.sendTCP(roomListAnswerPacket);
            } else {
                GameManager.getInstance().updateRoomForAllClientsInMultiplayer(ftClient.getConnection(), joinResult.room());
            }

            return;
        }

        Room room = joinResult.room();
        List<FTClient> clientsInRoom = GameManager.getInstance().getClientsInRoom(room.getRoomId());

        SMSGRoomJoin roomJoinAnswerPacket = roomJoinBuilder
                .roomType(room.getRoomType())
                .mode(room.getMode())
                .mapId(room.getMap())
                .build();
        connection.sendTCP(roomJoinAnswerPacket);

        roomManager.sendRoomInformation(connection, joinResult.room(), clientsInRoom);

        GameManager.getInstance().updateLobbyRoomListForAllClients(connection);
        GameManager.getInstance().refreshLobbyPlayerListForAllClients();

        resetIsJoiningOrLeavingRoom(ftClient);
    }

    private void resetIsJoiningOrLeavingRoom(FTClient ftClient) {
        ftClient.getIsJoiningOrLeavingRoom().set(false);
    }
}
