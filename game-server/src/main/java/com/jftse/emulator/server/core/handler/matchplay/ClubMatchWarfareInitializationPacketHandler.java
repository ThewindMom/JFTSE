package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.client.GuildView;
import com.jftse.emulator.server.core.packets.matchplay.S2CClubMatchWarfareInitializationPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.CMSGDefault;

@PacketId(0x2700)
public class ClubMatchWarfareInitializationPacketHandler
        implements PacketHandler<FTConnection, CMSGDefault> {
    private static final int ACTIVE_WARFARE_STATE = 3;

    @Override
    public void handle(FTConnection connection, CMSGDefault packet) {
        FTClient client = connection.getClient();
        if (client == null || !client.hasPlayer()) {
            return;
        }

        GuildView guild = client.getPlayer().getGuild();
        if (guild == null) {
            return;
        }

        connection.sendTCP(new S2CClubMatchWarfareInitializationPacket(
                Math.toIntExact(guild.id()), guild.name(), ACTIVE_WARFARE_STATE, true));
    }
}
