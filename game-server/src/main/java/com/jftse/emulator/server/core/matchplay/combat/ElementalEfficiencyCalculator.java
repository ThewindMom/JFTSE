package com.jftse.emulator.server.core.matchplay.combat;

import com.jftse.server.core.matchplay.Elementable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class ElementalEfficiencyCalculator {
    public static final ModifierProfile PLAYER_PROFILE = new ModifierProfile(26, -15, -20);
    public static final ModifierProfile GUARDIAN_PROFILE = new ModifierProfile(16, -5, -10);

    private static final double MAX_DEFENSIVE_EFFICIENCY = 32.0;

    private ElementalEfficiencyCalculator() {
    }

    public static double calculate(double offensiveEfficiency,
                                   Elementable offensiveElement,
                                   List<Elementable> defensiveElements,
                                   ModifierProfile profile) {
        double strongContribution = contribution(
                defensiveElements, offensiveElement::isStrongAgainst, profile.strong());
        double weakContribution = contribution(
                defensiveElements, offensiveElement::isWeakAgainst, profile.weak());
        double resistantContribution = contribution(
                defensiveElements,
                defensiveElement -> defensiveElement.isResistantTo(offensiveElement),
                profile.resistant());

        return offensiveEfficiency + strongContribution + weakContribution + resistantContribution;
    }

    private static double contribution(List<Elementable> defensiveElements,
                                       Predicate<Elementable> relationship,
                                       int modifier) {
        return defensiveElements.stream()
                .filter(relationship)
                .max(Comparator.comparingDouble(Elementable::getMaxEfficiency))
                .map(element -> modifier * normalizedEfficiency(element))
                .orElse(0.0);
    }

    private static double normalizedEfficiency(Elementable element) {
        return Math.clamp(element.getEfficiency() / MAX_DEFENSIVE_EFFICIENCY, 0.0, 1.0);
    }

    public record ModifierProfile(int strong, int weak, int resistant) {
    }
}
