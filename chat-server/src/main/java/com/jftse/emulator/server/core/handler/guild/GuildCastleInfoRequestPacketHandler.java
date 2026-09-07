package com.jftse.emulator.server.core.handler.guild;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.guild.S2CGuildCastleInfoAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.guild.Guild;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.GuildCastleService;
import com.jftse.server.core.shared.packets.guild.CMSGGuildCastleInfoRequest;

@PacketId(CMSGGuildCastleInfoRequest.PACKET_ID)
public class GuildCastleInfoRequestPacketHandler implements PacketHandler<FTConnection, CMSGGuildCastleInfoRequest> {
    private final GuildCastleService guildCastleService;

    public GuildCastleInfoRequestPacketHandler() {
        guildCastleService = ServiceManager.getInstance().getGuildCastleService();
    }

    @Override
    public void handle(FTConnection connection, CMSGGuildCastleInfoRequest packet) {
        FTClient client = connection.getClient();
        if (client == null || !client.hasPlayer()) {
            connection.sendTCP(new S2CGuildCastleInfoAnswerPacket((byte) -1, 0, 0, (byte) 0, 0));
            return;
        }

        Guild guild = guildCastleService.findForPlayer(client.getPlayer().getId());
        if (guild == null) {
            connection.sendTCP(new S2CGuildCastleInfoAnswerPacket((byte) -1, 0, 0, (byte) 0, 0));
            return;
        }

        connection.sendTCP(new S2CGuildCastleInfoAnswerPacket(
                (byte) 0, 0, 0, guild.getCastleAccessLimit(), guild.getCastleAdmissionFee()));
    }
}
