package com.jftse.emulator.server.core.matchplay.combat;

import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.core.utils.BattleUtils;
import com.jftse.server.core.matchplay.Elementable;
import com.jftse.server.core.matchplay.battle.GuardianBattleState;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import com.jftse.server.core.shared.ServerConfService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CombatCalculationContractTest {
    private Object previousManager;
    private Object previousConfig;

    @BeforeEach
    void loadDefaultScales() {
        previousManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        previousConfig = ((AtomicReference<?>) ReflectionTestUtils.getField(BattleUtils.class, "statConfig")).get();
        GameManager manager = mock(GameManager.class);
        ServerConfService config = mock(ServerConfService.class);
        when(manager.getServerConfService()).thenReturn(config);
        when(config.get("StrengthDamageScale", Double.class)).thenReturn(0.35);
        when(config.get("StaminaDamageReductionScale", Double.class)).thenReturn(0.30);
        when(config.get("WillpowerBallDamageScale", Double.class)).thenReturn(0.52);
        when(config.get("BallBaseDamage", Integer.class)).thenReturn(10);
        when(config.get("BallMinDamage", Integer.class)).thenReturn(20);
        ReflectionTestUtils.setField(GameManager.class, "instance", manager);
        BattleUtils.reloadStatConfig();
    }

    @AfterEach
    void restoreStatics() {
        ReflectionTestUtils.setField(GameManager.class, "instance", previousManager);
        ReflectionTestUtils.invokeMethod(ReflectionTestUtils.getField(BattleUtils.class, "statConfig"), "set", previousConfig);
    }

    @ParameterizedTest
    @CsvSource({"10,10,false,false,80", "10,10,true,false,76", "10,10,false,true,84",
            "0,67,false,false,100", "0,70,false,false,99"})
    void strengthThenDefenseUsesTruncationAndStrictGreaterThanFloor(
            int strength, int stamina, boolean attackBuff, boolean defenseBuff, int expectedHp) throws Exception {
        MatchplayBattleGame game = mock(MatchplayBattleGame.class);
        PlayerBattleState attacker = new PlayerBattleState((short) 0, 1, 100, strength, 0, 0, 0);
        PlayerBattleState target = new PlayerBattleState((short) 1, 2, 100, 0, stamina, 0, 0);
        when(game.getPlayerBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(attacker, target)));

        assertEquals(expectedHp, new PlayerCombatSystem(game).dealDamage(0, 1, (short) -20,
                attackBuff, defenseBuff, null));
        assertEquals(expectedHp, target.getCurrentHealth().get());
    }

    @ParameterizedTest
    @CsvSource({"0,false,20", "19,false,20", "20,false,20", "22,false,21", "22,true,25"})
    void ballDamageAppliesBuffBeforeMinimum(int will, boolean buff, int expected) {
        assertEquals(expected, BattleUtils.calculateBallDmg(will, buff));
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "0,0", "16,13", "32,26", "64,26"})
    void defensiveEfficiencyNormalizesActualValueAndCapsAt32(double efficiency, double expected) {
        Elementable offense = mock(Elementable.class);
        Elementable defense = mock(Elementable.class);
        when(offense.isStrongAgainst(any())).thenReturn(true);
        when(defense.getEfficiency()).thenReturn(efficiency);

        assertEquals(expected, ElementalEfficiencyCalculator.calculate(0, offense, List.of(defense),
                ElementalEfficiencyCalculator.PLAYER_PROFILE));
    }

    @Test
    void fractionalHealTruncatesAndCapsAtMaximum() throws Exception {
        MatchplayBattleGame game = mock(MatchplayBattleGame.class);
        PlayerBattleState target = new PlayerBattleState((short) 0, 1, 205, 0, 0, 0, 0);
        target.getCurrentHealth().set(100);
        when(game.getPlayerBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(target)));
        PlayerCombatSystem combat = new PlayerCombatSystem(game);

        assertEquals(120, combat.heal(0, (short) 10));
        assertEquals(205, combat.heal(0, (short) 100));
        assertEquals(200, BattleUtils.calculatePlayerHp(1));
        assertEquals(205, BattleUtils.calculatePlayerHp(2));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void concurrentAcceptedDamageDoesNotLoseHealthUpdates(boolean guardianMode) {
        PlayerBattleState target = new PlayerBattleState((short) 0, 1, 30000, 0, 0, 0, 0);
        PlayerCombatSystem playerCombat = new PlayerCombatSystem(mock(MatchplayBattleGame.class));
        GuardianCombatSystem guardianCombat = new GuardianCombatSystem(mock(MatchplayGuardianGame.class));
        java.util.stream.IntStream.range(0, 10000).parallel().forEach(i -> {
            if (guardianMode) {
                guardianCombat.updateHealthByDamage(target, -1);
            } else {
                playerCombat.updateHealthByDamage(target, -1);
            }
        });
        assertEquals(20000, target.getCurrentHealth().get());
    }

    @Test
    void guardianHealUsesGamePercentageInsteadOfRequestedPercentage() throws Exception {
        MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
        GuardianBattleState guardian = mock(GuardianBattleState.class);
        when(guardian.getPosition()).thenReturn(10);
        when(guardian.getMaxHealth()).thenReturn(100);
        when(guardian.getCurrentHealth()).thenReturn(new AtomicInteger(10));
        when(game.getGuardianBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(guardian)));
        when(game.getGuardianHealPercentage()).thenReturn((short) 5);

        assertEquals(15, new GuardianCombatSystem(game).heal(10, (short) 90));
        assertEquals(15, guardian.getCurrentHealth().get());
    }
}
