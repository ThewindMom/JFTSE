package com.jftse.server.core.service;

import com.jftse.entities.database.model.guild.Guild;
import com.jftse.entities.database.model.player.Player;

import java.util.List;

public interface GuildCastleService {
    byte ACCESS_MASTER = 0;
    byte ACCESS_SUBMASTER = 1;
    byte ACCESS_MEMBER = 2;
    byte ACCESS_ALL = 3;

    byte CHANGE_SUCCESS = 0;
    byte CHANGE_GUILD_NOT_FOUND = -1;
    byte CHANGE_REJECTED = -2;

    int MIN_ADMISSION_FEE = 0;
    int MAX_ADMISSION_FEE = 1000;

    Guild findById(Long guildId);

    Guild findForPlayer(Long playerId);

    List<Guild> findAll();

    byte changeInformation(Long playerId, byte accessLimit, int admissionFee);

    boolean canEnter(Long playerId, Long guildId);

    Player chargeAdmission(Long playerId, Long guildId);
}
