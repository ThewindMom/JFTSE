package com.jftse.server.core.item;

import com.jftse.entities.database.model.player.EquippedItemStats;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.constants.GameMode;

import java.util.List;

public final class SpecialItemEffects {
    private SpecialItemEffects() {
    }

    public static boolean isMatchStatItem(int itemIndex) {
        return itemIndex >= 27 && itemIndex <= 37 || itemIndex >= 42 && itemIndex <= 46;
    }

    public static boolean isActiveInMode(int itemIndex, short gameMode) {
        if (!isMatchStatItem(itemIndex))
            return false;

        boolean hpNecklace = itemIndex >= 27 && itemIndex <= 29 || itemIndex == 42;
        return !hpNecklace || isHpActiveInMode(gameMode);
    }

    public static int getActiveHp(EquippedItemStats stats, short gameMode) {
        return isHpActiveInMode(gameMode) ? stats.getSpecialAddHp() : 0;
    }

    private static boolean isHpActiveInMode(short gameMode) {
        return gameMode == GameMode.BATTLE || gameMode == GameMode.GUARDIAN || gameMode == GameMode.BATTLEMON;
    }

    public static void apply(EquippedItemStats stats, List<PlayerPocket> equippedItems) {
        int addHp = 0;
        int strength = 0;
        int stamina = 0;
        int dexterity = 0;
        int willpower = 0;

        for (PlayerPocket item : equippedItems) {
            if (!EItemCategory.SPECIAL.getName().equals(item.getCategory()))
                continue;

            switch (item.getItemIndex()) {
                case 27, 42 -> addHp += 50;
                case 28 -> addHp += 100;
                case 29 -> addHp += 200;
                case 30, 43 -> strength += 3;
                case 31 -> strength += 5;
                case 32, 44 -> stamina += 3;
                case 33 -> stamina += 5;
                case 34, 45 -> dexterity += 3;
                case 35 -> dexterity += 5;
                case 36, 46 -> willpower += 3;
                case 37 -> willpower += 5;
            }
        }

        stats.setSpecialAddHp(addHp);
        stats.setSpecialStrength(strength);
        stats.setSpecialStamina(stamina);
        stats.setSpecialDexterity(dexterity);
        stats.setSpecialWillpower(willpower);
    }
}
