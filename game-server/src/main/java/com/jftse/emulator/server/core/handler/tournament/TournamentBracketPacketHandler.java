package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.tournament.TournamentManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentBracket;
import com.jftse.server.core.shared.packets.tournament.SMSGTournamentBracket;

import java.nio.charset.StandardCharsets;
import java.util.List;

@PacketId(CMSGTournamentBracket.PACKET_ID)
public class TournamentBracketPacketHandler implements PacketHandler<FTConnection, CMSGTournamentBracket> {
    private static final int FIXED_NAME_BYTES = 12;

    private final TournamentManager tournamentManager = TournamentManager.getInstance();

    @Override
    public void handle(FTConnection connection, CMSGTournamentBracket request) {
        FTClient client = connection.getClient();
        int tournamentId = request.getTournamentId();
        boolean supported = client.hasPlayer()
                && tournamentManager.hasTournament(tournamentId)
                && supports(request.getBracketType(), request.getPage());
        List<TournamentManager.BracketEntry> entries = supported
                ? tournamentManager.getBracketEntries(
                        tournamentId,
                        request.getBracketType(),
                        Byte.toUnsignedInt(request.getPage()))
                : List.of();
        boolean found = supported && entries.size() == expectedEntryCount(request.getBracketType());

        connection.sendTCP(response(
                found ? TournamentManager.SUCCESS : TournamentManager.NOT_FOUND,
                tournamentId,
                request.getBracketType(),
                request.getPage(),
                entries
        ));
    }

    static boolean supports(byte bracketType, byte page) {
        int unsignedPage = Byte.toUnsignedInt(page);
        return (bracketType == TournamentManager.QUALIFYING && unsignedPage < 8)
                || (bracketType == TournamentManager.FINAL && unsignedPage == 0);
    }

    private static int expectedEntryCount(byte bracketType) {
        return bracketType == TournamentManager.QUALIFYING ? 6 : 16;
    }

    static SMSGTournamentBracket response(
            byte status,
            int tournamentId,
            byte bracketType,
            byte page,
            List<TournamentManager.BracketEntry> entries
    ) {
        SMSGTournamentBracket response = SMSGTournamentBracket.builder().status(status).build();
        if (status != TournamentManager.SUCCESS) {
            return response;
        }

        response.write(tournamentId, bracketType, page, (byte) 0, (short) entries.size());
        entries.forEach(entry -> response.write(
                fixedUtf16(entry.first()),
                fixedUtf16(entry.second()),
                fixedUtf16(entry.third())
        ));
        return response;
    }

    private static byte[] fixedUtf16(String value) {
        byte[] fixed = new byte[FIXED_NAME_BYTES];
        byte[] encoded = value.getBytes(StandardCharsets.UTF_16LE);
        System.arraycopy(encoded, 0, fixed, 0, Math.min(encoded.length, fixed.length - 2));
        return fixed;
    }
}
