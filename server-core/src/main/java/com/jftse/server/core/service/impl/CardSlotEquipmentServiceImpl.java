package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.item.ItemCard;
import com.jftse.entities.database.model.player.CardSlotEquipment;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.repository.item.ItemCardRepository;
import com.jftse.entities.database.repository.player.CardSlotEquipmentRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.server.core.item.CardStats;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.service.CardSlotEquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CardSlotEquipmentServiceImpl implements CardSlotEquipmentService {
    private final CardSlotEquipmentRepository cardSlotEquipmentRepository;
    private final PlayerPocketRepository playerPocketRepository;
    private final ItemCardRepository itemCardRepository;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CardSlotEquipment save(CardSlotEquipment cardSlotEquipment) {
        return cardSlotEquipmentRepository.save(cardSlotEquipment);
    }

    @Override
    @Transactional(readOnly = true)
    public CardSlotEquipment findById(Long id) {
        Optional<CardSlotEquipment> cardSlotEquipment = cardSlotEquipmentRepository.findById(id);
        return cardSlotEquipment.orElse(null);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateCardSlots(CardSlotEquipment cardSlotEquipment, Integer cardSlotId) {
        cardSlotEquipment = findById(cardSlotEquipment.getId());

        if (cardSlotEquipment.getSlot1().equals(cardSlotId))
            cardSlotEquipment.setSlot1(0);
        else if (cardSlotEquipment.getSlot2().equals(cardSlotId))
            cardSlotEquipment.setSlot2(0);
        else if (cardSlotEquipment.getSlot3().equals(cardSlotId))
            cardSlotEquipment.setSlot3(0);
        else if (cardSlotEquipment.getSlot4().equals(cardSlotId))
            cardSlotEquipment.setSlot4(0);

        save(cardSlotEquipment);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateCardSlots(Player player, List<Integer> cardSlotItems) {
        tryUpdateCardSlots(player, cardSlotItems);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean tryUpdateCardSlots(Player player, List<Integer> cardSlotItems) {
        if (player == null || player.getPocket() == null || player.getCardSlotEquipment() == null
                || cardSlotItems == null || cardSlotItems.size() != 4 || cardSlotItems.stream().anyMatch(id -> id == null || id < 0))
            return false;

        CardSlotEquipment equipment = cardSlotEquipmentRepository
                .findByIdForUpdate(player.getCardSlotEquipment().getId()).orElse(null);
        if (equipment == null)
            return false;

        List<Long> requestedIds = cardSlotItems.stream().filter(id -> id != 0).map(Integer::longValue).toList();
        if (new HashSet<>(requestedIds).size() != requestedIds.size())
            return false;

        List<PlayerPocket> pockets = requestedIds.isEmpty()
                ? List.of()
                : playerPocketRepository.findAllByPocketAndIdInForUpdate(player.getPocket(), requestedIds);
        Map<Long, PlayerPocket> pocketsById = pockets.stream().collect(Collectors.toMap(PlayerPocket::getId, item -> item));
        if (pocketsById.size() != requestedIds.size() || requestedIds.stream().anyMatch(id -> {
            PlayerPocket item = pocketsById.get(id);
            return item == null || item.getItemCount() == null || item.getItemCount() <= 0
                    || !EItemCategory.CARD.getName().equals(item.getCategory()) || item.getItemIndex() == null;
        }))
            return false;

        List<Integer> itemIndexes = requestedIds.stream()
                .map(pocketsById::get)
                .map(PlayerPocket::getItemIndex)
                .distinct()
                .toList();
        List<ItemCard> cards = itemCardRepository.findAllByItemIndexIn(itemIndexes);
        Map<Integer, ItemCard> cardsByIndex = cards.stream()
                .collect(Collectors.toMap(ItemCard::getItemIndex, card -> card));
        if (itemIndexes.stream().anyMatch(index -> {
            ItemCard card = cardsByIndex.get(index);
            return card == null || card.getItemType() == null || card.getAbilityPower() == null
                    || !CardStats.supports(card.getItemType());
        }))
            return false;

        equipment.setSlot1(cardSlotItems.get(0));
        equipment.setSlot2(cardSlotItems.get(1));
        equipment.setSlot3(cardSlotItems.get(2));
        equipment.setSlot4(cardSlotItems.get(3));
        save(equipment);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public CardStats calculateCardStats(Player player) {
        List<Integer> slots = getEquippedCardSlots(player);
        List<Long> ids = slots.stream().filter(id -> id != null && id > 0).map(Integer::longValue).toList();
        if (ids.isEmpty())
            return CardStats.zero();

        List<PlayerPocket> pockets = playerPocketRepository.findAllByPocketAndIdIn(player.getPocket(), ids).stream()
                .filter(item -> EItemCategory.CARD.getName().equals(item.getCategory()))
                .filter(item -> item.getItemCount() != null && item.getItemCount() > 0)
                .toList();
        List<ItemCard> cards = itemCardRepository.findAllByItemIndexIn(
                pockets.stream().map(PlayerPocket::getItemIndex).distinct().toList());
        Map<Integer, ItemCard> cardsByIndex = new HashMap<>();
        cards.forEach(card -> cardsByIndex.put(card.getItemIndex(), card));

        CardStats result = CardStats.zero();
        for (PlayerPocket pocket : pockets) {
            ItemCard card = cardsByIndex.get(pocket.getItemIndex());
            if (card != null && card.getItemType() != null && card.getAbilityPower() != null)
                result = result.add(card.getItemType(), card.getAbilityPower());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> getEquippedCardSlots(Player player) {
        List<Integer> result = new ArrayList<>();

        CardSlotEquipment cardSlotEquipment = findById(player.getCardSlotEquipment().getId());

        result.add(cardSlotEquipment.getSlot1());
        result.add(cardSlotEquipment.getSlot2());
        result.add(cardSlotEquipment.getSlot3());
        result.add(cardSlotEquipment.getSlot4());

        return result;
    }
}
