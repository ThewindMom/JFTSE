package com.jftse.emulator.server.core.matchplay.combat;

import com.jftse.emulator.common.exception.ValidationException;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerCombatSystemReviveTest {
    private ConcurrentLinkedDeque<PlayerBattleState> states;
    private PlayerCombatSystem combat;

    @BeforeEach
    void setUp() {
        MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
        states = new ConcurrentLinkedDeque<>();
        when(game.getPlayerBattleStates()).thenReturn(states);
        combat = new PlayerCombatSystem(game);
    }

    @Test
    void revivePlayerTargetsAimedSlotNotFirstDead() throws ValidationException {
        PlayerBattleState firstDead = deadPlayer((short) 0);
        PlayerBattleState aimedDead = deadPlayer((short) 1);
        states.add(firstDead);
        states.add(aimedDead);

        PlayerBattleState revived = combat.revivePlayer((short) 1, (short) 50);

        assertNotNull(revived);
        assertEquals(1, revived.getPosition());
        assertFalse(revived.isDead());
        assertTrue(revived.getCurrentHealth().get() > 0);
        assertTrue(firstDead.isDead());
        assertEquals(0, firstDead.getCurrentHealth().get());
    }

    @Test
    void revivePlayerReturnsNullWhenTargetIsAliveSoCallerCanFallback() throws ValidationException {
        PlayerBattleState living = livingPlayer((short) 1);
        PlayerBattleState firstDead = deadPlayer((short) 0);
        states.add(firstDead);
        states.add(living);

        assertNull(combat.revivePlayer((short) 1, (short) 50));

        PlayerBattleState fallback = combat.reviveAnyPlayer((short) 40);
        assertNotNull(fallback);
        assertEquals(0, fallback.getPosition());
        assertFalse(fallback.isDead());
    }

    @Test
    void healFromZeroClearsDeadFlag() throws ValidationException {
        PlayerBattleState dead = deadPlayer((short) 0);
        states.add(dead);

        short newHealth = combat.heal(0, (short) 25);

        assertTrue(newHealth > 0);
        assertEquals(newHealth, dead.getCurrentHealth().get());
        assertFalse(dead.isDead());
    }

    private static PlayerBattleState deadPlayer(short position) {
        PlayerBattleState state = new PlayerBattleState(position, position + 200L, 200, 10, 10, 10, 10);
        state.getCurrentHealth().set(0);
        state.setDead(true);
        return state;
    }

    private static PlayerBattleState livingPlayer(short position) {
        return new PlayerBattleState(position, position + 200L, 200, 10, 10, 10, 10);
    }
}
