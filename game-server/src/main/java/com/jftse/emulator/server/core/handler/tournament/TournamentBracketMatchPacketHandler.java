package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.tournament.TournamentManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentBracketMatch;
import com.jftse.server.core.shared.packets.tournament.SMSGTournamentBracketMatch;

import java.util.List;

@PacketId(CMSGTournamentBracketMatch.PACKET_ID)
public class TournamentBracketMatchPacketHandler implements PacketHandler<FTConnection, CMSGTournamentBracketMatch> {
    private final TournamentManager tournamentManager = TournamentManager.getInstance();

    @Override
    public void handle(FTConnection connection, CMSGTournamentBracketMatch request) {
        FTClient client = connection.getClient();
        int tournamentId = request.getTournamentId();
        boolean found = client.hasPlayer()
                && tournamentManager.hasTournament(tournamentId)
                && supports(request.getBracketType(), request.getMatchIndex());
        List<TournamentManager.BracketMatch> matches = found
                ? tournamentManager.getBracketMatches(tournamentId)
                : List.of();

        connection.sendTCP(response(
                found ? TournamentManager.SUCCESS : TournamentManager.NOT_FOUND,
                tournamentId,
                request.getBracketType(),
                request.getMatchIndex(),
                matches
        ));
    }

    static boolean supports(byte bracketType, byte matchIndex) {
        return bracketType == 1 && matchIndex == 0;
    }

    static SMSGTournamentBracketMatch response(
            byte status,
            int tournamentId,
            byte bracketType,
            byte matchIndex,
            List<TournamentManager.BracketMatch> matches
    ) {
        SMSGTournamentBracketMatch response = SMSGTournamentBracketMatch.builder().status(status).build();
        if (status != TournamentManager.SUCCESS) {
            return response;
        }

        response.write(tournamentId, bracketType, matchIndex, (short) matches.size());
        matches.forEach(match -> response.write(match.result(), match.state()));
        return response;
    }
}
