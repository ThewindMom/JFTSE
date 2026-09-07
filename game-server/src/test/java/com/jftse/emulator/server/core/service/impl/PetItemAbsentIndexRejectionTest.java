package com.jftse.emulator.server.core.service.impl;

import com.jftse.emulator.server.core.service.BattlemonLifecycleService;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.pet.PetStatistic;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.repository.pet.PetRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.entities.database.repository.pocket.PocketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Retail Item_PetItem.set has indices 1-14 and 16-23 only. Index 15 is absent.
 * The server must reject it unused and must not consume the stack.
 */
class PetItemAbsentIndexRejectionTest {
    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");

    private PetRepository petRepository;
    private PlayerPocketRepository playerPocketRepository;
    private BattlemonLifecycleService service;

    @BeforeEach
    void setUp() {
        petRepository = mock(PetRepository.class);
        playerPocketRepository = mock(PlayerPocketRepository.class);
        service = new BattlemonLifecycleServiceImpl(
                petRepository, playerPocketRepository, mock(PocketRepository.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rejectsAbsentRetailIndex15AndUnknownIndicesWithoutConsumption() {
        Pet pet = pet(2L, (byte) 1);
        pet.setStrength((byte) 7);
        when(petRepository.findByIdAndPlayerIdForUpdate(2L, 5L)).thenReturn(Optional.of(pet));

        for (int missingIndex : new int[] {15, 0, 24}) {
            PlayerPocket item = item(24L, "PET_ITEM", missingIndex, 3);
            when(playerPocketRepository.findByIdAndPocketIdForUpdate(24L, 5L)).thenReturn(Optional.of(item));

            assertFalse(service.usePetItem(5L, 5L, 2L, 24L).successful());
            assertEquals(7, pet.getStrength().intValue());
            assertEquals(3, item.getItemCount());
        }
        verify(petRepository, never()).save(pet);
        verify(playerPocketRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static Pet pet(long id, byte type) {
        Player player = new Player();
        player.setId(5L);
        Pet pet = new Pet();
        pet.setId(id);
        pet.setPlayer(player);
        pet.setPetStatistic(new PetStatistic());
        pet.setType(type);
        pet.setName("Pet");
        pet.setLevel(1);
        pet.setExpPoints(0);
        pet.setHp(200);
        pet.setStrength((byte) 0);
        pet.setStamina((byte) 0);
        pet.setDexterity((byte) 0);
        pet.setWillpower((byte) 0);
        pet.setHunger(40);
        pet.setEnergy(30);
        pet.setLifeMax(120);
        pet.setValidUntil(Date.from(NOW.plus(Duration.ofDays(30))));
        pet.setAlive(true);
        return pet;
    }

    private static PlayerPocket item(long id, String category, int itemIndex, int count) {
        Pocket pocket = new Pocket();
        pocket.setId(5L);
        pocket.setBelongings(12);
        PlayerPocket item = new PlayerPocket();
        item.setId(id);
        item.setPocket(pocket);
        item.setCategory(category);
        item.setItemIndex(itemIndex);
        item.setUseType("Count");
        item.setItemCount(count);
        return item;
    }
}
