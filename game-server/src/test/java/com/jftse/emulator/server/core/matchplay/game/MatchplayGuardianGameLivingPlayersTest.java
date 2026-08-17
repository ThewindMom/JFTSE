package com.jftse.emulator.server.core.matchplay.game;

import com.jftse.emulator.common.exception.ValidationException;
import com.jftse.emulator.server.core.matchplay.combat.PlayerCombatSystem;
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

    @Test
    void livingPlayersOrderFollowsBattleStateInsertionOrder() {
        // ConcurrentLinkedDeque encounter order: insertion order. livingPlayers() does not sort.
        MatchplayGuardianGame game = gameWith(
                player((short) 3, false, 5),
                player((short) 0, false, 10),
                player((short) 1, true, 0),
                player((short) 2, false, 8));

        List<Integer> positions = game.livingPlayers().stream().map(PlayerBattleState::getPosition).toList();

        assertEquals(List.of(3, 0, 2), positions);
    }

    @Test
    void livingPlayersEmptyWhenEveryoneIsDead() {
        MatchplayGuardianGame game = gameWith(
                player((short) 0, true, 0),
                player((short) 1, true, 0),
                player((short) 2, true, 0),
                player((short) 3, true, 0));

        assertTrue(game.livingPlayers().isEmpty());
    }

    @Test
    void livingPlayersOmitsGuardiansAndRequiresBothAlivePredicates() {
        MatchplayGuardianGame game = gameWith(
                player((short) 0, false, 20),
                player((short) 1, true, 15),
                player((short) 2, false, 0),
                player((short) 4, false, 8000),
                player((short) 10, false, 9000));

        List<Integer> positions = game.livingPlayers().stream().map(PlayerBattleState::getPosition).toList();

        assertEquals(List.of(0), positions);
    }

    @Test
    void livingPlayersAfterReviveIncludesOnlyTheRevivedDeadPlusOriginalLiving() throws ValidationException {
        ConcurrentLinkedDeque<PlayerBattleState> states = new ConcurrentLinkedDeque<>();
        PlayerBattleState living = player((short) 0, false, 80);
        PlayerBattleState deadOne = player((short) 1, true, 0);
        PlayerBattleState deadThree = player((short) 3, true, 0);
        states.add(living);
        states.add(deadOne);
        states.add(deadThree);

        MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
        when(game.getPlayerBattleStates()).thenReturn(states);
        when(game.livingPlayers()).thenCallRealMethod();
        PlayerCombatSystem combat = new PlayerCombatSystem(game);

        assertEquals(List.of(0), game.livingPlayers().stream().map(PlayerBattleState::getPosition).toList());

        combat.revivePlayer((short) 3, (short) 50);

        List<Integer> after = game.livingPlayers().stream().map(PlayerBattleState::getPosition).toList();
        assertEquals(List.of(0, 3), after);
        assertTrue(deadThree.getCurrentHealth().get() > 0);
        assertTrue(!deadThree.isDead());
        assertEquals(0, deadOne.getCurrentHealth().get());
        assertTrue(deadOne.isDead());
    }

    private static MatchplayGuardianGame gameWith(PlayerBattleState... players) {
        MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
        ConcurrentLinkedDeque<PlayerBattleState> states = new ConcurrentLinkedDeque<>();
        for (PlayerBattleState player : players) {
            states.add(player);
        }
        when(game.getPlayerBattleStates()).thenReturn(states);
        when(game.livingPlayers()).thenCallRealMethod();
        return game;
    }

    private static PlayerBattleState player(short position, boolean dead, int hp) {
        PlayerBattleState state = new PlayerBattleState(position, position + 300L, 100, 10, 10, 10, 10);
        state.getCurrentHealth().set(hp);
        state.setDead(dead);
        return state;
    }
}
