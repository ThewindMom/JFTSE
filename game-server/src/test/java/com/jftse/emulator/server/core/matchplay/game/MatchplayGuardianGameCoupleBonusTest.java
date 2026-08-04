package com.jftse.emulator.server.core.matchplay.game;

import com.jftse.emulator.server.core.life.room.RoomPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchplayGuardianGameCoupleBonusTest {
    private static final long PLAYER_ID = 101L;
    private static final long PARTNER_ID = 202L;

    @Test
    void partnerWithoutActiveCoupleDoesNotReceiveGuardianBonus() {
        RoomPlayer player = roomPlayer(PLAYER_ID, null);
        RoomPlayer partner = roomPlayer(PARTNER_ID, PLAYER_ID);

        assertFalse(MatchplayGuardianGame.hasActiveCoupleInParty(player, List.of(player, partner)));
    }

    @Test
    void mutualActiveCoupleReceivesGuardianBonus() {
        RoomPlayer player = roomPlayer(PLAYER_ID, PARTNER_ID);
        RoomPlayer partner = roomPlayer(PARTNER_ID, PLAYER_ID);

        assertTrue(MatchplayGuardianGame.hasActiveCoupleInParty(player, List.of(player, partner)));
    }

    private RoomPlayer roomPlayer(long playerId, Long coupleId) {
        return new RoomPlayer(null) {
            @Override
            public long getPlayerId() {
                return playerId;
            }

            @Override
            public Long getCoupleId() {
                return coupleId;
            }
        };
    }
}
