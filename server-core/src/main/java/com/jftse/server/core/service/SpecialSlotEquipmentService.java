package com.jftse.server.core.service;

import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.player.SpecialSlotEquipment;
import com.jftse.entities.database.model.pocket.PlayerPocket;

import java.util.List;

public interface SpecialSlotEquipmentService {
    record MatchStatItemUseResult(List<PlayerPocket> updatedItems, List<Long> removedItemIds, List<Integer> specialSlots) {
    }

    SpecialSlotEquipment save(SpecialSlotEquipment specialSlotEquipment);

    SpecialSlotEquipment findById(Long id);

    List<Integer> updateSpecialSlots(Player player, List<Integer> specialSlotItems);

    MatchStatItemUseResult consumeMatchStatItems(Player player, short gameMode);

    List<Integer> getEquippedSpecialSlots(Player player);
}
