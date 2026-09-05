package com.jftse.emulator.server.core.matchplay.combat;

import com.jftse.emulator.common.exception.ValidationException;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.core.utils.BattleUtils;
import com.jftse.entities.database.model.battle.Skill;
import com.jftse.server.core.item.EElementalProperty;
import com.jftse.server.core.matchplay.Elementable;
import com.jftse.server.core.matchplay.battle.GuardianBattleState;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;

public class GuardianCombatSystem implements GuardianCombatable {
    private final MatchplayGuardianGame game;

    public GuardianCombatSystem(MatchplayGuardianGame game) {
        this.game = game;
    }

    @Override
    public short dealDamage(int attackerPos, int targetPos, short damage, boolean hasAttackerDmgBuff, boolean hasTargetDefBuff, Skill skill) throws ValidationException {
        int totalDamageToDeal = damage;
        PlayerBattleState attackingPlayer = game.getPlayerBattleStates().stream()
                .filter(x -> x.getPosition() == attackerPos)
                .findFirst()
                .orElse(null);

        boolean isNormalDamageSkill = Math.abs(damage) != 1;
        if (attackingPlayer != null && isNormalDamageSkill) {
            totalDamageToDeal = BattleUtils.calculateDmg(attackingPlayer.getStr(), damage, hasAttackerDmgBuff);
        }

        GuardianBattleState targetGuardian = game.getGuardianBattleStates().stream()
                .filter(x -> x.getPosition() == targetPos)
                .findFirst()
                .orElse(null);

        if (targetGuardian == null)
            throw new ValidationException("targetGuardian battle state is null");

        if (isNormalDamageSkill) {
            int damageToDeny = BattleUtils.calculateDef(targetGuardian.getSta(), Math.abs(totalDamageToDeal), hasTargetDefBuff);
            if (damageToDeny > Math.abs(totalDamageToDeal)) {
                totalDamageToDeal = -1;
            } else {
                totalDamageToDeal += damageToDeny;
            }

            Elementable offensiveElement = attackingPlayer != null ? attackingPlayer.getOffensiveElement() : null;

            if (totalDamageToDeal != -1 && offensiveElement != null && skill != null && offensiveElement.getProperty() == EElementalProperty.fromValue(skill.getElemental().byteValue())) {
                double efficiency = ElementalEfficiencyCalculator.calculate(
                        offensiveElement.getEfficiency(),
                        offensiveElement,
                        targetGuardian.getDefensiveElements(),
                        ElementalEfficiencyCalculator.GUARDIAN_PROFILE);

                final double efficiencyMultiplier = 1 + (efficiency / 100.0);
                totalDamageToDeal =  (int) (totalDamageToDeal * efficiencyMultiplier);
            }
        }

        return updateHealthByDamage(targetGuardian, totalDamageToDeal);
    }

    @Override
    public short dealDamageOnBallLoss(int attackerPos, int targetPos, boolean hasAttackerWillBuff) throws ValidationException {
        GuardianBattleState targetGuardian = game.getGuardianBattleStates().stream()
                .filter(x -> x.getPosition() == targetPos)
                .findFirst()
                .orElse(null);

        if (targetGuardian == null)
            throw new ValidationException("targetGuardian battle state is null");

        int lossBallDamage = 0;
        boolean servingGuardianScored = attackerPos == 4;
        if (servingGuardianScored) {
            lossBallDamage = (short) -(targetGuardian.getMaxHealth() * 0.02);
        } else {
            PlayerBattleState attackingPlayer = game.getPlayerBattleStates().stream()
                    .filter(x -> x.getPosition() == attackerPos)
                    .findFirst()
                    .orElse(null);
            if (attackingPlayer != null) {
                int playerWill = attackingPlayer.getWill();
                lossBallDamage = -BattleUtils.calculateBallDmg(playerWill, hasAttackerWillBuff);

                int additionalWillDmg = (int) (targetGuardian.getMaxHealth() * (playerWill / 10000d));
                lossBallDamage -= additionalWillDmg;
            }
        }

        return updateHealthByDamage(targetGuardian, lossBallDamage);
    }

    @Override
    public short heal(int targetPos, short percentage) throws ValidationException {
        GuardianBattleState targetGuardian = game.getGuardianBattleStates().stream()
                .filter(x -> x.getPosition() == targetPos)
                .findFirst()
                .orElse(null);

        if (targetGuardian == null)
            throw new ValidationException("targetGuardian battle state is null");

        percentage = game.getGuardianHealPercentage();

        short healthToHeal = (short) (targetGuardian.getMaxHealth() * (percentage / 100f));
        return (short) targetGuardian.getCurrentHealth().updateAndGet(current ->
                Math.min((short) (Math.max(current, 0) + healthToHeal), targetGuardian.getMaxHealth()));
    }

