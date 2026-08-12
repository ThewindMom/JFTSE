package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.player.SpecialSlotEquipment;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.player.SpecialSlotEquipmentRepository;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.item.SpecialItemEffects;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.PocketService;
import com.jftse.server.core.service.SpecialSlotEquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SpecialSlotEquipmentServiceImpl implements SpecialSlotEquipmentService {
    private final SpecialSlotEquipmentRepository specialSlotEquipmentRepository;
    private final PlayerPocketService playerPocketService;
    private final PocketService pocketService;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SpecialSlotEquipment save(SpecialSlotEquipment specialSlotEquipment) {
        return specialSlotEquipmentRepository.save(specialSlotEquipment);
    }

    @Override
    @Transactional(readOnly = true)
    public SpecialSlotEquipment findById(Long id) {
        Optional<SpecialSlotEquipment> specialSlotEquipment = specialSlotEquipmentRepository.findById(id);
        return specialSlotEquipment.orElse(null);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Integer> updateSpecialSlots(Player player, List<Integer> specialSlotItems) {
        if (specialSlotItems.size() != 4)
            throw new IllegalArgumentException("Special slot equipment requires exactly four slots.");

        Pocket pocket = player.getPocket();
        SpecialSlotEquipment specialSlotEquipment = findById(player.getSpecialSlotEquipment().getId());

        List<PlayerPocket> playerPockets = playerPocketService.getItemsAsPocket(
                List.of(
                        Long.valueOf(specialSlotItems.get(0)),
                        Long.valueOf(specialSlotItems.get(1)),
                        Long.valueOf(specialSlotItems.get(2)),
                        Long.valueOf(specialSlotItems.get(3))
                ),
                pocket
        );

        Set<Integer> equippedItemIds = new HashSet<>();
        for (int i = 0; i < specialSlotItems.size(); i++) {
            Integer itemId = specialSlotItems.get(i);
            PlayerPocket item = playerPockets.stream()
                    .filter(pp -> pp.getId().intValue() == itemId
                            && EItemCategory.SPECIAL.getName().equals(pp.getCategory())
                            && equippedItemIds.add(itemId))
                    .findFirst()
                    .orElse(null);

            int slotValue = item == null ? 0 : item.getId().intValue();

            switch (i) {
                case 0 -> specialSlotEquipment.setSlot1(slotValue);
                case 1 -> specialSlotEquipment.setSlot2(slotValue);
                case 2 -> specialSlotEquipment.setSlot3(slotValue);
                case 3 -> specialSlotEquipment.setSlot4(slotValue);
            }
        }

        save(specialSlotEquipment);
        return List.of(
                specialSlotEquipment.getSlot1(),
                specialSlotEquipment.getSlot2(),
                specialSlotEquipment.getSlot3(),
                specialSlotEquipment.getSlot4()
        );
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MatchStatItemUseResult consumeMatchStatItems(Player player, short gameMode) {
        Pocket pocket = player.getPocket();
        SpecialSlotEquipment equipment = specialSlotEquipmentRepository.findById(player.getSpecialSlotEquipment().getId()).orElseThrow();
        List<Integer> specialSlots = new ArrayList<>(List.of(
                equipment.getSlot1(),
                equipment.getSlot2(),
                equipment.getSlot3(),
                equipment.getSlot4()
        ));
        List<Long> equippedItemIds = specialSlots.stream()
                .filter(id -> id > 0)
                .distinct()
                .map(Integer::longValue)
                .toList();

        if (equippedItemIds.isEmpty())
            return new MatchStatItemUseResult(List.of(), List.of(), List.copyOf(specialSlots));

        List<PlayerPocket> equippedItems = playerPocketService.getItemsAsPocket(equippedItemIds, pocket);
        List<PlayerPocket> updatedItems = new ArrayList<>();
        List<Long> removedItemIds = new ArrayList<>();

        for (Long itemId : equippedItemIds) {
            PlayerPocket item = equippedItems.stream()
                    .filter(pp -> pp.getId().equals(itemId)
                            && EItemCategory.SPECIAL.getName().equals(pp.getCategory())
                            && SpecialItemEffects.isActiveInMode(pp.getItemIndex(), gameMode))
                    .findFirst()
                    .orElse(null);
            if (item == null)
                continue;

            int remainingCount = item.getItemCount() - 1;
            if (remainingCount > 0) {
                item.setItemCount(remainingCount);
                updatedItems.add(playerPocketService.save(item));
                continue;
            }

            playerPocketService.remove(itemId);
            pocketService.decrementPocketBelongings(pocket);
            removedItemIds.add(itemId);
            specialSlots.replaceAll(slotItemId -> slotItemId.longValue() == itemId ? 0 : slotItemId);
        }

        if (!removedItemIds.isEmpty()) {
            equipment.setSlot1(specialSlots.get(0));
            equipment.setSlot2(specialSlots.get(1));
            equipment.setSlot3(specialSlots.get(2));
            equipment.setSlot4(specialSlots.get(3));
            specialSlotEquipmentRepository.save(equipment);
        }

        return new MatchStatItemUseResult(
                List.copyOf(updatedItems),
                List.copyOf(removedItemIds),
                List.copyOf(specialSlots)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> getEquippedSpecialSlots(Player player) {
        List<Integer> result = new ArrayList<>();

        SpecialSlotEquipment specialSlotEquipment = findById(player.getSpecialSlotEquipment().getId());

        result.add(specialSlotEquipment.getSlot1());
        result.add(specialSlotEquipment.getSlot2());
        result.add(specialSlotEquipment.getSlot3());
        result.add(specialSlotEquipment.getSlot4());

        return result;
    }
}
