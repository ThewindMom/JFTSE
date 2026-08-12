package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.matchplay.CMSGRelayServerProblem;

@PacketId(CMSGRelayServerProblem.PACKET_ID)
public class RelayConnectionProblemHandler implements PacketHandler<FTConnection, CMSGRelayServerProblem> {
    @Override
    public void handle(FTConnection connection, CMSGRelayServerProblem packet) {
        FTClient ftClient = connection.getClient();
        if (ftClient == null) {
            return;
        }

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
    }
}
