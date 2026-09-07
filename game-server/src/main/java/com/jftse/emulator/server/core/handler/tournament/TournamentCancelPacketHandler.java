package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.tournament.TournamentManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentCancel;
import com.jftse.server.core.shared.packets.tournament.SMSGTournamentCancel;

@PacketId(CMSGTournamentCancel.PACKET_ID)
public class TournamentCancelPacketHandler implements PacketHandler<FTConnection, CMSGTournamentCancel> {
    private final TournamentManager tournamentManager = TournamentManager.getInstance();

    @Override
    public void handle(FTConnection connection, CMSGTournamentCancel request) {
        FTClient client = connection.getClient();
        byte status = client.hasPlayer()
                ? tournamentManager.cancel(request.getTournamentId(), client.getPlayer().getId())
                : TournamentManager.NOT_APPLIED;

        connection.sendTCP(SMSGTournamentCancel.builder().status(status).build());
    }
}
