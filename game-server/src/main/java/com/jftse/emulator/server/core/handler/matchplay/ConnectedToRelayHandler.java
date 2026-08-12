package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.matchplay.CMSGConnectedToRelay;
import com.jftse.server.core.shared.packets.matchplay.SMSGConnectedToRelay;

import java.util.concurrent.ConcurrentLinkedDeque;

@PacketId(CMSGConnectedToRelay.PACKET_ID)
public class ConnectedToRelayHandler implements PacketHandler<FTConnection, CMSGConnectedToRelay> {
    @Override
    public void handle(FTConnection connection, CMSGConnectedToRelay packet) {
        FTClient ftClient = connection.getClient();
        if (ftClient == null) {
            return;
        }

        RoomPlayer roomPlayer = ftClient.getRoomPlayer();
        Room room = ftClient.getActiveRoom();
        GameSession gameSession = ftClient.getActiveGameSession();
        if (roomPlayer == null || room == null || gameSession == null ||
                room.getStatus() != RoomStatus.StartingGame ||
                !gameSession.getClients().contains(ftClient) ||
                !gameSession.getGameplayActorPositions().contains(roomPlayer.getPosition())) {
            return;
        }
        if (!roomPlayer.getConnectedToRelay().compareAndSet(false, true)) {
            return;
        }

        ConcurrentLinkedDeque<RoomPlayer> roomPlayerList = room.getRoomPlayerList();
        int gameplayPositionLimit = room.getRoomType() == RoomType.BATTLEMON ? 2 : 4;
        if (roomPlayerList.stream()
                .filter(rp -> rp.getPosition() < gameplayPositionLimit)
                .allMatch(rp -> rp.getConnectedToRelay().get())) {
            synchronized (room) {
                if (room.getStatus() == RoomStatus.StartingGame) {
                    room.setStatus(RoomStatus.RelayConnectionSuccess);
                }
            }
        }
    }
}
