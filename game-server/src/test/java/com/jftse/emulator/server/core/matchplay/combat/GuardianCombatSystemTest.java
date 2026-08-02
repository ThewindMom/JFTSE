package com.jftse.emulator.server.core.matchplay.combat;

import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.core.utils.BattleUtils;
import com.jftse.entities.database.model.battle.GuardianBase;
import com.jftse.entities.database.model.battle.Skill;
import com.jftse.server.core.matchplay.EarthElement;
import com.jftse.server.core.matchplay.Elementable;
import com.jftse.server.core.matchplay.WaterElement;
import com.jftse.server.core.matchplay.WindElement;
import com.jftse.server.core.matchplay.battle.GuardianBattleState;
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

class GuardianCombatSystemTest {
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
    void defensiveEnchantGradeScalesDamageFromGuardianToPlayer() throws Exception {
        // Given / When
        short gradeOneMaximumHealth = dealGuardianDamageToPlayer(List.of(new EarthElement(5, 5)));
        short gradeNineMaximumHealth = dealGuardianDamageToPlayer(List.of(new EarthElement(32, 32)));

        // Then
        assertEquals(901, gradeOneMaximumHealth);
        assertEquals(905, gradeNineMaximumHealth);
    }

    @Test
    void strongestDuplicateSetsPlayerDefenseCapAgainstGuardianDamage() throws Exception {
        // Given / When
        short targetHealth = dealGuardianDamageToPlayer(
                List.of(new EarthElement(5, 5), new EarthElement(32, 32)));

        // Then
        assertEquals(905, targetHealth);
    }

    @Test
    void distinctDefenseRelationshipsCombineAgainstGuardianDamage() throws Exception {
        // Given / When
        short targetHealth = dealGuardianDamageToPlayer(
                List.of(new EarthElement(32, 32), new WaterElement(32, 32)));

        // Then
        assertEquals(915, targetHealth);
    }

    private short dealGuardianDamageToPlayer(List<Elementable> defenses) throws Exception {
        MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
        GuardianBattleState attacker = guardian((short) 4);
        attacker.getElements().add(new WindElement(0, 0));
        PlayerBattleState target = new PlayerBattleState((short) 0, 100, 1000, 0, 0, 0, 0);
        target.getDefensiveElements().addAll(defenses);
        when(game.getGuardianBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(attacker)));
        when(game.getPlayerBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(target)));

        return new GuardianCombatSystem(game).dealDamageToPlayer(
                4, 0, (short) -100, false, false, elementalSkill());
    }

    private GuardianBattleState guardian(short position) {
        GuardianBase guardian = mock(GuardianBase.class);
        when(guardian.getId()).thenReturn(1L);
        when(guardian.getBtItemID()).thenReturn(0);
        return new GuardianBattleState(guardian, position, 1000, 0, 0, 0, 0, 0, 0, 0);
    }

    private Skill elementalSkill() {
        Skill skill = new Skill();
        skill.setElemental(2);
        return skill;
    }
}
