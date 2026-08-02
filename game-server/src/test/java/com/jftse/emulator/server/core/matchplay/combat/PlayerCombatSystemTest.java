package com.jftse.emulator.server.core.matchplay.combat;

import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.utils.BattleUtils;
import com.jftse.entities.database.model.battle.Skill;
import com.jftse.server.core.matchplay.EarthElement;
import com.jftse.server.core.matchplay.Elementable;
import com.jftse.server.core.matchplay.WaterElement;
import com.jftse.server.core.matchplay.WindElement;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerCombatSystemTest {
    private AtomicReference<?> statConfig;
    private Method setStatConfig;
    private Object previousStatConfig;

    @BeforeEach
    void installNeutralStatConfig() throws ReflectiveOperationException {
        Field statConfigField = BattleUtils.class.getDeclaredField("statConfig");
        statConfigField.setAccessible(true);
        Object statConfigValue = statConfigField.get(null);
        if (!(statConfigValue instanceof AtomicReference<?> reference)) {
            throw new IllegalStateException("BattleUtils statConfig is not an AtomicReference");
        }
        statConfig = reference;
        setStatConfig = AtomicReference.class.getMethod("set", Object.class);
        previousStatConfig = statConfig.get();

        Class<?> statConfigClass = Class.forName(BattleUtils.class.getName() + "$StatConfig");
        Constructor<?> constructor = statConfigClass.getDeclaredConstructor(
                double.class, double.class, double.class, int.class, int.class);
        constructor.setAccessible(true);
        setStatConfig.invoke(statConfig, constructor.newInstance(0.0, 0.0, 0.0, 10, 20));
    }

    @AfterEach
    void restoreStatConfig() throws ReflectiveOperationException {
        setStatConfig.invoke(statConfig, previousStatConfig);
    }

    @Test
    void defensiveEnchantGradeScalesPlayerDamageReduction() throws Exception {
        // Given / When
        short gradeOneMaximumHealth = dealWindDamage(List.of(new EarthElement(5, 5)), 2);
        short gradeNineMaximumHealth = dealWindDamage(List.of(new EarthElement(32, 32)), 2);

        // Then
        assertEquals(903, gradeOneMaximumHealth);
        assertEquals(915, gradeNineMaximumHealth);
    }

    @Test
    void strongestDuplicateSetsThePlayerDefenseCapWithoutStacking() throws Exception {
        // Given / When
        short targetHealth = dealWindDamage(
                List.of(new EarthElement(5, 5), new EarthElement(32, 32)), 2);

        // Then
        assertEquals(915, targetHealth);
    }

    @Test
    void distinctDefenseRelationshipsCombineInPlayerCombat() throws Exception {
        // Given / When
        short targetHealth = dealWindDamage(
                List.of(new EarthElement(32, 32), new WaterElement(32, 32)), 2);

        // Then
        assertEquals(935, targetHealth);
    }

    @Test
    void mismatchedSkillElementBypassesPlayerElementalScaling() throws Exception {
        // Given / When
        short targetHealth = dealWindDamage(List.of(new EarthElement(32, 32)), 3);

        // Then
        assertEquals(900, targetHealth);
    }

    private short dealWindDamage(List<Elementable> defenses, int skillElement) throws Exception {
        MatchplayBattleGame game = mock(MatchplayBattleGame.class);
        PlayerBattleState attacker = new PlayerBattleState((short) 0, 100, 1000, 0, 0, 0, 0);
        attacker.setOffensiveElement(new WindElement(0, 0));
        PlayerBattleState target = new PlayerBattleState((short) 1, 200, 1000, 0, 0, 0, 0);
        target.getDefensiveElements().addAll(defenses);
        when(game.getPlayerBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(attacker, target)));

        Skill skill = new Skill();
        skill.setElemental(skillElement);

        return new PlayerCombatSystem(game).dealDamage(0, 1, (short) -100, false, false, skill);
    }
}
