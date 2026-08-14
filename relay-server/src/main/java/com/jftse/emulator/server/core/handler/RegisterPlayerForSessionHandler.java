package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.server.core.manager.RelayManager;
import com.jftse.emulator.server.core.manager.RelaySessionAuthorizationStore;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.relay.CMSGPlayerJoinSession;
import com.jftse.server.core.shared.packets.relay.SMSGPlayerJoinSessionResult;
import com.jftse.server.core.thread.ThreadManager;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@PacketId(CMSGPlayerJoinSession.PACKET_ID)
@Log4j2
public class RegisterPlayerForSessionHandler implements PacketHandler<FTConnection, CMSGPlayerJoinSession> {
    private static final long AUTHORIZATION_WAIT_NANOS = TimeUnit.SECONDS.toNanos(2);

    @Override
    public void handle(FTConnection connection, CMSGPlayerJoinSession matchplayPlayerIdsInSessionPacket) {
        FTClient client = connection.getClient();
        if (client == null || !client.getRegistrationPending().compareAndSet(false, true)) {
            return;
        }
        tryRegister(connection, matchplayPlayerIdsInSessionPacket, System.nanoTime() + AUTHORIZATION_WAIT_NANOS);
    }

    private void tryRegister(FTConnection connection, CMSGPlayerJoinSession packet, long deadline) {
        FTClient client = connection.getClient();
        if (client == null) {
            connection.close();
            return;
        }

        int playerId = packet.getPlayerIds().stream().findFirst().orElse(-1);
        int sessionId = packet.getSessionId();
        RelaySessionAuthorizationStore authorizationStore = RelaySessionAuthorizationStore.getInstance();
        if (playerId != -1 && authorizationStore.find(sessionId).isEmpty() && System.nanoTime() < deadline) {
            ThreadManager.getInstance().schedule(() -> tryRegister(connection, packet, deadline), 50);
            return;
        }

        completeRegistration(connection, sessionId, playerId, packet.getIsSpectator());
    }

    private void completeRegistration(FTConnection connection, int sessionId, int playerId, boolean isSpectator) {
        FTClient client = connection.getClient();
        RelaySessionAuthorizationStore authorizationStore = RelaySessionAuthorizationStore.getInstance();
        InetSocketAddress remoteAddress = connection.getRemoteAddressTCP();
        String remoteHost = remoteAddress == null || remoteAddress.getAddress() == null
                ? null
                : remoteAddress.getAddress().getHostAddress();
        RelaySessionAuthorizationStore.SessionAuthorization authorization = authorizationStore.find(sessionId)
                .orElse(null);
        boolean authorized = playerId != -1 && authorization != null &&
                authorizationStore.canRegister(sessionId, playerId, isSpectator, remoteHost);
        boolean registered = authorized && RelayManager.getInstance()
                .registerClient(sessionId, playerId, isSpectator, authorization.battlemon(),
                        authorization.generation(), client);

        if (registered && connection.getChannelHandlerContext().pipeline().get("readTimeoutHandler") != null) {
            connection.getChannelHandlerContext().pipeline().remove("readTimeoutHandler");
        }

        client.getRegistrationPending().set(false);
        connection.sendTCP(SMSGPlayerJoinSessionResult.builder().result(registered ? (byte) 0 : (byte) 1).build());

        if (!registered) {
            log.warn("Rejected relay registration for playerId {} and session {}", playerId, sessionId);
            connection.close();
            return;
        }

        log.info("playerId {} connected for session: {}", playerId, sessionId);
    }
}
