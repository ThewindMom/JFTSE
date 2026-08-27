package com.jftse.server.core.messenger;

import com.jftse.entities.database.model.messenger.Parcel;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ParcelItemPlacementTest {
    private static final int LUCY_STARTER_RACKET = 61;
    private static final int WIND = 6;

    @Test
    void windRacketKeepsEnchantWhenReceiverAlreadyHasSameIndex() {
        Pocket pocket = new Pocket();
        PlayerPocket existing = racket(pocket, 0, 0);
        Parcel parcel = windRacketParcel();

        PlayerPocket placed = ParcelItemPlacement.toPocketItem(existing, parcel, pocket);

        assertNotSame(existing, placed);
        assertEquals(1, existing.getItemCount());
        assertEquals(0, existing.getEnchantElement());
        assertEquals(0, existing.getEnchantLevel());
        assertEquals(LUCY_STARTER_RACKET, placed.getItemIndex());
        assertEquals("PARTS", placed.getCategory());
        assertEquals("N/A", placed.getUseType());
        assertEquals(WIND, placed.getEnchantElement());
        assertEquals(1, placed.getEnchantLevel());
        assertEquals(1, placed.getItemCount());
        assertSame(pocket, placed.getPocket());
    }

    @Test
    void windRacketKeepsEnchantWhenReceiverHasNoMatch() {
        Pocket pocket = new Pocket();
        Parcel parcel = windRacketParcel();

        PlayerPocket placed = ParcelItemPlacement.toPocketItem(null, parcel, pocket);

        assertEquals(WIND, placed.getEnchantElement());
        assertEquals(1, placed.getEnchantLevel());
        assertEquals(LUCY_STARTER_RACKET, placed.getItemIndex());
    }

    @Test
    void stackableMaterialStillMergesCount() {
        Pocket pocket = new Pocket();
        PlayerPocket existing = new PlayerPocket();
        existing.setCategory("MATERIAL");
        existing.setItemIndex(10);
        existing.setUseType("Count");
        existing.setItemCount(3);
        existing.setPocket(pocket);

        Parcel parcel = new Parcel();
        parcel.setCategory("MATERIAL");
        parcel.setItemIndex(10);
        parcel.setUseType("Count");
        parcel.setItemCount(2);
        parcel.setEnchantElement(0);
        parcel.setEnchantLevel(0);

        PlayerPocket placed = ParcelItemPlacement.toPocketItem(existing, parcel, pocket);

        assertSame(existing, placed);
        assertEquals(5, placed.getItemCount());
    }

    private static PlayerPocket racket(Pocket pocket, int element, int level) {
        PlayerPocket item = new PlayerPocket();
        item.setCategory("PARTS");
        item.setItemIndex(LUCY_STARTER_RACKET);
        item.setUseType("N/A");
        item.setItemCount(1);
        item.setEnchantElement(element);
        item.setEnchantLevel(level);
        item.setPocket(pocket);
        return item;
    }

    private static Parcel windRacketParcel() {
        Parcel parcel = new Parcel();
        parcel.setCategory("PARTS");
        parcel.setItemIndex(LUCY_STARTER_RACKET);
        parcel.setUseType("N/A");
        parcel.setItemCount(1);
        parcel.setEnchantElement(WIND);
        parcel.setEnchantLevel(1);
        return parcel;
    }
}
