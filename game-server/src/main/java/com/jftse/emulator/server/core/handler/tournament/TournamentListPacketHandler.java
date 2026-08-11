package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.tournament.TournamentManager;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentList;
import com.jftse.server.core.shared.packets.tournament.SMSGTournamentList;
import com.jftse.server.core.shared.packets.tournament.Tournament;
import com.jftse.server.core.shared.packets.tournament.TournamentPair;
import com.jftse.server.core.shared.packets.tournament.TournamentReward;

import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

@PacketId(CMSGTournamentList.PACKET_ID)
public class TournamentListPacketHandler implements PacketHandler<FTConnection, CMSGTournamentList> {
    private static final int BRACKET_SIZE = 16;
    private static final List<TournamentReward> REWARDS = IntStream.range(0, 5)
            .mapToObj(ignored -> TournamentReward.builder().productIndex(287).useType(0).build())
            .toList();
    private static final List<TournamentPair> EMPTY_BRACKET = IntStream.range(0, BRACKET_SIZE)
            .mapToObj(ignored -> TournamentPair.builder().first(0).second(0).build())
            .toList();

    private final TournamentManager tournamentManager = TournamentManager.getInstance();

    @Override
    public void handle(FTConnection connection, CMSGTournamentList request) {
        List<Tournament> tournaments = tournamentManager.getTournaments(request.getPage()).stream()
                .map(TournamentListPacketHandler::toPacket)
                .toList();

        connection.sendTCP(SMSGTournamentList.builder().tournaments(tournaments).build());
    }

    static Tournament toPacket(TournamentManager.TournamentDefinition definition) {
        return Tournament.builder()
                .tournamentId(definition.tournamentId())
                .entryType((byte) 1)
                .gameMode((byte) 0)
                .title(definition.title())
                .applicationStart(Date.from(definition.applicationStart()))
                .applicationEnd(Date.from(definition.applicationEnd()))
                .tournamentStart(Date.from(definition.tournamentStart()))
                .tournamentEnd(Date.from(definition.tournamentEnd()))
                .unknown0(2)
                .unknown1(3)
                .unknown2((byte) 0)
                .unknown3(4)
                .unknownTime(Date.from(definition.applicationStart()))
                .status((byte) 1)
                .unknownFlag(false)
                .unknown4(BRACKET_SIZE)
                .bracketSize(BRACKET_SIZE)
                .rewards(REWARDS)
                .bracketA(EMPTY_BRACKET)
                .bracketB(EMPTY_BRACKET)
                .build();
    }
}
