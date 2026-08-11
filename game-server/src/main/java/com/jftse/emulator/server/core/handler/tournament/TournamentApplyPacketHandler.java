package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.tournament.TournamentManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentApply;
import com.jftse.server.core.shared.packets.tournament.SMSGTournamentApply;

@PacketId(CMSGTournamentApply.PACKET_ID)
public class TournamentApplyPacketHandler implements PacketHandler<FTConnection, CMSGTournamentApply> {
    private final TournamentManager tournamentManager = TournamentManager.getInstance();

    @Override
    public void handle(FTConnection connection, CMSGTournamentApply request) {
        FTClient client = connection.getClient();
        byte status = client.hasPlayer()
                ? tournamentManager.apply(request.getTournamentId(), client.getPlayer().getId())
                : TournamentManager.NOT_APPLIED;

        connection.sendTCP(SMSGTournamentApply.builder().status(status).build());
    }
}
