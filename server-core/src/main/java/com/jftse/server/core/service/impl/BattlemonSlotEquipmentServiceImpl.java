package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.player.BattlemonSlotEquipment;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.repository.player.BattlemonSlotEquipmentRepository;
import com.jftse.entities.database.repository.player.PlayerRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.service.BattlemonSlotEquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BattlemonSlotEquipmentServiceImpl implements BattlemonSlotEquipmentService {
    private static final Set<Integer> REFRESH_ITEM_INDEXES = Set.of(20, 21, 22);
    private static final Set<Integer> FEED_ITEM_INDEXES = Set.of(16, 17, 18, 19);

    private final BattlemonSlotEquipmentRepository battlemonSlotEquipmentRepository;
    private final PlayerRepository playerRepository;
    private final PlayerPocketRepository playerPocketRepository;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BattlemonSlotEquipment save(BattlemonSlotEquipment battlemonSlotEquipment) {
        return battlemonSlotEquipmentRepository.save(battlemonSlotEquipment);
    }

    @Override
    @Transactional(readOnly = true)
    public BattlemonSlotEquipment findById(Long id) {
        Optional<BattlemonSlotEquipment> battlemonSlotEquipment = battlemonSlotEquipmentRepository.findById(id);
        return battlemonSlotEquipment.orElse(null);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BattlemonSlotEquipment getOrCreate(Player player) {
        Player managedPlayer = playerRepository.findByIdForUpdate(player.getId()).orElseThrow();
        BattlemonSlotEquipment equipment = getOrCreateEquipment(managedPlayer);
        return applyValidatedSlots(managedPlayer, equipment,
                Arrays.asList(equipment.getSlot1(), equipment.getSlot2()));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BattlemonSlotEquipment updateBattlemonSlots(Player player, List<Integer> battlemonSlotItems) {
        Player managedPlayer = playerRepository.findByIdForUpdate(player.getId()).orElseThrow();
        BattlemonSlotEquipment equipment = getOrCreateEquipment(managedPlayer);
        if (battlemonSlotItems == null || battlemonSlotItems.size() != 2) {
            return applyValidatedSlots(managedPlayer, equipment,
                    Arrays.asList(equipment.getSlot1(), equipment.getSlot2()));
        }
        return applyValidatedSlots(managedPlayer, equipment, battlemonSlotItems);
    }

    private BattlemonSlotEquipment getOrCreateEquipment(Player player) {
        BattlemonSlotEquipment equipment = player.getBattlemonSlotEquipment();
        if (equipment == null) {
            equipment = battlemonSlotEquipmentRepository.save(new BattlemonSlotEquipment());
            player.setBattlemonSlotEquipment(equipment);
            playerRepository.save(player);
        }
        return equipment;
    }

    private BattlemonSlotEquipment applyValidatedSlots(Player player, BattlemonSlotEquipment equipment,
                                                         List<Integer> requestedSlots) {
        List<Long> requestedIds = requestedSlots.stream()
                .filter(id -> id != null && id > 0)
                .map(Integer::longValue)
                .distinct()
                .toList();
        Map<Long, PlayerPocket> ownedItems = requestedIds.isEmpty()
                ? Map.of()
                : playerPocketRepository.findAllByPocketAndIdIn(player.getPocket(), requestedIds)
                        .stream()
                        .collect(Collectors.toMap(PlayerPocket::getId, Function.identity()));

        int refreshSlot = validatedSlot(requestedSlots.get(0), ownedItems, REFRESH_ITEM_INDEXES);
        int feedSlot = validatedSlot(requestedSlots.get(1), ownedItems, FEED_ITEM_INDEXES);
        if (!Objects.equals(equipment.getSlot1(), refreshSlot) ||
                !Objects.equals(equipment.getSlot2(), feedSlot)) {
            equipment.setSlot1(refreshSlot);
            equipment.setSlot2(feedSlot);
            equipment = battlemonSlotEquipmentRepository.save(equipment);
        }
        return equipment;
    }

    private int validatedSlot(Integer requestedId, Map<Long, PlayerPocket> ownedItems,
                              Set<Integer> allowedItemIndexes) {
        if (requestedId == null || requestedId <= 0) {
            return 0;
        }
        PlayerPocket item = ownedItems.get(requestedId.longValue());
        if (item == null || !EItemCategory.PET_ITEM.getName().equals(item.getCategory()) ||
                item.getItemCount() == null || item.getItemCount() <= 0 ||
                !allowedItemIndexes.contains(item.getItemIndex())) {
            return 0;
        }
        return requestedId;
    }
}
