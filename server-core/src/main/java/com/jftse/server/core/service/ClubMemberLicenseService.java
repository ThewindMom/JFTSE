package com.jftse.server.core.service;

import com.jftse.entities.database.model.pocket.PlayerPocket;

public interface ClubMemberLicenseService {
    enum UseStatus {
        SUCCESS,
        ITEM_NOT_FOUND,
        NOT_OWNED,
        INVALID_ITEM,
        NOT_CLUB_MEMBER,
        NOT_CLUB_MASTER,
        GUILD_LEVEL_TOO_LOW,
        CAPACITY_LIMIT_REACHED
    }

    record UseResult(UseStatus status, PlayerPocket item, boolean itemRemoved, byte maxMemberCount) {
    }

    UseResult use(Long playerId, Long pocketId, Long playerPocketId);
}
