package com.jftse.server.core.messenger;

import com.jftse.entities.database.model.messenger.Parcel;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;

public final class ParcelItemPlacement {
    private ParcelItemPlacement() {
    }

    public static boolean shouldStack(PlayerPocket existing) {
        return existing != null && !existing.getUseType().equals("N/A");
    }

    public static PlayerPocket toPocketItem(PlayerPocket existing, Parcel parcel, Pocket pocket) {
        if (shouldStack(existing)) {
            existing.setItemCount(existing.getItemCount() + parcel.getItemCount());
            return existing;
        }

        PlayerPocket item = new PlayerPocket();
        item.setCategory(parcel.getCategory());
        item.setItemCount(parcel.getItemCount());
        item.setItemIndex(parcel.getItemIndex());
        item.setUseType(parcel.getUseType());
        item.setPocket(pocket);
        item.setEnchantStr(parcel.getEnchantStr());
        item.setEnchantSta(parcel.getEnchantSta());
        item.setEnchantDex(parcel.getEnchantDex());
        item.setEnchantWil(parcel.getEnchantWil());
        item.setEnchantElement(parcel.getEnchantElement());
        item.setEnchantLevel(parcel.getEnchantLevel());
        return item;
    }
}
