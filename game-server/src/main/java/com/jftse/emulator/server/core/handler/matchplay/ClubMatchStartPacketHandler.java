package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.life.room.ClubMatchRules;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.matchplay.ClubMatchCoordinator;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.matchplay.CMSGClubMatchStart;

@PacketId(CMSGClubMatchStart.PACKET_ID)
public class ClubMatchStartPacketHandler implements PacketHandler<FTConnection, CMSGClubMatchStart> {
    @Override
    public void handle(FTConnection connection, CMSGClubMatchStart packet) {
        FTClient client = connection.getClient();
        if (client == null) {
            return;
        }
        Room room = client.getActiveRoom();
        RoomPlayer roomPlayer = client.getRoomPlayer();
        if (!client.hasPlayer() || !ClubMatchRules.isClubMatch(room) || roomPlayer == null) {
            return;
        }

        ClubMatchCoordinator.getInstance().startFromClient(room, roomPlayer.getPlayerId());
    }
}
