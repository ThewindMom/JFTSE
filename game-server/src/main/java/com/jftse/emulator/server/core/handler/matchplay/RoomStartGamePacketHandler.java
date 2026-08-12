package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.life.room.ClubMatchRules;
import com.jftse.emulator.server.core.matchplay.RoomGameLauncher;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.matchplay.CMSGStartGame;

@PacketId(CMSGStartGame.PACKET_ID)
public class RoomStartGamePacketHandler implements PacketHandler<FTConnection, CMSGStartGame> {
    @Override
    public void handle(FTConnection connection, CMSGStartGame packet) {
        FTClient client = connection.getClient();
        if (client != null && ClubMatchRules.isClubServerRoom(client.getActiveRoom())) {
            return;
        }
        new RoomGameLauncher().launchOrdinary(connection);
    }
}
