package com.jftse.server.core.service;

import com.jftse.entities.database.model.player.CardSlotEquipment;
import com.jftse.entities.database.model.player.Player;
import com.jftse.server.core.item.CardStats;

import java.util.List;

public interface CardSlotEquipmentService {
    CardSlotEquipment save(CardSlotEquipment cardSlotEquipment);

    CardSlotEquipment findById(Long id);

    void updateCardSlots(CardSlotEquipment cardSlotEquipment, Integer cardSlotId);

    void updateCardSlots(Player player, List<Integer> cardSlotItems);

    boolean tryUpdateCardSlots(Player player, List<Integer> cardSlotItems);

    CardStats calculateCardStats(Player player);

    List<Integer> getEquippedCardSlots(Player player);
}
