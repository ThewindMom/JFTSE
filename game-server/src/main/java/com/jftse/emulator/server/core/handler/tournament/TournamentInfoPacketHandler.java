package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.tournament.TournamentManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentInfo;
import com.jftse.server.core.shared.packets.tournament.SMSGTournamentInfo;

@PacketId(CMSGTournamentInfo.PACKET_ID)
public class TournamentInfoPacketHandler implements PacketHandler<FTConnection, CMSGTournamentInfo> {
    static final byte INFO_FAILED = -1;

    private final TournamentManager tournamentManager = TournamentManager.getInstance();

    @Override
    public void handle(FTConnection connection, CMSGTournamentInfo request) {
        FTClient client = connection.getClient();
        int tournamentId = request.getTournamentId();
        boolean found = client.hasPlayer() && tournamentManager.hasTournament(tournamentId);
        byte playerState = found
                ? tournamentManager.getPlayerState(tournamentId, client.getPlayer().getId())
                : 0;

        connection.sendTCP(response(found ? TournamentManager.SUCCESS : INFO_FAILED, tournamentId, playerState));
    }

    static SMSGTournamentInfo response(byte result, int tournamentId, boolean applied) {
        return response(result, tournamentId, (byte) (applied ? 1 : 0));
    }

    static SMSGTournamentInfo response(byte result, int tournamentId, byte playerState) {
        SMSGTournamentInfo response = SMSGTournamentInfo.builder().result(result).build();
        if (result == TournamentManager.SUCCESS) {
            response.write(
                    tournamentId,
                    playerState,
                    (byte) 0,
                    "",
                    "",
                    "",
                    (byte) 0,
                    (byte) 0,
                    0L,
                    0L
            );
        }
        return response;
    }
}
