package com.jftse.server.core.item;

import com.jftse.entities.database.model.pocket.PlayerPocket;

public final class BattlemonController {
    public static final int SPECIAL_ITEM_INDEX = 11;
    public static final int SHOP_PRODUCT_INDEX = 515;

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
}
