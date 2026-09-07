package com.jftse.emulator.server.core.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClubHouseWorldContractTest {
    @Test
    void usesTheClientCastleMapAndAnInteriorSpawn() {
        assertEquals(5, GameManager.CLUB_HOUSE_MAP);
        assertEquals(16.0f, GameManager.CLUB_HOUSE_SPAWN_X);
        assertEquals(32.0f, GameManager.CLUB_HOUSE_SPAWN_Y);
    }
}
