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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BattlemonLifecycleServiceImplTest {
    private PetRepository petRepository;
    private PlayerPocketRepository playerPocketRepository;
    private PocketRepository pocketRepository;
    private BattlemonLifecycleService service;

    @BeforeEach
    void setUp() {
        petRepository = mock(PetRepository.class);
        playerPocketRepository = mock(PlayerPocketRepository.class);
        pocketRepository = mock(PocketRepository.class);
        service = new BattlemonLifecycleServiceImpl(
                petRepository, playerPocketRepository, pocketRepository);
    }

    @Test
    void appliesResourceDefinedPetItemToSelectedOwnedPetAndConsumesOne() {
        Pet pet = pet(2L, (byte) 1);
        pet.setStrength((byte) 15);
        PlayerPocket item = item(24L, "PET_ITEM", 1, 3);
        when(petRepository.findByIdAndPlayerIdForUpdate(2L, 5L)).thenReturn(Optional.of(pet));
        when(playerPocketRepository.findByIdAndPocketIdForUpdate(24L, 5L)).thenReturn(Optional.of(item));

        BattlemonLifecycleService.MutationResult result = service.usePetItem(5L, 5L, 2L, 24L);

        assertTrue(result.successful());
        assertEquals(16, Byte.toUnsignedInt(pet.getStrength()));
        assertEquals(2, result.remainingItemCount());
        assertEquals(2, item.getItemCount());
        verify(petRepository).save(pet);
        verify(playerPocketRepository).save(item);
    }

    @ParameterizedTest
    @MethodSource("statPetItems")
    void appliesEveryStatPetItemIndex(int itemIndex, int strength, int stamina,
                                      int dexterity, int willpower) {
        Pet pet = pet(2L, (byte) 1);
        PlayerPocket item = item(24L, "PET_ITEM", itemIndex, 2);
        when(petRepository.findByIdAndPlayerIdForUpdate(2L, 5L)).thenReturn(Optional.of(pet));
        when(playerPocketRepository.findByIdAndPocketIdForUpdate(24L, 5L)).thenReturn(Optional.of(item));

        assertTrue(service.usePetItem(5L, 5L, 2L, 24L).successful());
        assertEquals(strength, Byte.toUnsignedInt(pet.getStrength()));
        assertEquals(stamina, Byte.toUnsignedInt(pet.getStamina()));
        assertEquals(dexterity, Byte.toUnsignedInt(pet.getDexterity()));
        assertEquals(willpower, Byte.toUnsignedInt(pet.getWillpower()));
        assertEquals(1, item.getItemCount());
    }

    @ParameterizedTest
    @MethodSource("recoveryPetItems")
    void appliesEveryRecoveryPetItemIndex(int itemIndex, int energy, int hunger) {
        Pet pet = pet(2L, (byte) 1);
        PlayerPocket item = item(24L, "PET_ITEM", itemIndex, 2);
        when(petRepository.findByIdAndPlayerIdForUpdate(2L, 5L)).thenReturn(Optional.of(pet));
        when(playerPocketRepository.findByIdAndPocketIdForUpdate(24L, 5L)).thenReturn(Optional.of(item));

        assertTrue(service.usePetItem(5L, 5L, 2L, 24L).successful());
        assertEquals(energy, pet.getEnergy());
        assertEquals(hunger, pet.getHunger());
        assertEquals(1, item.getItemCount());
    }

    @Test
    void permitsSignedDatabaseStatMaximumAndRejectsOverflowWithoutConsumption() {
        Pet pet = pet(2L, (byte) 1);
        pet.setStrength((byte) 126);
        PlayerPocket item = item(24L, "PET_ITEM", 1, 3);
        when(petRepository.findByIdAndPlayerIdForUpdate(2L, 5L)).thenReturn(Optional.of(pet));
        when(playerPocketRepository.findByIdAndPocketIdForUpdate(24L, 5L)).thenReturn(Optional.of(item));

        assertTrue(service.usePetItem(5L, 5L, 2L, 24L).successful());
        assertEquals(127, pet.getStrength().intValue());
        assertEquals(2, item.getItemCount());

        assertFalse(service.usePetItem(5L, 5L, 2L, 24L).successful());
        assertEquals(2, item.getItemCount());
    }

    @Test
    void clampsRecoveryToRetailSpeciesMaximumAndDoesNotConsumeAnIneffectiveItem() {
        Pet pet = pet(2L, (byte) 1);
        pet.setEnergy(90);
        pet.setHunger(150);
        PlayerPocket fanta500 = item(27L, "PET_ITEM", 23, 3);
        when(petRepository.findByIdAndPlayerIdForUpdate(2L, 5L)).thenReturn(Optional.of(pet));
        when(playerPocketRepository.findByIdAndPocketIdForUpdate(27L, 5L)).thenReturn(Optional.of(fanta500));

        BattlemonLifecycleService.MutationResult applied = service.usePetItem(5L, 5L, 2L, 27L);

        assertTrue(applied.successful());
        assertEquals(100, pet.getEnergy());
        assertEquals(150, pet.getHunger());
        assertEquals(2, fanta500.getItemCount());

        when(playerPocketRepository.findByIdAndPocketIdForUpdate(27L, 5L)).thenReturn(Optional.of(fanta500));
        BattlemonLifecycleService.MutationResult ineffective = service.usePetItem(5L, 5L, 2L, 27L);

        assertFalse(ineffective.successful());
        assertEquals(2, fanta500.getItemCount());
    }

    @Test
    void extendsCurrentLifeByOneDayWithoutExceedingLifeMaximum() {
        Pet pet = pet(2L, (byte) 1);
        pet.setLifeMax(120);
        pet.setValidUntil(Date.from(Instant.now().plus(Duration.ofDays(119))));
        PlayerPocket life = item(25L, "PET_ITEM", 13, 3);
        when(petRepository.findByIdAndPlayerIdForUpdate(2L, 5L)).thenReturn(Optional.of(pet));
        when(playerPocketRepository.findByIdAndPocketIdForUpdate(25L, 5L)).thenReturn(Optional.of(life));

        Instant before = pet.getValidUntil().toInstant();
        BattlemonLifecycleService.MutationResult result = service.usePetItem(5L, 5L, 2L, 25L);

        assertTrue(result.successful());
        long extensionHours = Duration.between(before, pet.getValidUntil().toInstant()).toHours();
        assertTrue(extensionHours >= 23 && extensionHours <= 24);
        assertTrue(pet.getValidUntil().toInstant().isBefore(Instant.now().plus(Duration.ofDays(121))));
    }

    @Test
    void rejectsForgedWrongCategoryAndExpiredPetsWithoutConsumption() {
        Pet pet = pet(2L, (byte) 1);
        PlayerPocket forged = item(24L, "SPECIAL", 1, 3);
        when(petRepository.findByIdAndPlayerIdForUpdate(2L, 5L)).thenReturn(Optional.of(pet));
        when(playerPocketRepository.findByIdAndPocketIdForUpdate(24L, 5L)).thenReturn(Optional.of(forged));

        assertFalse(service.usePetItem(5L, 5L, 2L, 24L).successful());

        pet.setValidUntil(Date.from(Instant.now().minusSeconds(1)));
        PlayerPocket validItem = item(25L, "PET_ITEM", 13, 3);
        when(playerPocketRepository.findByIdAndPocketIdForUpdate(25L, 5L)).thenReturn(Optional.of(validItem));
        assertFalse(service.usePetItem(5L, 5L, 2L, 25L).successful());
        verify(playerPocketRepository, never()).save(forged);
        verify(playerPocketRepository, never()).save(validItem);
    }

    @Test
    void renamesLivePetWithOwnedBattlemonTagAndRemovesLastItemAtomically() {
        Pet pet = pet(2L, (byte) 1);
        PlayerPocket tag = item(22L, "SPECIAL", 10, 1);
        when(petRepository.findAllByPlayerIdAndTypeForUpdate(5L, (byte) 1)).thenReturn(List.of(pet));
        when(playerPocketRepository.findByIdAndPocketIdForUpdate(22L, 5L)).thenReturn(Optional.of(tag));
        when(pocketRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(tag.getPocket()));

        BattlemonLifecycleService.MutationResult result =
                service.renamePet(5L, 5L, 22L, (byte) 1, "Renamed");

        assertTrue(result.successful());
        assertEquals("Renamed", pet.getName());
        assertEquals(0, result.remainingItemCount());
        verify(playerPocketRepository).delete(tag);
        verify(pocketRepository).save(tag.getPocket());
        verify(pocketRepository).findByIdForUpdate(5L);
        assertEquals(11, tag.getPocket().getBelongings());
    }

    @Test
    void rejectsNameShorterThanNativeTwoToTwelveCodeUnitLimit() {
        Pet pet = pet(2L, (byte) 1);
        PlayerPocket tag = item(22L, "SPECIAL", 10, 2);
        when(petRepository.findAllByPlayerIdAndTypeForUpdate(5L, (byte) 1)).thenReturn(List.of(pet));
        when(playerPocketRepository.findByIdAndPocketIdForUpdate(22L, 5L)).thenReturn(Optional.of(tag));

        BattlemonLifecycleService.MutationResult result =
                service.renamePet(5L, 5L, 22L, (byte) 1, "Q");

        assertFalse(result.successful());
        assertEquals("Pet", pet.getName());
        assertEquals(2, tag.getItemCount());
        verify(petRepository, never()).save(pet);
        verify(playerPocketRepository, never()).save(tag);
    }

    @Test
    void revivesDeadPetUsingDocumentedResourceMaximaAndCompatibilityExpiryMapping() {
        Pet pet = pet(2L, (byte) 1);
        pet.setAlive(false);
        pet.setEnergy(2);
        pet.setHunger(3);
        pet.setLifeMax(120);
        pet.setValidUntil(Date.from(Instant.now().minus(Duration.ofDays(2))));
        PlayerPocket root = item(21L, "SPECIAL", 9, 3);
        when(petRepository.findAllByPlayerIdAndTypeForUpdate(5L, (byte) 1)).thenReturn(List.of(pet));
        when(playerPocketRepository.findByIdAndPocketIdForUpdate(21L, 5L)).thenReturn(Optional.of(root));

        Instant before = Instant.now();
        BattlemonLifecycleService.MutationResult result = service.revivePet(5L, 5L, 21L, (byte) 1);

        assertTrue(result.successful());
        assertTrue(pet.getAlive());
        assertEquals(100, pet.getEnergy());
        assertEquals(150, pet.getHunger());
        long restoredDays = Duration.between(before, pet.getValidUntil().toInstant()).toDays();
        assertTrue(restoredDays >= 119 && restoredDays <= 120);
        assertEquals(2, root.getItemCount());

        assertFalse(service.revivePet(5L, 5L, 21L, (byte) 1).successful());
        assertEquals(2, root.getItemCount());
    }

    @Test
    void expiryTransitionsPetToDeadAndAllowsRevival() {
        Pet pet = pet(2L, (byte) 1);
        pet.setValidUntil(Date.from(Instant.now().minusSeconds(1)));
        PlayerPocket root = item(21L, "SPECIAL", 9, 3);
        when(petRepository.findAllByPlayerIdAndTypeForUpdate(5L, (byte) 1)).thenReturn(List.of(pet));
        when(playerPocketRepository.findByIdAndPocketIdForUpdate(21L, 5L)).thenReturn(Optional.of(root));

        assertTrue(service.revivePet(5L, 5L, 21L, (byte) 1).successful());
        assertTrue(pet.getAlive());
        assertEquals(2, root.getItemCount());
        assertTrue(pet.getValidUntil().after(new Date()));
    }

    @Test
    void ambiguousDuplicatePetTypeFailsClosedWithoutConsumingRenameItem() {
        Pet first = pet(2L, (byte) 1);
        Pet duplicate = pet(3L, (byte) 1);
        PlayerPocket tag = item(22L, "SPECIAL", 10, 2);
        when(petRepository.findAllByPlayerIdAndTypeForUpdate(5L, (byte) 1))
                .thenReturn(List.of(first, duplicate));
        when(playerPocketRepository.findByIdAndPocketIdForUpdate(22L, 5L)).thenReturn(Optional.of(tag));

        BattlemonLifecycleService.MutationResult result =
                service.renamePet(5L, 5L, 22L, (byte) 1, "Rejected");

        assertFalse(result.successful());
        assertEquals("Pet", first.getName());
        assertEquals("Pet", duplicate.getName());
        assertEquals(2, tag.getItemCount());
        verify(petRepository, never()).save(first);
        verify(petRepository, never()).save(duplicate);
        verify(playerPocketRepository, never()).save(tag);
    }

    @Test
    void preservesUnsignedWireItemIdBitsAndRejectsIdsWiderThanUint32() {
        long unsignedId = 0x8000_0001L;
        Pet pet = pet(2L, (byte) 1);
        PlayerPocket item = item(unsignedId, "PET_ITEM", 1, 2);
        when(petRepository.findByIdAndPlayerIdForUpdate(2L, 5L)).thenReturn(Optional.of(pet));
        when(playerPocketRepository.findByIdAndPocketIdForUpdate(unsignedId, 5L)).thenReturn(Optional.of(item));

        BattlemonLifecycleService.MutationResult applied =
                service.usePetItem(5L, 5L, 2L, unsignedId);

        assertTrue(applied.successful());
        assertEquals(0x8000_0001, applied.itemPocketWireId());

        Pet untouched = pet(3L, (byte) 1);
        when(petRepository.findByIdAndPlayerIdForUpdate(3L, 5L)).thenReturn(Optional.of(untouched));
        BattlemonLifecycleService.MutationResult rejected =
                service.usePetItem(5L, 5L, 3L, 0x1_0000_0000L);

        assertFalse(rejected.successful());
        assertEquals(0, Byte.toUnsignedInt(untouched.getStrength()));
        assertThrows(IllegalArgumentException.class, rejected::itemPocketWireId);
        verify(petRepository, never()).save(untouched);
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
        pet.setLevel((byte) 1);
        pet.setExpPoints(0);
        pet.setHp(200);
        pet.setStrength((byte) 0);
        pet.setStamina((byte) 0);
        pet.setDexterity((byte) 0);
        pet.setWillpower((byte) 0);
        pet.setHunger(40);
        pet.setEnergy(30);
        pet.setLifeMax(120);
        pet.setValidUntil(Date.from(Instant.now().plus(Duration.ofDays(30))));
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

    private static Stream<Arguments> statPetItems() {
        return Stream.of(
                Arguments.of(1, 1, 0, 0, 0), Arguments.of(2, 0, 1, 0, 0),
                Arguments.of(3, 0, 0, 1, 0), Arguments.of(4, 0, 0, 0, 1),
                Arguments.of(5, 2, 0, 0, 0), Arguments.of(6, 0, 2, 0, 0),
                Arguments.of(7, 0, 0, 2, 0), Arguments.of(8, 0, 0, 0, 2),
                Arguments.of(9, 5, 0, 0, 0), Arguments.of(10, 0, 5, 0, 0),
                Arguments.of(11, 0, 0, 5, 0), Arguments.of(12, 0, 0, 0, 5));
    }

    private static Stream<Arguments> recoveryPetItems() {
        return Stream.of(
                Arguments.of(16, 30, 45), Arguments.of(17, 30, 50),
                Arguments.of(18, 30, 60), Arguments.of(19, 30, 90),
                Arguments.of(20, 35, 40), Arguments.of(21, 40, 40),
                Arguments.of(22, 50, 40), Arguments.of(23, 80, 90));
    }
}
