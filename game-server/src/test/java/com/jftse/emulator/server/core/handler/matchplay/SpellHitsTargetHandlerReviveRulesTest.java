package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.common.exception.ValidationException;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.matchplay.combat.PlayerCombatSystem;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.entities.database.model.battle.Guardian;
import com.jftse.entities.database.model.battle.Skill;
import com.jftse.server.core.matchplay.battle.GuardianBattleState;
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

class SpellHitsTargetHandlerReviveRulesTest {
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
    void rebirthAndRebirthOneAreNotRejectedSolelyBecauseHpIsBelowOne() {
        PlayerBattleState dead = deadPlayer((short) 1);
        Skill rebirth = skill(5L, 50);
        Skill rebirthOne = skill(29L, 30);
        Skill heal = skill(3L, 25);

        assertFalse(SpellHitsTargetHandler.rejectSkillOnDeadPlayer(dead, rebirth));
        assertFalse(SpellHitsTargetHandler.rejectSkillOnDeadPlayer(dead, rebirthOne));
        assertTrue(SpellHitsTargetHandler.rejectSkillOnDeadPlayer(dead, heal));
        assertTrue(SpellHitsTargetHandler.isReviveSkill(rebirth));
        assertTrue(SpellHitsTargetHandler.isReviveSkill(rebirthOne));
        assertFalse(SpellHitsTargetHandler.isReviveSkill(heal));
        assertFalse(SpellHitsTargetHandler.isReviveSkill(null));
    }

    @Test
    void normalHealSkillOnDeadPlayerIsStillRejected() {
        PlayerBattleState dead = deadPlayer((short) 2);
        Skill potion = skill(12L, 40);

        assertTrue(SpellHitsTargetHandler.rejectSkillOnDeadPlayer(dead, potion));
        assertFalse(SpellHitsTargetHandler.rejectSkillOnDeadPlayer(livingPlayer((short) 0), potion));
        assertFalse(SpellHitsTargetHandler.rejectSkillOnDeadPlayer(null, potion));
    }

    @Test
    void resolveGuardianReviveTargetsAimedDeadPlayerNotTheCaster() throws ValidationException {
        PlayerBattleState caster = livingPlayer((short) 0);
        int casterHp = caster.getCurrentHealth().get();
        PlayerBattleState dead1 = deadPlayer((short) 1);
        PlayerBattleState dead3 = deadPlayer((short) 3);
        states.add(caster);
        states.add(dead1);
        states.add(dead3);

        PlayerBattleState revived = SpellHitsTargetHandler.resolveGuardianRevive(combat, (short) 3, (short) 50);

        assertNotNull(revived);
        assertEquals(3, revived.getPosition());
        assertFalse(dead3.isDead());
        assertTrue(dead3.getCurrentHealth().get() > 0);
        assertTrue(dead1.isDead());
        assertEquals(0, dead1.getCurrentHealth().get());
        assertEquals(casterHp, caster.getCurrentHealth().get());
        assertEquals(List.of(0, 3), game.livingPlayers().stream().map(PlayerBattleState::getPosition).toList());
    }

    @Test
    void resolveGuardianReviveFallsBackToFirstDeadWhenAimedSlotIsLiving() throws ValidationException {
        PlayerBattleState living0 = livingPlayer((short) 0);
        PlayerBattleState living2 = livingPlayer((short) 2);
        PlayerBattleState firstDead = deadPlayer((short) 1);
        PlayerBattleState laterDead = deadPlayer((short) 3);
        states.add(living0);
        states.add(firstDead);
        states.add(living2);
        states.add(laterDead);

        PlayerBattleState revived = SpellHitsTargetHandler.resolveGuardianRevive(combat, (short) 0, (short) 40);

        assertNotNull(revived);
        assertEquals(1, revived.getPosition());
        assertFalse(firstDead.isDead());
        assertTrue(laterDead.isDead());
        assertFalse(living0.isDead());
    }

    @Test
    void resolveGuardianReviveFallsBackToFirstDeadForInvalidPosition() throws ValidationException {
        PlayerBattleState living0 = livingPlayer((short) 0);
        PlayerBattleState dead1 = deadPlayer((short) 1);
        PlayerBattleState dead3 = deadPlayer((short) 3);
        states.add(living0);
        states.add(dead1);
        states.add(dead3);

        PlayerBattleState revived = SpellHitsTargetHandler.resolveGuardianRevive(combat, (short) 9, (short) 50);

        assertNotNull(revived);
        assertEquals(1, revived.getPosition());
        assertTrue(dead3.isDead());
    }

    @Test
    void resolveGuardianReviveReturnsNullWhenNobodyIsDead() throws ValidationException {
        PlayerBattleState living0 = livingPlayer((short) 0);
        PlayerBattleState living1 = livingPlayer((short) 1);
        int hp0 = living0.getCurrentHealth().get();
        int hp1 = living1.getCurrentHealth().get();
        states.add(living0);
        states.add(living1);

        assertNull(SpellHitsTargetHandler.resolveGuardianRevive(combat, (short) 1, (short) 50));
        assertEquals(hp0, living0.getCurrentHealth().get());
        assertEquals(hp1, living1.getCurrentHealth().get());
    }

    @Test
    void resolveBattleReviveStaysTeammateAwareOnFallback() throws ValidationException {
        ConcurrentLinkedDeque<PlayerBattleState> battleStates = new ConcurrentLinkedDeque<>();
        PlayerBattleState redLiving = livingPlayer((short) 0);
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

        PlayerBattleState revived = SpellHitsTargetHandler.resolveBattleRevive(
                battleCombat, (short) 0, (short) 40, redCaster);

        assertNotNull(revived);
        assertEquals(2, revived.getPosition());
        assertTrue(blueDead.isDead());
        assertFalse(redDead.isDead());
    }

    @Test
    void guardianReceiverHealthWithoutSkillDoesNotLookUpPlayerHp() throws ValidationException {
        Guardian guardian = new Guardian();
        guardian.setId(7L);
        guardian.setBtItemID(1);
        GuardianBattleState guardianState = new GuardianBattleState(guardian, (short) 10, 4500, 10, 10, 10, 10, 0, 0, 0);
        guardianState.getCurrentHealth().set(3210);

        short health = SpellHitsTargetHandler.guardianReceiverHealthWithoutSkill(guardianState);

        assertEquals(3210, health);
        assertThrows(ValidationException.class, () -> combat.getPlayerCurrentHealth((short) 10));
        assertThrows(ValidationException.class, () -> SpellHitsTargetHandler.guardianReceiverHealthWithoutSkill(null));
    }

    private static Skill skill(long id, int damage) {
        Skill skill = new Skill();
        skill.setId(id);
        skill.setDamage(damage);
        return skill;
    }

    private static PlayerBattleState deadPlayer(short position) {
        PlayerBattleState state = new PlayerBattleState(position, position + 400L, 200, 10, 10, 10, 10);
        state.getCurrentHealth().set(0);
        state.setDead(true);
        return state;
    }

    private static PlayerBattleState livingPlayer(short position) {
        return new PlayerBattleState(position, position + 400L, 200, 10, 10, 10, 10);
    }
}
