package com.jftse.emulator.server.core.client;

import com.jftse.entities.database.model.player.BattlemonSlotEquipment;
import com.jftse.entities.database.model.player.Player;

import java.util.List;

public record EquippedPetSlots(long id, int slot1, int slot2) {
    public static EquippedPetSlots defaultSlots() {
        return new EquippedPetSlots(0L, 0, 0);
    }

    public static EquippedPetSlots of(Player player) {
        BattlemonSlotEquipment equipment = player.getBattlemonSlotEquipment();
        return equipment == null ? defaultSlots() : of(equipment);
    }

    public static EquippedPetSlots of(BattlemonSlotEquipment equipment) {
        return new EquippedPetSlots(equipment.getId(), equipment.getSlot1(), equipment.getSlot2());
    }

    public List<Integer> toList() {
        return List.of(slot1, slot2);
    }
}
