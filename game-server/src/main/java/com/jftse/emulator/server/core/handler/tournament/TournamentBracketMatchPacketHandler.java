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
        boolean supported = client.hasPlayer()
                && tournamentManager.hasTournament(tournamentId)
                && supports(request.getBracketType(), request.getMatchIndex());
        List<TournamentManager.BracketMatch> matches = supported
                ? tournamentManager.getBracketMatches(
                        tournamentId,
                        request.getBracketType(),
                        Byte.toUnsignedInt(request.getMatchIndex()))
                : List.of();
        boolean found = supported && matches.size() == expectedMatchCount(request.getBracketType());

        connection.sendTCP(response(
                found ? TournamentManager.SUCCESS : TournamentManager.NOT_FOUND,
                tournamentId,
                request.getBracketType(),
                request.getMatchIndex(),
                matches
        ));
    }

    static boolean supports(byte bracketType, byte matchIndex) {
        int unsignedIndex = Byte.toUnsignedInt(matchIndex);
        return (bracketType == TournamentManager.QUALIFYING && unsignedIndex < 8)
                || (bracketType == TournamentManager.FINAL && unsignedIndex == 0);
    }

    private static int expectedMatchCount(byte bracketType) {
        return bracketType == TournamentManager.QUALIFYING ? 6 : 15;
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
