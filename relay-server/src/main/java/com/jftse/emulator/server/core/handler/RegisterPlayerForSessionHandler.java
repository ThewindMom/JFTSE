package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.server.core.manager.RelayManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.relay.CMSGPlayerJoinSession;
import com.jftse.server.core.shared.packets.relay.SMSGPlayerJoinSessionResult;
import lombok.extern.log4j.Log4j2;

@PacketId(CMSGPlayerJoinSession.PACKET_ID)
@Log4j2
public class RegisterPlayerForSessionHandler implements PacketHandler<FTConnection, CMSGPlayerJoinSession> {
    @Override
    public void handle(FTConnection connection, CMSGPlayerJoinSession packet) {
        int playerId = packet.getPlayerIds().stream().findFirst().orElse(-1);
        boolean spectator = packet.getIsSpectator();
        int sessionId = packet.getSessionId();
        SMSGPlayerJoinSessionResult.Builder result = new SMSGPlayerJoinSessionResult.Builder();

        if (playerId != -1) {
            if (spectator) connection.getChannelHandlerContext().pipeline().remove("readTimeoutHandler");
            FTClient client = connection.getClient();
            client.setGameSessionId(sessionId);
            client.setPlayerId(playerId);
            client.setSpectator(spectator);
            RelayManager.getInstance().addClientToSession(sessionId, client);
            log.info("playerId {} connected for session: {}", playerId, sessionId);
            result.result((byte) 0);
        } else {
            result.result((byte) 1);
        }
        connection.sendTCP(result.build());
        if (playerId == -1) {
            log.error("playerId is -1");
            connection.close();
        }
    }
}
