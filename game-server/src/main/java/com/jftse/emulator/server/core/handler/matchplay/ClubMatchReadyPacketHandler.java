package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.common.service.ConfigService;
import com.jftse.emulator.server.core.matchplay.ClubMatchCoordinator;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.matchplay.CMSGClubMatchReady;

import java.time.Duration;

@PacketId(CMSGClubMatchReady.PACKET_ID)
public class ClubMatchReadyPacketHandler implements PacketHandler<FTConnection, CMSGClubMatchReady> {
    @Override
    public void handle(FTConnection connection, CMSGClubMatchReady packet) {
        FTClient client = connection.getClient();
        if (client == null) {
            return;
        }
        if (!client.getIsGoingReady().compareAndSet(false, true)) {
            return;
        }

        try {
            int countdownSeconds = Math.max(1, ConfigService.getInstance()
                    .getValue("club.match.ready-wait.seconds", 5));
            ClubMatchCoordinator.getInstance().updateReady(connection, packet.getReady(),
                    Duration.ofSeconds(countdownSeconds));
        } finally {
            client.getIsGoingReady().set(false);
        }
    }
}
