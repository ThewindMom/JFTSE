package com.jftse.emulator.server.core.matchplay.guardian;

import com.jftse.emulator.common.scripting.ScriptFile;
import com.jftse.emulator.common.scripting.ScriptManagerV2;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.battle.Skill;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Getter
@Setter
@Log4j2
public class PhaseScript {
    private final BossBattlePhaseable phase;
    private final ScriptFile scriptFile;
    private final ScriptManagerV2 scriptManager;
    private final Lock lock = new ReentrantLock();

    public PhaseScript(BossBattlePhaseable phase, ScriptFile scriptFile, ScriptManagerV2 scriptManager) {
        this.phase = phase;
        this.scriptFile = scriptFile;
        this.scriptManager = scriptManager;
    }

    private <T> T call(String action, T fallback, PhaseCallable<T> callable) {
        lock.lock();
        try {
            T result = scriptManager.callOnScriptThread(scriptFile, callable::call);
            return result == null ? fallback : result;
        } catch (Exception e) {
            log.error("Phase script call failed. script={}, action={}, error={}",
                    scriptFile.getScriptKey(), action, e.getMessage(), e);
            return fallback;
        } finally {
            lock.unlock();
        }
    }

    private void run(String action, PhaseRunnable runnable) {
        lock.lock();
        try {
            scriptManager.callOnScriptThread(scriptFile, () -> {
                runnable.run();
                return null;
            });
        } catch (Exception e) {
            log.error("Phase script runnable failed. script={}, action={}, error={}",
                    scriptFile.getScriptKey(), action, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public String getPhaseName() {
        return call("getPhaseName", scriptFile.getName(), phase::getPhaseName);
    }

    public void start() {
        run("start", phase::start);
    }

    public PhaseUpdateResult update(FTConnection connection) {
        return call("update", PhaseUpdateResult.ERROR, () -> phase.update(connection));
    }

    public void end() {
        run("end", phase::end);
    }

    public long phaseTime() {
        return call("phaseTime", 0L, phase::phaseTime);
    }

    public long playTime() {
        return call("playTime", 0L, phase::playTime);
    }

    public boolean hasEnded() {
        return call("hasEnded", false, phase::hasEnded);
    }

    public long getGuardianAttackLoopTime(AdvancedGuardianState guardian) {
        return call("getGuardianAttackLoopTime", MatchplayGuardianGame.guardianAttackLoopTime,
                () -> phase.getGuardianAttackLoopTime(guardian));
    }

    public int onHeal(int target, int healAmount, boolean isGuardian) {
        return call("onHeal", healAmount, () -> phase.onHeal(target, healAmount, isGuardian));
    }

    public int onDealDamage(int attackingPlayer, int targetGuardian, int damage,
                            boolean hasAttackerDmgBuff, boolean hasTargetDefBuff, Skill skill) {
        return call("onDealDamage", damage,
                () -> phase.onDealDamage(attackingPlayer, targetGuardian, damage, hasAttackerDmgBuff, hasTargetDefBuff, skill));
    }

    public int onDealDamageToPlayer(int attackingGuardian, int targetPlayer, int damageAmount,
                                    boolean hasAttackerDmgBuff, boolean hasTargetDefBuff, Skill skill) {
        return call("onDealDamageToPlayer", damageAmount,
                () -> phase.onDealDamageToPlayer(attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill));
    }

    public int onDealDamageOnBallLoss(int attackerPos, int targetPos, boolean hasAttackerWillBuff) {
        return call("onDealDamageOnBallLoss", 0,
                () -> phase.onDealDamageOnBallLoss(attackerPos, targetPos, hasAttackerWillBuff));
    }

    public int onDealDamageOnBallLossToPlayer(int attackerPos, int targetPos, boolean hasAttackerWillBuff) {
        return call("onDealDamageOnBallLossToPlayer", 0,
                () -> phase.onDealDamageOnBallLossToPlayer(attackerPos, targetPos, hasAttackerWillBuff));
    }

    @FunctionalInterface
    private interface PhaseCallable<T> {
        T call() throws Exception;
    }

    @FunctionalInterface
    private interface PhaseRunnable {
        void run() throws Exception;
    }
}
