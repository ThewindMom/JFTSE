package com.jftse.emulator.server.core.matchplay.combat;

import com.jftse.server.core.matchplay.EarthElement;
import com.jftse.server.core.matchplay.Elementable;
import com.jftse.server.core.matchplay.FireElement;
import com.jftse.server.core.matchplay.WaterElement;
import com.jftse.server.core.matchplay.WindElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElementalEfficiencyCalculatorTest {
    private static final double TOLERANCE = 1e-9;

    @Test
    void preservesOffensiveEfficiencyWhenDefenseHasNoMatchingRelationship() {
        // Given
        Elementable offense = new WindElement(0, 0);
        List<Elementable> defenses = List.of();

        // When
        double efficiency = ElementalEfficiencyCalculator.calculate(
                12.5, offense, defenses, ElementalEfficiencyCalculator.PLAYER_PROFILE);

        // Then
        assertEquals(12.5, efficiency, TOLERANCE);
    }

    @Test
    void scalesWeakDefenseByEnchantEfficiencyUpToTheExistingCap() {
        // Given
        Elementable offense = new WindElement(0, 0);

        // When
        double gradeZero = calculatePlayerEfficiency(offense, new EarthElement(0, 0));
        double gradeEight = calculatePlayerEfficiency(offense, new EarthElement(8, 8));
        double gradeNineMaximum = calculatePlayerEfficiency(offense, new EarthElement(32, 32));
        double overflow = calculatePlayerEfficiency(offense, new EarthElement(80, 80));

        // Then
        assertEquals(0.0, gradeZero, TOLERANCE);
        assertEquals(-3.75, gradeEight, TOLERANCE);
        assertEquals(-15.0, gradeNineMaximum, TOLERANCE);
        assertEquals(-15.0, overflow, TOLERANCE);
    }

    @Test
    void scalesUnfavorableDefenseTowardThePositiveStrongCap() {
        // Given
        Elementable offense = new WindElement(0, 0);
        Elementable vulnerableDefense = new FireElement(32, 32);

        // When
        double efficiency = calculatePlayerEfficiency(offense, vulnerableDefense);

        // Then
        assertEquals(26.0, efficiency, TOLERANCE);
    }

    @Test
    void combinesDistinctWeakAndResistantDefenseCategories() {
        // Given
        Elementable offense = new WindElement(0, 0);
        List<Elementable> defenses = List.of(new EarthElement(32, 32), new WaterElement(32, 32));

        // When
        double playerEfficiency = ElementalEfficiencyCalculator.calculate(
                0, offense, defenses, ElementalEfficiencyCalculator.PLAYER_PROFILE);
        double guardianEfficiency = ElementalEfficiencyCalculator.calculate(
                0, offense, defenses, ElementalEfficiencyCalculator.GUARDIAN_PROFILE);

        // Then
        assertEquals(-35.0, playerEfficiency, TOLERANCE);
        assertEquals(-15.0, guardianEfficiency, TOLERANCE);
    }

    @Test
    void selectsStrongestConfiguredDuplicateWithoutRollingOrStackingWeakerGear() {
        // Given
        Elementable offense = mock(Elementable.class);
        Elementable weakerEarth = mock(Elementable.class);
        Elementable strongerEarth = mock(Elementable.class);
        when(offense.isWeakAgainst(weakerEarth)).thenReturn(true);
        when(offense.isWeakAgainst(strongerEarth)).thenReturn(true);
        when(weakerEarth.getMaxEfficiency()).thenReturn(8.0);
        when(strongerEarth.getMaxEfficiency()).thenReturn(32.0);
        when(strongerEarth.getEfficiency()).thenReturn(32.0);

        // When
        double efficiency = ElementalEfficiencyCalculator.calculate(
                0,
                offense,
                List.of(weakerEarth, strongerEarth),
                ElementalEfficiencyCalculator.PLAYER_PROFILE);

        // Then
        assertEquals(-15.0, efficiency, TOLERANCE);
        verify(strongerEarth).getEfficiency();
        verify(weakerEarth, never()).getEfficiency();
    }

    @Test
    void duplicateOrderingDoesNotChangeTheSelectedDefense() {
        // Given
        Elementable offense = new WindElement(0, 0);
        Elementable weakerEarth = new EarthElement(8, 8);
        Elementable strongerEarth = new EarthElement(32, 32);

        // When
        double weakFirst = ElementalEfficiencyCalculator.calculate(
                0,
                offense,
                List.of(weakerEarth, strongerEarth),
                ElementalEfficiencyCalculator.PLAYER_PROFILE);
        double strongFirst = ElementalEfficiencyCalculator.calculate(
                0,
                offense,
                List.of(strongerEarth, weakerEarth),
                ElementalEfficiencyCalculator.PLAYER_PROFILE);

        // Then
        assertEquals(-15.0, weakFirst, TOLERANCE);
        assertEquals(weakFirst, strongFirst, TOLERANCE);
    }

    private static double calculatePlayerEfficiency(Elementable offense, Elementable defense) {
        return ElementalEfficiencyCalculator.calculate(
                0, offense, List.of(defense), ElementalEfficiencyCalculator.PLAYER_PROFILE);
    }
}
