package com.jftse.emulator.server.core.matchplay.combat;

import com.jftse.emulator.common.exception.ValidationException;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerCombatSystemReviveTest {
    private ConcurrentLinkedDeque<PlayerBattleState> states;
    private MatchplayGuardianGame game;
    private PlayerCombatSystem combat;

    @BeforeEach
    void setUp() {
        game = mock(MatchplayGuardianGame.class);
        states = new ConcurrentLinkedDeque<>();
        when(game.getPlayerBattleStates()).thenReturn(states);
        when(game.livingPlayers()).thenCallRealMethod();
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
    void reviveAimedPositionThreeLeavesPositionOneDead() throws ValidationException {
        PlayerBattleState living0 = livingPlayer((short) 0);
        PlayerBattleState dead1 = deadPlayer((short) 1);
        PlayerBattleState living2 = livingPlayer((short) 2);
        PlayerBattleState dead3 = deadPlayer((short) 3);
        states.add(living0);
        states.add(dead1);
        states.add(living2);
        states.add(dead3);

        PlayerBattleState revived = combat.revivePlayer((short) 3, (short) 50);

        assertNotNull(revived);
        assertEquals(3, revived.getPosition());
        assertFalse(dead3.isDead());
        assertTrue(dead3.getCurrentHealth().get() > 0);
        assertTrue(dead1.isDead());
        assertEquals(0, dead1.getCurrentHealth().get());
        assertEquals(List.of(0, 2, 3), livingPositions());
    }

    @Test
    void reviveAimedPositionOneLeavesPositionThreeDead() throws ValidationException {
        PlayerBattleState living0 = livingPlayer((short) 0);
        PlayerBattleState dead1 = deadPlayer((short) 1);
        PlayerBattleState living2 = livingPlayer((short) 2);
        PlayerBattleState dead3 = deadPlayer((short) 3);
        states.add(living0);
        states.add(dead1);
        states.add(living2);
        states.add(dead3);

        PlayerBattleState revived = combat.revivePlayer((short) 1, (short) 40);

        assertNotNull(revived);
        assertEquals(1, revived.getPosition());
        assertFalse(dead1.isDead());
        assertTrue(dead1.getCurrentHealth().get() > 0);
        assertTrue(dead3.isDead());
        assertEquals(0, dead3.getCurrentHealth().get());
        assertEquals(List.of(0, 1, 2), livingPositions());
    }

    @Test
    void reviveAlreadyLivingPositionDoesNotRezSomeoneElse() throws ValidationException {
        PlayerBattleState living0 = livingPlayer((short) 0);
        int casterHp = living0.getCurrentHealth().get();
        PlayerBattleState dead1 = deadPlayer((short) 1);
        PlayerBattleState dead3 = deadPlayer((short) 3);
        states.add(living0);
        states.add(dead1);
        states.add(dead3);

        assertNull(combat.revivePlayer((short) 0, (short) 50));

        assertEquals(casterHp, living0.getCurrentHealth().get());
        assertFalse(living0.isDead());
        assertTrue(dead1.isDead());
        assertEquals(0, dead1.getCurrentHealth().get());
        assertTrue(dead3.isDead());
        assertEquals(0, dead3.getCurrentHealth().get());
    }

    @Test
    void reviveInvalidPositionDoesNotChangeAnyone() throws ValidationException {
        PlayerBattleState living0 = livingPlayer((short) 0);
        PlayerBattleState dead1 = deadPlayer((short) 1);
        states.add(living0);
        states.add(dead1);

        assertNull(combat.revivePlayer((short) 9, (short) 50));

        assertTrue(dead1.isDead());
        assertEquals(0, dead1.getCurrentHealth().get());
        assertFalse(living0.isDead());
    }

    @Test
    void reviveWhenNobodyDeadReturnsNullAndLeavesHpUnchanged() throws ValidationException {
        PlayerBattleState living0 = livingPlayer((short) 0);
        PlayerBattleState living1 = livingPlayer((short) 1);
        int hp0 = living0.getCurrentHealth().get();
        int hp1 = living1.getCurrentHealth().get();
        states.add(living0);
        states.add(living1);

        assertNull(combat.revivePlayer((short) 1, (short) 50));
        assertNull(combat.reviveAnyPlayer((short) 50));

        assertEquals(hp0, living0.getCurrentHealth().get());
        assertEquals(hp1, living1.getCurrentHealth().get());
        assertFalse(living0.isDead());
        assertFalse(living1.isDead());
    }

    @Test
    void aimedAtPlayerGetsHpAndClearsDeadWhileNonTargetStaysDead() throws ValidationException {
        PlayerBattleState caster = livingPlayer((short) 0);
        int casterHp = caster.getCurrentHealth().get();
        PlayerBattleState target = deadPlayer((short) 2);
        PlayerBattleState otherDead = deadPlayer((short) 3);
        states.add(caster);
        states.add(target);
        states.add(otherDead);

        PlayerBattleState revived = combat.revivePlayer((short) 2, (short) 30);

        assertNotNull(revived);
        assertEquals(2, revived.getPosition());
        assertTrue(revived.getCurrentHealth().get() > 0);
        assertFalse(revived.isDead());
        assertEquals(0, otherDead.getCurrentHealth().get());
        assertTrue(otherDead.isDead());
        assertEquals(casterHp, caster.getCurrentHealth().get());
        assertFalse(caster.isDead());
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

    @Test
    void healOnLivingDoesNotSetDead() throws ValidationException {
        PlayerBattleState living = livingPlayer((short) 0);
        living.getCurrentHealth().set(100);
        states.add(living);

        short newHealth = combat.heal(0, (short) 25);

        assertTrue(newHealth > 100);
        assertFalse(living.isDead());
    }

    @Test
    void zeroPercentHealOnDeadPlayerLeavesThemDead() throws ValidationException {
        PlayerBattleState dead = deadPlayer((short) 0);
        states.add(dead);

        short newHealth = combat.heal(0, (short) 0);

        assertEquals(0, newHealth);
        assertEquals(0, dead.getCurrentHealth().get());
        assertTrue(dead.isDead());
    }

    @Test
    void damageToZeroSetsDead() {
        PlayerBattleState living = livingPlayer((short) 0);
        states.add(living);

        short newHealth = combat.updateHealthByDamage(living, -living.getMaxHealth());

        assertEquals(0, newHealth);
        assertEquals(0, living.getCurrentHealth().get());
        assertTrue(living.isDead());
    }

    @Test
    void getPlayerCurrentHealthThrowsForGuardianPosition() {
        states.add(livingPlayer((short) 0));

        ValidationException missingFour = assertThrows(ValidationException.class,
                () -> combat.getPlayerCurrentHealth((short) 4));
        ValidationException missingTen = assertThrows(ValidationException.class,
                () -> combat.getPlayerCurrentHealth((short) 10));

        assertTrue(missingFour.getMessage().contains("playerBattleState is null"));
        assertTrue(missingTen.getMessage().contains("playerBattleState is null"));
    }

    @Test
    void getPlayerCurrentHealthReturnsLivingPlayerHp() throws ValidationException {
        PlayerBattleState living = livingPlayer((short) 0);
        living.getCurrentHealth().set(123);
        states.add(living);

        assertEquals(123, combat.getPlayerCurrentHealth((short) 0));
    }

    @Test
    void battleReviveAnyPlayerIsTeammateAware() throws ValidationException {
        ConcurrentLinkedDeque<PlayerBattleState> battleStates = new ConcurrentLinkedDeque<>();
        PlayerBattleState redLiving = livingPlayer((short) 0);
        int casterHp = redLiving.getCurrentHealth().get();
        PlayerBattleState blueDead = deadPlayer((short) 1);
        PlayerBattleState redDead = deadPlayer((short) 2);
        battleStates.add(redLiving);
        battleStates.add(blueDead);
        battleStates.add(redDead);

        MatchplayBattleGame battleGame = mock(MatchplayBattleGame.class);
        when(battleGame.getPlayerBattleStates()).thenReturn(battleStates);
        when(battleGame.isRedTeam(anyInt())).thenCallRealMethod();
        PlayerCombatSystem battleCombat = new PlayerCombatSystem(battleGame);

        RoomPlayer redCaster = new RoomPlayer(null);
        redCaster.setPosition((short) 0);

        PlayerBattleState revived = battleCombat.reviveAnyPlayer((short) 40, redCaster);

        assertNotNull(revived);
        assertEquals(2, revived.getPosition());
        assertFalse(redDead.isDead());
        assertTrue(blueDead.isDead());
        assertEquals(0, blueDead.getCurrentHealth().get());
        assertEquals(casterHp, redLiving.getCurrentHealth().get());
    }

    @Test
    void battleRevivePlayerStillTargetsAimedTeammate() throws ValidationException {
        ConcurrentLinkedDeque<PlayerBattleState> battleStates = new ConcurrentLinkedDeque<>();
        PlayerBattleState redLiving = livingPlayer((short) 0);
        PlayerBattleState redDead = deadPlayer((short) 2);
        PlayerBattleState blueDead = deadPlayer((short) 1);
        battleStates.add(redLiving);
        battleStates.add(redDead);
        battleStates.add(blueDead);

        MatchplayBattleGame battleGame = mock(MatchplayBattleGame.class);
        when(battleGame.getPlayerBattleStates()).thenReturn(battleStates);
        when(battleGame.isRedTeam(anyInt())).thenCallRealMethod();
        PlayerCombatSystem battleCombat = new PlayerCombatSystem(battleGame);

        PlayerBattleState revived = battleCombat.revivePlayer((short) 2, (short) 50);

        assertNotNull(revived);
        assertEquals(2, revived.getPosition());
        assertTrue(blueDead.isDead());
    }

    private List<Integer> livingPositions() {
        return game.livingPlayers().stream().map(PlayerBattleState::getPosition).toList();
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
