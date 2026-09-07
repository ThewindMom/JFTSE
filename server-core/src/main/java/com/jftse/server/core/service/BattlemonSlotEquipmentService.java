package com.jftse.server.core.service;

import com.jftse.entities.database.model.player.BattlemonSlotEquipment;
import com.jftse.entities.database.model.player.Player;

import java.util.List;

public interface BattlemonSlotEquipmentService {
    BattlemonSlotEquipment save(BattlemonSlotEquipment battlemonSlotEquipment);

    BattlemonSlotEquipment findById(Long id);

    BattlemonSlotEquipment getOrCreate(Player player);

    BattlemonSlotEquipment updateBattlemonSlots(Player player, List<Integer> battlemonSlotItems);
}
