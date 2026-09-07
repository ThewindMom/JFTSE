package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.constants.RoomStatus;
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
        if (roomPlayer == null || !roomPlayer.getConnectedToRelay().compareAndSet(false, true)) {
            SMSGConnectedToRelay answer = SMSGConnectedToRelay.builder().result((byte) 1).build();
            connection.sendTCP(answer);

            Room room = ftClient.getActiveRoom();
            GameSession gameSession = ftClient.getActiveGameSession();
            boolean participantFailure = room == null
                    || !room.isTournamentRoom()
                    || (gameSession != null && ftClient.hasPlayer()
                    && gameSession.getTournamentParticipantPositions().containsKey(ftClient.getPlayer().getId()));
            if (room != null && participantFailure) {
                synchronized (room) {
                    room.setStatus(RoomStatus.RelayConnectionFailed);
                }
            }
            return;
        }

        Room room = ftClient.getActiveRoom();
        if (room == null) {
            return;
        }
        ConcurrentLinkedDeque<RoomPlayer> roomPlayerList = room.getRoomPlayerList();
        GameSession gameSession = ftClient.getActiveGameSession();
        boolean allConnected = room.isTournamentRoom()
                ? gameSession != null && gameSession.areTournamentParticipantsConnectedToRelay()
                : roomPlayerList.stream().allMatch(rp -> rp.getConnectedToRelay().get());
        if (allConnected) {
            room.setStatus(RoomStatus.RelayConnectionSuccess);
        }
    }
}
