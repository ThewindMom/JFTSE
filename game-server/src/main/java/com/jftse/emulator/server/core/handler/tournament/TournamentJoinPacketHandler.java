package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.tournament.TournamentManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentJoin;
import com.jftse.server.core.shared.packets.tournament.SMSGTournamentJoin;
import com.jftse.server.core.shared.packets.tournament.Tournament;

@PacketId(CMSGTournamentJoin.PACKET_ID)
public class TournamentJoinPacketHandler implements PacketHandler<FTConnection, CMSGTournamentJoin> {
    private final TournamentManager tournamentManager = TournamentManager.getInstance();

    @Override
    public void handle(FTConnection connection, CMSGTournamentJoin request) {
        FTClient client = connection.getClient();
        byte status = client.hasPlayer() && tournamentManager.hasTournament(request.getTournamentId())
                ? TournamentManager.SUCCESS
                : TournamentManager.NOT_FOUND;

        connection.sendTCP(response(status, request.getTournamentId()));
    }

    static SMSGTournamentJoin response(byte status, int tournamentId) {
        SMSGTournamentJoin response = SMSGTournamentJoin.builder().status(status).build();
        if (status == TournamentManager.SUCCESS) {
            Tournament tournament = TournamentManager.getInstance().getTournament(tournamentId)
                    .map(TournamentListPacketHandler::toPacket)
                    .orElseThrow();
            response.write(
                    tournament.getTournamentId(),
                    tournament.getEntryType(),
                    tournament.getGameMode(),
                    tournament.getTitle(),
                    tournament.getApplicationStart(),
                    tournament.getApplicationEnd(),
                    tournament.getTournamentStart(),
                    tournament.getTournamentEnd(),
                    tournament.getUnknown0(),
                    tournament.getUnknown1(),
                    tournament.getUnknown2(),
                    tournament.getUnknown3(),
                    tournament.getUnknownTime(),
                    tournament.getStatus(),
                    tournament.getUnknownFlag(),
                    tournament.getUnknown4(),
                    tournament.getBracketSize()
            );
        }
        return response;
    }
}
