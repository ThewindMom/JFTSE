package com.jftse.emulator.server.core.handler.player;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerNameChangeHandlerTest {
    @Test
    void nicknameChangerUsesDocumentedSixMonthCooldown() {
        Date lastChange = Date.from(Instant.parse("2026-01-31T12:34:56Z"));

        Date nextChange = PlayerNameChangeHandler.nextNameChangeDate(lastChange);

        assertEquals(Instant.parse("2026-07-31T12:34:56Z"), nextChange.toInstant());
    }
}
