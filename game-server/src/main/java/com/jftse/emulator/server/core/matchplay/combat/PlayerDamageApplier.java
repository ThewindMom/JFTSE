package com.jftse.emulator.server.core.matchplay.combat;

import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import lombok.extern.log4j.Log4j2;

/**
 * Shared player HP application. A one-shot {@code BattleState.shieldActive} absorbs
 * incoming (negative) HP damage and then clears.
 */
@Log4j2
public final class PlayerDamageApplier {
    private PlayerDamageApplier() {
    }

    public static short updateHealthByDamage(PlayerBattleState targetPlayer, int dmg) {
        if (dmg < 0 && targetPlayer.isShieldActive()) {
            targetPlayer.setShieldActive(false);
            int currentHealth = Math.max(targetPlayer.getCurrentHealth().get(), 0);
            log.info("One-shot shield absorbed incoming HP damage for playerId={} position={} dmg={}",
                    targetPlayer.getId(), targetPlayer.getPosition(), dmg);
            return (short) currentHealth;
        }

        int currentHealth = targetPlayer.getCurrentHealth().get();
        currentHealth = Math.max(currentHealth, 0);
        short newPlayerHealth = (short) (currentHealth + dmg);
        if (newPlayerHealth < 1) {
            targetPlayer.setDead(true);
        }
        newPlayerHealth = newPlayerHealth < 0 ? 0 : newPlayerHealth;

        if (targetPlayer.getCurrentHealth().compareAndSet(currentHealth, newPlayerHealth))
            return newPlayerHealth;
        else
            return (short) currentHealth;
    }
}
