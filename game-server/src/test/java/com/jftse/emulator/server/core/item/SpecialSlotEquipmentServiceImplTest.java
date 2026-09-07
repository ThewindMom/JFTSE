package com.jftse.emulator.server.core.item;

import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.player.SpecialSlotEquipment;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.player.SpecialSlotEquipmentRepository;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.PocketService;
import com.jftse.server.core.service.impl.SpecialSlotEquipmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecialSlotEquipmentServiceImplTest {
    @Mock
    private SpecialSlotEquipmentRepository specialSlotEquipmentRepository;
    @Mock
    private PlayerPocketService playerPocketService;
    @Mock
    private PocketService pocketService;
    @InjectMocks
    private SpecialSlotEquipmentServiceImpl service;

    private Pocket pocket;
    private Player player;
    private SpecialSlotEquipment equipment;

    @BeforeEach
    void setUp() {
        pocket = new Pocket();
        pocket.setId(71L);
        pocket.setBelongings(10);

        equipment = new SpecialSlotEquipment();
        equipment.setId(81L);

        player = new Player();
        player.setPocket(pocket);
        player.setSpecialSlotEquipment(equipment);

        lenient().when(specialSlotEquipmentRepository.findById(equipment.getId())).thenReturn(Optional.of(equipment));
    }

    @Test
    void rejectsMalformedSpecialSlotPayloadsWithoutMutation() {
        assertThrows(IllegalArgumentException.class, () -> service.updateSpecialSlots(player, List.of(1001, 0, 0)));

        verify(specialSlotEquipmentRepository, never()).save(equipment);
        verify(playerPocketService, never()).getItemsAsPocket(anyList(), eq(pocket));
    }

    @Test
    void onlyEquipsOwnedSpecialItemsAndRejectsDuplicatePocketIds() {
        PlayerPocket necklace = item(1001L, 29, EItemCategory.SPECIAL.getName(), 2);
        PlayerPocket clothing = item(1002L, 29, EItemCategory.PARTS.getName(), 1);
        when(playerPocketService.getItemsAsPocket(anyList(), eq(pocket))).thenReturn(List.of(necklace, clothing));

        List<Integer> slots = service.updateSpecialSlots(player, List.of(1001, 1002, 9999, 1001));

        assertEquals(List.of(1001, 0, 0, 0), slots);
        assertEquals(1001, equipment.getSlot1());
        assertEquals(0, equipment.getSlot2());
        assertEquals(0, equipment.getSlot3());
        assertEquals(0, equipment.getSlot4());
    }

    @Test
    void consumesEachEquippedMatchItemOnceAndKeepsNonEmptyStacksEquipped() {
        equipment.setSlot1(1001);
        equipment.setSlot2(1002);
        equipment.setSlot3(0);
        equipment.setSlot4(1001);
        PlayerPocket necklace = item(1001L, 29, EItemCategory.SPECIAL.getName(), 2);
        PlayerPocket earring = item(1002L, 31, EItemCategory.SPECIAL.getName(), 4);
        when(playerPocketService.getItemsAsPocket(anyList(), eq(pocket))).thenReturn(List.of(necklace, earring));
        when(playerPocketService.save(necklace)).thenReturn(necklace);
        when(playerPocketService.save(earring)).thenReturn(earring);

        var result = service.consumeMatchStatItems(player, GameMode.BATTLE);

        assertEquals(1, necklace.getItemCount());
        assertEquals(3, earring.getItemCount());
        assertEquals(List.of(necklace, earring), result.updatedItems());
        assertTrue(result.removedItemIds().isEmpty());
        assertEquals(List.of(1001, 1002, 0, 1001), result.specialSlots());
        verify(playerPocketService).save(necklace);
        verify(playerPocketService).save(earring);
        verify(playerPocketService, never()).remove(1001L);
        verify(pocketService, never()).decrementPocketBelongings(pocket);
    }

    @Test
    void removesExhaustedMatchItemAndClearsEverySlotContainingIt() {
        equipment.setSlot1(1003);
        equipment.setSlot2(0);
        equipment.setSlot3(1003);
        equipment.setSlot4(0);
        PlayerPocket earring = item(1003L, 37, EItemCategory.SPECIAL.getName(), 1);
        when(playerPocketService.getItemsAsPocket(anyList(), eq(pocket))).thenReturn(List.of(earring));

        var result = service.consumeMatchStatItems(player, GameMode.GUARDIAN);

        assertTrue(result.updatedItems().isEmpty());
        assertEquals(List.of(1003L), result.removedItemIds());
        assertEquals(List.of(0, 0, 0, 0), result.specialSlots());
        verify(playerPocketService).remove(1003L);
        verify(pocketService).decrementPocketBelongings(pocket);
        verify(specialSlotEquipmentRepository).save(equipment);
    }

    @Test
    void basicModeConsumesEarringsButNotBattleOnlyHpNecklaces() {
        equipment.setSlot1(1001);
        equipment.setSlot2(1002);
        equipment.setSlot3(0);
        equipment.setSlot4(0);
        PlayerPocket necklace = item(1001L, 27, EItemCategory.SPECIAL.getName(), 2);
        PlayerPocket earring = item(1002L, 30, EItemCategory.SPECIAL.getName(), 2);
        when(playerPocketService.getItemsAsPocket(anyList(), eq(pocket))).thenReturn(List.of(necklace, earring));
        when(playerPocketService.save(earring)).thenReturn(earring);

        var result = service.consumeMatchStatItems(player, GameMode.BASIC);

        assertEquals(2, necklace.getItemCount());
        assertEquals(1, earring.getItemCount());
        assertEquals(List.of(earring), result.updatedItems());
        verify(playerPocketService, never()).save(necklace);
        verify(playerPocketService).save(earring);
    }

    private PlayerPocket item(long id, int itemIndex, String category, int count) {
        PlayerPocket item = new PlayerPocket();
        item.setId(id);
        item.setPocket(pocket);
        item.setItemIndex(itemIndex);
        item.setCategory(category);
        item.setItemCount(count);
        return item;
    }
}
