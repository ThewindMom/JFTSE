package com.jftse.emulator.server.core.matchplay.guardian;

import com.jftse.emulator.server.core.matchplay.combat.PlayerDamageApplier;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtlantisV2SafeZoneIntegrationTest {
    private static final int SESSION = 1010;
    private static final int PLAYER = 77;

    @Test
    void visiblePadDropsEverySeaWaveInAVolleyWithoutSpendingTheOneShotShield() {
        GuardianShieldPads pads = new GuardianShieldPads(GuardianShieldPads.Config.defaults(), (s, p, pos) -> {
        });
        pads.onMatchStart(SESSION);
        pads.onCourtPosition(SESSION, PLAYER, 0, -40, -40);
        pads.activate(SESSION);

        assertTrue(pads.isVisible(SESSION));
        assertTrue(pads.isInsideVisiblePad(SESSION, PLAYER, 0));
        assertTrue(pads.hasGranted(SESSION, PLAYER));

        PlayerBattleState state = new PlayerBattleState((short) 0, PLAYER, 3000, 10, 10, 10, 10);
        state.setShieldActive(true);

        for (int wave = 0; wave < AtlantisV2Rules.FIRST_VOLLEY_COUNT + AtlantisV2Rules.SECOND_VOLLEY_COUNT; wave++) {
            assertTrue(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 0, pads.isInsideVisiblePad(SESSION, PLAYER, 0)),
                    "wave " + wave + " must be ignored while standing on the pad");
        }

        assertTrue(state.isShieldActive(), "safe-zone ignore must not consume the one-shot shield");
        assertEquals(3000, state.getCurrentHealth().get());
    }

    @Test
    void leavingThePadLetsSeaWaveThroughAndThenOneShotShieldAbsorbsOnce() {
        GuardianShieldPads pads = new GuardianShieldPads(GuardianShieldPads.Config.defaults(), (s, p, pos) -> {
        });
        pads.onMatchStart(SESSION);
        pads.activate(SESSION);
        pads.onCourtPosition(SESSION, PLAYER, 0, -40, -40);
        assertTrue(pads.isInsideVisiblePad(SESSION, PLAYER, 0));

        pads.onCourtPosition(SESSION, PLAYER, 0, 0, 0);
        assertFalse(pads.isInsideVisiblePad(SESSION, PLAYER, 0));
        assertFalse(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 0, false));

        PlayerBattleState state = new PlayerBattleState((short) 0, PLAYER, 3000, 10, 10, 10, 10);
        state.setShieldActive(true);
        assertEquals(3000, PlayerDamageApplier.updateHealthByDamage(state, -9000));
        assertFalse(state.isShieldActive());
        assertEquals(0, PlayerDamageApplier.updateHealthByDamage(state, -9000));
        assertTrue(state.isDead());
    }

    @Test
    void padsAreNotASafeZoneBeforeTheyBecomeVisible() {
        GuardianShieldPads pads = new GuardianShieldPads(GuardianShieldPads.Config.defaults(), (s, p, pos) -> {
        });
        pads.onMatchStart(SESSION);
        pads.onCourtPosition(SESSION, PLAYER, 0, 40, -40);
        assertFalse(pads.isInsideVisiblePad(SESSION, PLAYER, 0));
        assertFalse(AtlantisV2Rules.shouldIgnoreSeaWaveHit((short) 0, false));
    }
}
