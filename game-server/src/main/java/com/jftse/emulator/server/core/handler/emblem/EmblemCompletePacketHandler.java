package com.jftse.emulator.server.core.handler.emblem;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.EmblemCompletionResult;
import com.jftse.server.core.service.EmblemQuestStatus;
import com.jftse.server.core.shared.packets.emblem.CMSGEmblemComplete;
import com.jftse.server.core.shared.packets.emblem.S2CEmblemCompletionPacket;
import com.jftse.server.core.shared.packets.emblem.S2CEmblemListPacket;

@PacketId(CMSGEmblemComplete.PACKET_ID)
public class EmblemCompletePacketHandler implements PacketHandler<FTConnection, CMSGEmblemComplete> {
    @Override
    public void handle(FTConnection connection, CMSGEmblemComplete packet) {
        if (!connection.getClient().hasPlayer()) {
            connection.sendTCP(new S2CEmblemCompletionPacket(
                    EmblemCompletionResult.failure(EmblemQuestStatus.NOT_ALLOWED)));
            connection.sendTCP(S2CEmblemListPacket.sentinel((short) -1));
            return;
        }

        var player = connection.getClient().getPlayer();
        var services = ServiceManager.getInstance();
        var emblemQuestService = services.getEmblemQuestService();
        EmblemCompletionResult result = emblemQuestService.complete(player.getPlayerRef(), packet.getQuestIndex());
        if (result.status() == EmblemQuestStatus.SUCCESS)
            player.sync(services.getPlayerService().findById(player.getId()));
        connection.sendTCP(new S2CEmblemCompletionPacket(result));
        connection.sendTCP(S2CEmblemListPacket.success(emblemQuestService.list(player.getId())));
    }
}