    @Override
    public short dealDamageToPlayer(int attackerPos, int targetPos, short damage, boolean hasAttackerDmgBuff, boolean hasTargetDefBuff, Skill skill) throws ValidationException {
        int totalDamageToDeal = damage;
        GuardianBattleState attackingGuardian = game.getGuardianBattleStates().stream()
                .filter(x -> x.getPosition() == attackerPos)
                .findFirst()
                .orElse(null);

        boolean isNormalDamageSkill = Math.abs(damage) != 1;
        if (attackingGuardian != null && isNormalDamageSkill) {
            totalDamageToDeal = BattleUtils.calculateDmg(attackingGuardian.getStr(), damage, hasAttackerDmgBuff);
        }

        Elementable offensiveElement;
        if (attackingGuardian != null) {
            offensiveElement = attackingGuardian.getElements().stream()
                    .filter(x -> skill != null && x.getProperty() == EElementalProperty.fromValue(skill.getElemental().byteValue()))
                    .findFirst()
                    .orElse(null);
        } else {
            offensiveElement = null;
        }

        PlayerBattleState targetPlayer = game.getPlayerBattleStates().stream()
                .filter(x -> x.getPosition() == targetPos)
                .findFirst()
                .orElse(null);

        if (targetPlayer == null)
            throw new ValidationException("targetPlayer battle state is null");

        if (isNormalDamageSkill) {
            int damageToDeny = BattleUtils.calculateDef(targetPlayer.getSta(), Math.abs(totalDamageToDeal), hasTargetDefBuff);
            if (damageToDeny > Math.abs(totalDamageToDeal)) {
                totalDamageToDeal = -1;
            } else {
                totalDamageToDeal += damageToDeny;
            }

            if (totalDamageToDeal != -1 && offensiveElement != null && offensiveElement.getProperty() == EElementalProperty.fromValue(skill.getElemental().byteValue())) {
                double efficiency = ElementalEfficiencyCalculator.calculate(
                        offensiveElement.getEfficiency(),
                        offensiveElement,
                        targetPlayer.getDefensiveElements(),
                        ElementalEfficiencyCalculator.GUARDIAN_PROFILE);

                final double efficiencyMultiplier = 1 + (efficiency / 100.0);
                totalDamageToDeal =  (int) (totalDamageToDeal * efficiencyMultiplier);
            }
        }

        return updateHealthByDamage(targetPlayer, totalDamageToDeal);
    }

    @Override
    public short dealDamageOnBallLossToPlayer(int attackerPos, int targetPos, boolean hasAttackerWillBuff) throws ValidationException {
        PlayerBattleState targetPlayer = game.getPlayerBattleStates().stream()
                .filter(x -> x.getPosition() == targetPos)
                .findFirst()
                .orElse(null);

        if (targetPlayer == null)
            throw new ValidationException("targetPlayer battle state is null");

        int lossBallDamage = 0;
        boolean servingGuardianScored = attackerPos == 4;
        if (servingGuardianScored) {
            lossBallDamage = (short) -(targetPlayer.getMaxHealth() * 0.02);
        } else {
            GuardianBattleState attackingGuardian = game.getGuardianBattleStates().stream()
                    .filter(x -> x.getPosition() == attackerPos)
                    .findFirst()
                    .orElse(null);
            if (attackingGuardian != null) {
                lossBallDamage = -BattleUtils.calculateBallDmg(attackingGuardian.getWill(), hasAttackerWillBuff);
            }
        }

        return updateHealthByDamage(targetPlayer, lossBallDamage);
    }

    @Override
    public short updateHealthByDamage(GuardianBattleState targetGuardian, int dmg) {
        return (short) targetGuardian.getCurrentHealth().updateAndGet(current ->
                Math.min(targetGuardian.getMaxHealth(), Math.max(0, (short) (Math.max(current, 0) + dmg))));
    }

    @Override
    public short updateHealthByDamage(PlayerBattleState targetPlayer, int dmg) {
        short newPlayerHealth = (short) targetPlayer.getCurrentHealth().updateAndGet(current ->
                Math.min(targetPlayer.getMaxHealth(), Math.max(0, (short) (Math.max(current, 0) + dmg))));
        if (newPlayerHealth < 1) {
            targetPlayer.setDead(true);
        }
        return newPlayerHealth;
    }

    @Override
    public GuardianBattleState reviveAnyGuardian(short revivePercentage) throws ValidationException {
        GuardianBattleState guardianBattleState = game.getGuardianBattleStates().stream()
                .filter(x -> x.getCurrentHealth().get() < 1)
                .findFirst()
                .orElse(null);

        if (guardianBattleState != null) {
            revivePercentage = game.getGuardianHealPercentage();
            heal(guardianBattleState.getPosition(), revivePercentage);
        }

        return guardianBattleState;
    }
}
