package com.jftse.emulator.server.core.item;

import com.jftse.entities.database.model.player.EquippedItemStats;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.item.SpecialItemEffects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialItemEffectsTest {
    @ParameterizedTest
    @CsvSource({
            "27, 50, 0, 0, 0, 0",
            "28, 100, 0, 0, 0, 0",
            "29, 200, 0, 0, 0, 0",
            "30, 0, 3, 0, 0, 0",
            "31, 0, 5, 0, 0, 0",
            "32, 0, 0, 3, 0, 0",
            "33, 0, 0, 5, 0, 0",
            "34, 0, 0, 0, 3, 0",
            "35, 0, 0, 0, 5, 0",
            "36, 0, 0, 0, 0, 3",
            "37, 0, 0, 0, 0, 5",
            "42, 50, 0, 0, 0, 0",
            "43, 0, 3, 0, 0, 0",
            "44, 0, 0, 3, 0, 0",
            "45, 0, 0, 0, 3, 0",
            "46, 0, 0, 0, 0, 3"
    })
    void resolvesCatalogNecklaceAndEarringEffects(int itemIndex, int hp, int str, int sta, int dex, int wil) {
        EquippedItemStats stats = new EquippedItemStats();

        SpecialItemEffects.apply(stats, List.of(item(itemIndex, EItemCategory.SPECIAL.getName())));

        assertEquals(hp, stats.getSpecialAddHp());
        assertEquals(str, stats.getSpecialStrength());
        assertEquals(sta, stats.getSpecialStamina());
        assertEquals(dex, stats.getSpecialDexterity());
        assertEquals(wil, stats.getSpecialWillpower());
        assertTrue(SpecialItemEffects.isMatchStatItem(itemIndex));
    }

    @Test
    void aggregatesFourEquippedSpecialSlots() {
        EquippedItemStats stats = new EquippedItemStats();

        SpecialItemEffects.apply(stats, List.of(
                item(29, EItemCategory.SPECIAL.getName()),
                item(31, EItemCategory.SPECIAL.getName()),
                item(44, EItemCategory.SPECIAL.getName()),
                item(37, EItemCategory.SPECIAL.getName())
        ));

        assertEquals(200, stats.getSpecialAddHp());
        assertEquals(5, stats.getSpecialStrength());
        assertEquals(3, stats.getSpecialStamina());
        assertEquals(0, stats.getSpecialDexterity());
        assertEquals(5, stats.getSpecialWillpower());
    }

    @Test
    void ignoresUnknownAndNonSpecialInventoryRowsAndClearsPreviousEffects() {
        EquippedItemStats stats = new EquippedItemStats();
        stats.setSpecialAddHp(200);
        stats.setSpecialStrength(5);

        SpecialItemEffects.apply(stats, List.of(
                item(29, EItemCategory.PARTS.getName()),
                item(48, EItemCategory.SPECIAL.getName())
        ));

        assertEquals(0, stats.getSpecialAddHp());
        assertEquals(0, stats.getSpecialStrength());
        assertFalse(SpecialItemEffects.isMatchStatItem(26));
        assertFalse(SpecialItemEffects.isMatchStatItem(48));
    }

    @ParameterizedTest
    @CsvSource({
            "27, 0, false",
            "27, 1, true",
            "27, 2, true",
            "27, -1, true",
            "30, 0, true",
            "30, 1, true",
            "30, 2, true",
            "30, -1, true",
            "48, 1, false"
    })
    void appliesItemsOnlyInTheirSupportedGameModes(int itemIndex, short gameMode, boolean expected) {
        assertEquals(expected, SpecialItemEffects.isActiveInMode(itemIndex, gameMode));
    }

    @Test
    void exposesNecklaceHpOnlyInBattleAndGuardianModes() {
        EquippedItemStats stats = new EquippedItemStats();
        stats.setSpecialAddHp(200);

        assertEquals(0, SpecialItemEffects.getActiveHp(stats, GameMode.BASIC));
        assertEquals(200, SpecialItemEffects.getActiveHp(stats, GameMode.BATTLE));
        assertEquals(200, SpecialItemEffects.getActiveHp(stats, GameMode.GUARDIAN));
        assertEquals(200, SpecialItemEffects.getActiveHp(stats, GameMode.BATTLEMON));
    }

    private PlayerPocket item(int itemIndex, String category) {
        PlayerPocket item = new PlayerPocket();
        item.setItemIndex(itemIndex);
        item.setCategory(category);
        return item;
    }
}
