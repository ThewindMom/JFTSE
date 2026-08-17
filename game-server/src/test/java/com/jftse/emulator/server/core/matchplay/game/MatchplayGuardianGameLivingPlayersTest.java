package com.jftse.emulator.server.core.matchplay.game;

import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchplayGuardianGameLivingPlayersTest {

    @Test
    void livingPlayersKeepsOnlyActiveLivingPlayerSlots() {
        MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
        ConcurrentLinkedDeque<PlayerBattleState> states = new ConcurrentLinkedDeque<>();
        states.add(player((short) 0, false, 10));
        states.add(player((short) 1, true, 0));
        states.add(player((short) 2, false, 0));
        states.add(player((short) 3, false, 5));
        states.add(player((short) 10, false, 300));
        when(game.getPlayerBattleStates()).thenReturn(states);
        when(game.livingPlayers()).thenCallRealMethod();

        List<PlayerBattleState> living = game.livingPlayers();

        assertEquals(2, living.size());
        assertEquals(0, living.get(0).getPosition());
        assertEquals(3, living.get(1).getPosition());
        assertTrue(living.stream().allMatch(p -> p.getPosition() < 4 && !p.isDead() && p.getCurrentHealth().get() > 0));
    }

    private static PlayerBattleState player(short position, boolean dead, int hp) {
        PlayerBattleState state = new PlayerBattleState(position, position + 300L, 100, 10, 10, 10, 10);
        state.getCurrentHealth().set(hp);
        state.setDead(dead);
        return state;
    }
}
