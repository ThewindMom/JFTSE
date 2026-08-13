package com.jftse.server.core.item;

import com.jftse.entities.database.model.pocket.PlayerPocket;

import java.util.Optional;

public final class BattlemonController {
    public static final int SPECIAL_ITEM_INDEX = 11;
    public static final int SHOP_PRODUCT_INDEX = 515;

    public enum CoverageArea {
        UP(0x6d),
        DOWN(0x6e),
        LEFT(0x6f),
        RIGHT(0x70);

        private final int animationType;

        CoverageArea(int animationType) {
            this.animationType = animationType;
        }

        public int animationType() {
            return animationType;
        }
    }

    private BattlemonController() {
    }

    public static boolean isPossessed(PlayerPocket playerPocket) {
        return playerPocket != null
                && EItemCategory.SPECIAL.getName().equals(playerPocket.getCategory())
                && Integer.valueOf(SPECIAL_ITEM_INDEX).equals(playerPocket.getItemIndex())
                && playerPocket.getItemCount() != null
                && playerPocket.getItemCount() > 0;
    }

    public static boolean isPetActor(int actorPosition) {
        return actorPosition == 2 || actorPosition == 3;
    }

    public static Optional<CoverageArea> coverageArea(byte animationType) {
        int unsignedAnimationType = Byte.toUnsignedInt(animationType);
        for (CoverageArea coverageArea : CoverageArea.values()) {
            if (coverageArea.animationType() == unsignedAnimationType) {
                return Optional.of(coverageArea);
            }
        }
        return Optional.empty();
    }
}
