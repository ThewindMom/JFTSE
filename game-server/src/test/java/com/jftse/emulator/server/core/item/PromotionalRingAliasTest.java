package com.jftse.emulator.server.core.item;

import com.jftse.emulator.server.core.client.EquippedSpecialSlots;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.item.BaseItem;
import com.jftse.emulator.server.core.life.item.ItemFactory;
import com.jftse.emulator.server.core.life.item.special.RingOfExp;
import com.jftse.emulator.server.core.life.item.special.RingOfGold;
import com.jftse.emulator.server.core.life.item.special.RingOfWiseman;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.entities.database.model.item.ItemSpecial;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.service.ItemSpecialService;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.PlayerService;
import com.jftse.server.core.service.PocketService;
import com.jftse.server.core.service.SpecialSlotEquipmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionalRingAliasTest {
    @Mock private PlayerPocketService playerPocketService;
    @Mock private ItemSpecialService itemSpecialService;
    @Mock private PocketService pocketService;
    @Mock private PlayerService playerService;
    @Mock private SpecialSlotEquipmentService specialSlotEquipmentService;

    @BeforeEach
    void setUp() {
        ServiceManager manager = new ServiceManager();
        ReflectionTestUtils.setField(manager, "playerPocketService", playerPocketService);
        ReflectionTestUtils.setField(manager, "itemSpecialService", itemSpecialService);
        ReflectionTestUtils.setField(manager, "pocketService", pocketService);
        ReflectionTestUtils.setField(manager, "playerService", playerService);
        ReflectionTestUtils.setField(manager, "specialSlotEquipmentService", specialSlotEquipmentService);
        manager.init();
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(ServiceManager.class, "instance", null);
    }

    @ParameterizedTest
    @MethodSource("promotionalRings")
    void promotionalRingIsRecognizedByFactoryAndEquippedRewardLookup(
            int itemIndex, Class<? extends BaseItem> expectedType, String rewardType) {
        long playerPocketId = 500L + itemIndex;
        Pocket pocket = new Pocket();
        pocket.setId(71L);
        PlayerPocket pocketItem = new PlayerPocket();
        pocketItem.setId(playerPocketId);
        pocketItem.setPocket(pocket);
        pocketItem.setCategory(EItemCategory.SPECIAL.getName());
        pocketItem.setItemIndex(itemIndex);
        pocketItem.setItemCount(2);
        ItemSpecial catalogItem = new ItemSpecial();
        catalogItem.setItemIndex(itemIndex);
        catalogItem.setName("Promotional ring");

        when(playerPocketService.getItemAsPocket(playerPocketId, pocket)).thenReturn(pocketItem);
        when(itemSpecialService.findByItemIndex(itemIndex)).thenReturn(catalogItem);

        BaseItem item = ItemFactory.getItem(playerPocketId, pocket);

        assertInstanceOf(expectedType, item);
        assertEquals(itemIndex, item.getItemIndex());

        FTPlayer player = mock(FTPlayer.class);
        when(player.getPocketId()).thenReturn(71L);
        when(player.getSpecialSlots()).thenReturn(
                EquippedSpecialSlots.of(81L, List.of(Math.toIntExact(playerPocketId), 0, 0, 0)));
        when(playerPocketService.getItemAsPocket(playerPocketId, 71L)).thenReturn(pocketItem);
        RoomPlayer roomPlayer = new RoomPlayer(player);

        boolean equipped = switch (rewardType) {
            case "exp" -> roomPlayer.isRingOfExpEquipped();
            case "gold" -> roomPlayer.isRingOfGoldEquipped();
            case "wiseman" -> roomPlayer.isRingOfWisemanEquipped();
            default -> throw new IllegalArgumentException(rewardType);
        };

        assertTrue(equipped);
    }

    @ParameterizedTest
    @MethodSource("promotionalRings")
    void rewardLookupUsesTheExactEquippedRowWhenAnItemIndexHasMultipleStacks(
            int itemIndex, Class<? extends BaseItem> ignoredType, String rewardType) {
        long unequippedPocketId = 600L + itemIndex;
        long equippedPocketId = 700L + itemIndex;
        PlayerPocket unequippedStack = pocketItem(unequippedPocketId, itemIndex);
        PlayerPocket equippedStack = pocketItem(equippedPocketId, itemIndex);

        FTPlayer player = mock(FTPlayer.class);
        when(player.getPocketId()).thenReturn(71L);
        when(player.getSpecialSlots()).thenReturn(
                EquippedSpecialSlots.of(81L, List.of(Math.toIntExact(equippedPocketId), 0, 0, 0)));
        lenient().when(playerPocketService.getItemAsPocketByItemIndexAndCategoryAndPocket(
                itemIndex - 38, EItemCategory.SPECIAL.getName(), 71L)).thenReturn(null);
        lenient().when(playerPocketService.getItemAsPocketByItemIndexAndCategoryAndPocket(
                itemIndex, EItemCategory.SPECIAL.getName(), 71L)).thenReturn(unequippedStack);
        when(playerPocketService.getItemAsPocket(equippedPocketId, 71L)).thenReturn(equippedStack);

        RoomPlayer roomPlayer = new RoomPlayer(player);
        boolean equipped = switch (rewardType) {
            case "exp" -> roomPlayer.isRingOfExpEquipped();
            case "gold" -> roomPlayer.isRingOfGoldEquipped();
            case "wiseman" -> roomPlayer.isRingOfWisemanEquipped();
            default -> throw new IllegalArgumentException(rewardType);
        };

        assertTrue(equipped);
    }

    @ParameterizedTest
    @MethodSource("promotionalRings")
    void ringConsumptionDecrementsTheExactRowPassedToTheFactory(
            int itemIndex, Class<? extends BaseItem> ignoredType, String ignoredRewardType) {
        long unequippedPocketId = 800L + itemIndex;
        long equippedPocketId = 900L + itemIndex;
        PlayerPocket unequippedStack = pocketItem(unequippedPocketId, itemIndex);
        PlayerPocket equippedStack = pocketItem(equippedPocketId, itemIndex);
        Pocket pocket = equippedStack.getPocket();
        ItemSpecial catalogItem = new ItemSpecial();
        catalogItem.setItemIndex(itemIndex);
        catalogItem.setName("Promotional ring");

        when(playerPocketService.getItemAsPocket(equippedPocketId, pocket)).thenReturn(equippedStack);
        when(itemSpecialService.findByItemIndex(itemIndex)).thenReturn(catalogItem);
        when(pocketService.findById(pocket.getId())).thenReturn(pocket);

        BaseItem ring = ItemFactory.getItem(equippedPocketId, pocket);
        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(11L);
        when(player.getSpecialSlots()).thenReturn(
                EquippedSpecialSlots.of(81L, List.of(Math.toIntExact(equippedPocketId), 0, 0, 0)));

        assertTrue(ring.processPlayer(player));
        assertTrue(ring.processPocket(pocket.getId()));

        assertEquals(2, unequippedStack.getItemCount());
        assertEquals(1, equippedStack.getItemCount());
        verify(playerPocketService).save(equippedStack);
    }

    private PlayerPocket pocketItem(long id, int itemIndex) {
        Pocket pocket = new Pocket();
        pocket.setId(71L);
        PlayerPocket item = new PlayerPocket();
        item.setId(id);
        item.setPocket(pocket);
        item.setCategory(EItemCategory.SPECIAL.getName());
        item.setItemIndex(itemIndex);
        item.setItemCount(2);
        return item;
    }

    private static Stream<Arguments> promotionalRings() {
        return Stream.of(
                Arguments.of(39, RingOfExp.class, "exp"),
                Arguments.of(40, RingOfGold.class, "gold"),
                Arguments.of(41, RingOfWiseman.class, "wiseman")
        );
    }
}
