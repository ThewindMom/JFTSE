package com.jftse.server.core.service;

import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.entities.database.model.pocket.PlayerPocket;

public interface ContractWithGuardianService {
    enum UseStatus {
        SUCCESS,
        ITEM_NOT_FOUND,
        NOT_OWNED,
        INVALID_ITEM,
        STATISTIC_NOT_FOUND,
        POCKET_NOT_FOUND
    }

    record UseResult(UseStatus status, PlayerPocket item, boolean itemRemoved,
                     PlayerStatistic statistic) {
    }

    UseResult use(Long playerStatisticId, Long pocketId, Long playerPocketId);
}
