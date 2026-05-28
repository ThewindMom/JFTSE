package com.jftse.emulator.server.core.handler.ranking;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.guild.Guild;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.GuildService;
import com.jftse.server.core.shared.packets.ranking.CMSGRankingClubData;
import com.jftse.server.core.shared.packets.ranking.RankingClubDataRow;
import com.jftse.server.core.shared.packets.ranking.SMSGRankingClubData;
import org.springframework.data.domain.Page;

import java.util.List;

@PacketId(CMSGRankingClubData.PACKET_ID)
public class RankingClubDataHandler implements PacketHandler<FTConnection, CMSGRankingClubData> {
    private final GuildService guildService;

    public RankingClubDataHandler() {
        guildService = ServiceManager.getInstance().getGuildService();
    }

    @Override
    public void handle(FTConnection connection, CMSGRankingClubData packet) throws Exception {
        int mode = packet.getMode();
        int page = Math.max(packet.getPage(), 0);

        Page<Guild> guildPage = this.guildService.findAllRankingPage(mode, page);

        List<RankingClubDataRow> rows = guildPage.getContent().stream()
                .map(guild -> {
                    int i = guildPage.getContent().indexOf(guild);
                    int ranking = (page * 10) + 1 + i;

                    int wins;
                    int losses;
                    int points;

                    if (mode == 1) {
                        wins = guild.getLeagueRecordWin();
                        losses = guild.getLeagueRecordLoose();
                        points = guild.getLeaguePoints();
                    } else {
                        wins = guild.getBattleRecordWin();
                        losses = guild.getBattleRecordLoose();
                        points = guild.getClubPoints();
                    }

                    return RankingClubDataRow.builder()
                            .ranking(ranking)
                            .clubId(Math.toIntExact(guild.getId()))
                            .clubName(guild.getName())
                            .clubLevel(guild.getLevel())
                            .wins(wins)
                            .losses(losses)
                            .points(points)
                            .build();
                })
                .toList();

        SMSGRankingClubData resp = SMSGRankingClubData.builder()
                .mode(mode)
                .page(guildPage.getNumber())
                .totalPages(guildPage.getTotalPages())
                .rows(rows)
                .build();

        connection.sendTCP(resp);
    }
}
