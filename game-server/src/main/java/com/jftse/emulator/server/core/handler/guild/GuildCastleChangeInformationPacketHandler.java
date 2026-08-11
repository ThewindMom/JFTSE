package com.jftse.emulator.server.core.handler.guild;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.GuildCastleService;
import com.jftse.server.core.shared.packets.guild.CMSGGuildCastleChangeInformation;
import com.jftse.server.core.shared.packets.guild.SMSGGuildCastleChangeInformation;

@PacketId(CMSGGuildCastleChangeInformation.PACKET_ID)
public class GuildCastleChangeInformationPacketHandler implements PacketHandler<FTConnection, CMSGGuildCastleChangeInformation> {
    private final GuildCastleService guildCastleService;

    public GuildCastleChangeInformationPacketHandler() {
        guildCastleService = ServiceManager.getInstance().getGuildCastleService();
    }

    @Override
    public void handle(FTConnection connection, CMSGGuildCastleChangeInformation packet) {
        FTClient client = connection.getClient();
        byte result = GuildCastleService.CHANGE_GUILD_NOT_FOUND;
        if (client != null && client.hasPlayer()) {
            result = guildCastleService.changeInformation(
                    client.getPlayer().getId(), packet.getAccessLimit(), packet.getAdmissionFee());
        }
        connection.sendTCP(SMSGGuildCastleChangeInformation.builder().result(result).build());
    }
}
