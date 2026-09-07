package com.jftse.server.core.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record CardStats(int hp, int strength, int stamina, int dexterity, int willpower,
                        List<Integer> attackElements, List<Integer> defenseElements) {
    private static final int ELEMENT_COUNT = 8;
    private static final CardStats ZERO = new CardStats(0, 0, 0, 0, 0,
            Collections.nCopies(ELEMENT_COUNT, 0), Collections.nCopies(ELEMENT_COUNT, 0));

    public CardStats {
        attackElements = immutableElements(attackElements);
        defenseElements = immutableElements(defenseElements);
    }

    public static CardStats zero() {
        return ZERO;
    }

    public CardStats add(String itemType, int abilityPower) {
        int newHp = hp;
        int newStrength = strength;
        int newStamina = stamina;
        int newDexterity = dexterity;
        int newWillpower = willpower;
        List<Integer> newAttack = new ArrayList<>(attackElements);
        List<Integer> newDefense = new ArrayList<>(defenseElements);

        switch (itemType) {
            case "HP" -> newHp = saturatingIntAdd(newHp, abilityPower);
            case "STR" -> newStrength = saturatingByteAdd(newStrength, abilityPower);
            case "STA" -> newStamina = saturatingByteAdd(newStamina, abilityPower);
            case "DEX" -> newDexterity = saturatingByteAdd(newDexterity, abilityPower);
            case "WIL" -> newWillpower = saturatingByteAdd(newWillpower, abilityPower);
            case "ATT_EARTH" -> addElement(newAttack, 0, abilityPower);
            case "ATT_WIND" -> addElement(newAttack, 1, abilityPower);
            case "ATT_WATER" -> addElement(newAttack, 2, abilityPower);
            case "ATT_FIRE" -> addElement(newAttack, 3, abilityPower);
            case "DEF_EARTH" -> addElement(newDefense, 0, abilityPower);
            case "DEF_WIND" -> addElement(newDefense, 1, abilityPower);
            case "DEF_WATER" -> addElement(newDefense, 2, abilityPower);
            case "DEF_FIRE" -> addElement(newDefense, 3, abilityPower);
            default -> { }
        }
        return new CardStats(newHp, newStrength, newStamina, newDexterity, newWillpower,
                newAttack, newDefense);
    }

    public static boolean supports(String itemType) {
        return switch (itemType) {
            case "HP", "STR", "STA", "DEX", "WIL",
                    "ATT_EARTH", "ATT_WIND", "ATT_WATER", "ATT_FIRE",
                    "DEF_EARTH", "DEF_WIND", "DEF_WATER", "DEF_FIRE" -> true;
            default -> false;
        };
    }

    public static int saturateByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static void addElement(List<Integer> elements, int index, int power) {
        elements.set(index, saturatingByteAdd(elements.get(index), power));
    }

    private static int saturatingByteAdd(int value, int amount) {
        return (int) Math.max(0L, Math.min(255L, (long) value + amount));
    }

    private static int saturatingIntAdd(int value, int amount) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, (long) value + amount));
    }

    private static List<Integer> immutableElements(List<Integer> elements) {
        if (elements == null || elements.size() != ELEMENT_COUNT)
            throw new IllegalArgumentException("Card elemental stats must contain exactly 8 values");
        return List.copyOf(elements);
    }
}
