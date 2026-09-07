package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.CMSGSceneChange;
import lombok.extern.log4j.Log4j2;

@Log4j2
@PacketId(CMSGSceneChange.PACKET_ID)
public class SceneChangePacketHandler implements PacketHandler<FTConnection, CMSGSceneChange> {
    @Override
    public void handle(FTConnection connection, CMSGSceneChange packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer()) {
            return;
        }

        int oldSceneId = client.getSceneId();
        int newSceneId = packet.getSceneId();
        client.setSceneId(newSceneId);
        log.debug("{} changed scene from {} to {}", client.getPlayer().getName(), oldSceneId, newSceneId);
    }
}
