package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.life.room.GameSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionManagerTest {
    @AfterEach
    void clearServerType() {
        System.clearProperty("GameServerType");
    }

    @Test
    void ordinaryAndClubServersAllocateDisjointSessionRanges() {
        GameSessionManager manager = new GameSessionManager();
        manager.init();

        System.setProperty("GameServerType", "1");
        int ordinaryId = manager.addGameSession(new GameSession());
        System.setProperty("GameServerType", "7");
        int clubId = manager.addGameSession(new GameSession());

        assertTrue(ordinaryId >= 10_000 && ordinaryId < 20_000);
        assertTrue(clubId >= 70_000 && clubId < 80_000);
    }
}
