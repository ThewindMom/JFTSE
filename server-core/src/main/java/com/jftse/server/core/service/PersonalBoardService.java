package com.jftse.server.core.service;

import com.jftse.entities.database.model.pocket.PlayerPocket;

public interface PersonalBoardService {
    enum UseStatus {
        SUCCESS,
        ITEM_NOT_FOUND,
        NOT_OWNED,
        INVALID_ITEM
    }

    record UseResult(UseStatus status, PlayerPocket item, boolean itemRemoved) {
    }

    UseResult use(Long pocketId, Long playerPocketId);
}
