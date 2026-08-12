package com.jftse.server.core.service;

import com.jftse.entities.database.model.emblem.PlayerEmblemEquipment;
import com.jftse.entities.database.model.player.Player;

public interface PlayerEmblemEquipmentService {
    PlayerEmblemEquipment createIfAbsent(Player player);

    PlayerEmblemEquipment findByPlayerId(Long playerId);
}
