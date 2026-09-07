package com.jftse.emulator.server.core.card;

import com.jftse.entities.database.model.item.ItemCard;
import com.jftse.entities.database.model.player.CardSlotEquipment;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.item.ItemCardRepository;
import com.jftse.entities.database.repository.player.CardSlotEquipmentRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.server.core.item.CardStats;
import com.jftse.server.core.service.impl.CardSlotEquipmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardSlotEquipmentServiceTest {
    @Mock
    private CardSlotEquipmentRepository equipmentRepository;
    @Mock
    private PlayerPocketRepository pocketRepository;
    @Mock
    private ItemCardRepository cardRepository;
    @InjectMocks
    private CardSlotEquipmentServiceImpl service;

    private Player player;
    private CardSlotEquipment equipment;

    @BeforeEach
    void setUp() {
        Pocket pocket = new Pocket();
        pocket.setId(10L);
        equipment = new CardSlotEquipment();
        equipment.setId(20L);
        player = new Player();
        player.setPocket(pocket);
        player.setCardSlotEquipment(equipment);
        lenient().when(equipmentRepository.findById(20L)).thenReturn(Optional.of(equipment));
        lenient().when(equipmentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(equipment));
    }

    @Test
    void rejectsAClientOwnedNonCardAndKeepsAuthoritativeSlots() {
        PlayerPocket item = pocketItem(101L, "PARTS", 1);
        when(pocketRepository.findAllByPocketAndIdInForUpdate(player.getPocket(), List.of(101L)))
                .thenReturn(List.of(item));

        assertFalse(service.tryUpdateCardSlots(player, List.of(101, 0, 0, 0)));
        assertEquals(List.of(0, 0, 0, 0), service.getEquippedCardSlots(player));
        verify(equipmentRepository, never()).save(equipment);
    }

    @Test
    void rejectsDuplicateCardPocketIds() {
        assertFalse(service.tryUpdateCardSlots(player, List.of(101, 101, 0, 0)));
        verify(equipmentRepository, never()).save(equipment);
    }

    @Test
    void aggregatesNativeCardStatsAndElementOrder() {
        PlayerPocket strength = pocketItem(101L, "CARD", 1);
        PlayerPocket hp = pocketItem(102L, "CARD", 13);
        PlayerPocket earthAttack = pocketItem(103L, "CARD", 28);
        PlayerPocket fireDefense = pocketItem(104L, "CARD", 37);
        when(pocketRepository.findAllByPocketAndIdInForUpdate(
                player.getPocket(), List.of(101L, 102L, 103L, 104L)))
                .thenReturn(List.of(strength, hp, earthAttack, fireDefense));
        when(pocketRepository.findAllByPocketAndIdIn(
                player.getPocket(), List.of(101L, 102L, 103L, 104L)))
                .thenReturn(List.of(strength, hp, earthAttack, fireDefense));
        when(cardRepository.findAllByItemIndexIn(List.of(1, 13, 28, 37))).thenReturn(List.of(
                card(1, "STR", 3),
                card(13, "HP", 15),
                card(28, "ATT_EARTH", 3),
                card(37, "DEF_FIRE", 3)
        ));

        assertTrue(service.tryUpdateCardSlots(player, List.of(101, 102, 103, 104)));
        CardStats stats = service.calculateCardStats(player);

        assertEquals(15, stats.hp());
        assertEquals(3, stats.strength());
        assertEquals(List.of(3, 0, 0, 0, 0, 0, 0, 0), stats.attackElements());
        assertEquals(List.of(0, 0, 0, 3, 0, 0, 0, 0), stats.defenseElements());
    }

    @Test
    void saturatesCardArithmeticBeforeNativeByteSerialization() {
        CardStats nearMaximum = new CardStats(
                Integer.MAX_VALUE - 2, 254, 254, 254, 254,
                List.of(254, 0, 0, 0, 0, 0, 0, 0),
                List.of(0, 0, 0, 254, 0, 0, 0, 0));

        CardStats saturated = nearMaximum
                .add("HP", 10)
                .add("STR", 10)
                .add("ATT_EARTH", 10)
                .add("DEF_FIRE", 10);

        assertEquals(Integer.MAX_VALUE, saturated.hp());
        assertEquals(255, saturated.strength());
        assertEquals(255, saturated.attackElements().get(0));
        assertEquals(255, saturated.defenseElements().get(3));
        assertEquals(0, CardStats.zero().add("STA", -1).stamina());
        assertEquals(0, CardStats.zero().add("ATT_WIND", -1).attackElements().get(1));
    }

    private PlayerPocket pocketItem(long id, String category, int itemIndex) {
        PlayerPocket item = new PlayerPocket();
        item.setId(id);
        item.setPocket(player.getPocket());
        item.setCategory(category);
        item.setItemIndex(itemIndex);
        item.setItemCount(1);
        return item;
    }

    private ItemCard card(int itemIndex, String type, int power) {
        ItemCard card = new ItemCard();
        card.setItemIndex(itemIndex);
        card.setItemType(type);
        card.setAbilityPower(power);
        return card;
    }
}
