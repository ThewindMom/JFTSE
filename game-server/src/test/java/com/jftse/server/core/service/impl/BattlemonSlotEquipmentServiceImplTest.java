package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.player.BattlemonSlotEquipment;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.player.BattlemonSlotEquipmentRepository;
import com.jftse.entities.database.repository.player.PlayerRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.server.core.item.EItemCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BattlemonSlotEquipmentServiceImplTest {
    private BattlemonSlotEquipmentRepository equipmentRepository;
    private PlayerRepository playerRepository;
    private PlayerPocketRepository playerPocketRepository;
    private BattlemonSlotEquipmentServiceImpl service;
    private Player player;
    private Pocket pocket;

    @BeforeEach
    void setUp() {
        equipmentRepository = mock(BattlemonSlotEquipmentRepository.class);
        playerRepository = mock(PlayerRepository.class);
        playerPocketRepository = mock(PlayerPocketRepository.class);
        service = new BattlemonSlotEquipmentServiceImpl(
                equipmentRepository, playerRepository, playerPocketRepository);

        pocket = new Pocket();
        pocket.setId(4L);
        player = new Player();
        player.setId(4L);
        player.setPocket(pocket);
        when(playerRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(player));
        when(equipmentRepository.save(any(BattlemonSlotEquipment.class))).thenAnswer(invocation -> {
            BattlemonSlotEquipment equipment = invocation.getArgument(0);
            if (equipment.getId() == null) {
                equipment.setId(7L);
            }
            return equipment;
        });
    }

    @Test
    void createsMissingEquipmentForExistingPlayers() {
        BattlemonSlotEquipment equipment = service.getOrCreate(player);

        assertEquals(7L, equipment.getId());
        assertSame(equipment, player.getBattlemonSlotEquipment());
        verify(playerRepository).save(player);
    }

    @Test
    void clearsMissingAndDepletedStoredPocketRowsWhenSlotsAreLoaded() {
        BattlemonSlotEquipment equipment = equipment();
        equipment.setSlot1(30);
        equipment.setSlot2(26);
        PlayerPocket depletedAcornPie = pocketItem(26L, EItemCategory.PET_ITEM.getName(), 16, 0);
        when(playerPocketRepository.findAllByPocketAndIdIn(any(Pocket.class), anyList()))
                .thenReturn(List.of(depletedAcornPie));

        BattlemonSlotEquipment result = service.getOrCreate(player);

        assertEquals(List.of(0, 0), List.of(result.getSlot1(), result.getSlot2()));
        verify(equipmentRepository).save(equipment);
    }

    @Test
    void canonicalizesNullStoredSlotsWithoutInventoryLookup() {
        BattlemonSlotEquipment equipment = equipment();
        equipment.setSlot1(null);
        equipment.setSlot2(null);

        BattlemonSlotEquipment result = service.getOrCreate(player);

        assertEquals(List.of(0, 0), List.of(result.getSlot1(), result.getSlot2()));
        verifyNoInteractions(playerPocketRepository);
    }

    @Test
    void acceptsOwnedEnergyAndFoodPocketRowsInTheirCapturedSlots() {
        BattlemonSlotEquipment equipment = equipment();
        PlayerPocket cherryJuice = pocketItem(30L, EItemCategory.PET_ITEM.getName(), 20, 1);
        PlayerPocket acornPie = pocketItem(26L, EItemCategory.PET_ITEM.getName(), 16, 1);
        when(playerPocketRepository.findAllByPocketAndIdIn(any(Pocket.class), anyList()))
                .thenReturn(List.of(cherryJuice, acornPie));

        BattlemonSlotEquipment result = service.updateBattlemonSlots(player, List.of(30, 26));

        assertSame(equipment, result);
        assertEquals(30, result.getSlot1());
        assertEquals(26, result.getSlot2());
    }

    @Test
    void clearsEmptyAndNegativeSlotValuesWithoutInventoryLookup() {
        BattlemonSlotEquipment equipment = equipment();
        equipment.setSlot1(30);
        equipment.setSlot2(26);

        BattlemonSlotEquipment result = service.updateBattlemonSlots(player, List.of(0, -1));

        assertEquals(List.of(0, 0), List.of(result.getSlot1(), result.getSlot2()));
        verifyNoInteractions(playerPocketRepository);
    }

    @Test
    void malformedRequestsKeepValidatedCanonicalSlots() {
        BattlemonSlotEquipment equipment = equipment();
        equipment.setSlot1(30);
        equipment.setSlot2(26);
        PlayerPocket cherryJuice = pocketItem(30L, EItemCategory.PET_ITEM.getName(), 20, 1);
        PlayerPocket acornPie = pocketItem(26L, EItemCategory.PET_ITEM.getName(), 16, 1);
        when(playerPocketRepository.findAllByPocketAndIdIn(any(Pocket.class), anyList()))
                .thenReturn(List.of(cherryJuice, acornPie));

        BattlemonSlotEquipment result = service.updateBattlemonSlots(player, List.of(30));

        assertEquals(List.of(30, 26), List.of(result.getSlot1(), result.getSlot2()));
    }

    @Test
    void rejectsSwappedSlotsPetCharactersEmptyStacksAndForeignRows() {
        BattlemonSlotEquipment equipment = equipment();
        equipment.setSlot1(30);
        equipment.setSlot2(26);
        PlayerPocket cherryJuice = pocketItem(30L, EItemCategory.PET_ITEM.getName(), 20, 1);
        PlayerPocket acornPie = pocketItem(26L, EItemCategory.PET_ITEM.getName(), 16, 1);
        PlayerPocket pikaro = pocketItem(25L, EItemCategory.PET_CHAR.getName(), 1, 1);
        PlayerPocket emptyStack = pocketItem(31L, EItemCategory.PET_ITEM.getName(), 20, 0);
        when(playerPocketRepository.findAllByPocketAndIdIn(any(Pocket.class), anyList()))
                .thenReturn(List.of(cherryJuice, acornPie, pikaro, emptyStack));

        BattlemonSlotEquipment swapped = service.updateBattlemonSlots(player, List.of(26, 30));
        assertEquals(List.of(0, 0), List.of(swapped.getSlot1(), swapped.getSlot2()));

        BattlemonSlotEquipment forged = service.updateBattlemonSlots(player, List.of(31, 25));
        assertEquals(List.of(0, 0), List.of(forged.getSlot1(), forged.getSlot2()));

        BattlemonSlotEquipment foreign = service.updateBattlemonSlots(player, List.of(99, 98));
        assertEquals(List.of(0, 0), List.of(foreign.getSlot1(), foreign.getSlot2()));
    }

    private BattlemonSlotEquipment equipment() {
        BattlemonSlotEquipment equipment = new BattlemonSlotEquipment();
        equipment.setId(7L);
        player.setBattlemonSlotEquipment(equipment);
        return equipment;
    }

    private PlayerPocket pocketItem(long id, String category, int itemIndex, int itemCount) {
        PlayerPocket item = new PlayerPocket();
        item.setId(id);
        item.setPocket(pocket);
        item.setCategory(category);
        item.setItemIndex(itemIndex);
        item.setItemCount(itemCount);
        return item;
    }
}
