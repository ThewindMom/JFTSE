var S2CMatchplayUseSkill = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill");
var S2CMatchplayDealDamage = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayDealDamage");
var S2CChatRoomAnswerPacket = Java.type("com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket");
var PhaseUpdateResult = Java.type("com.jftse.emulator.server.core.matchplay.guardian.PhaseUpdateResult");
var AtlantisV2Rules = Java.type("com.jftse.emulator.server.core.matchplay.guardian.AtlantisV2Rules");
var GuardianShieldPadService = Java.type("com.jftse.emulator.server.core.matchplay.guardian.GuardianShieldPadService");

// Atlantis phase 2: keep the revived attendants balanced, then execute both together.
class TwinTides {
    constructor() {
        this.timeStarted = 0;
        this.finished = false;
        this.phaseStarted = false;
        this.initialRebirthPending = false;
        this.executeAnnounced = false;
        this.severity = 0;
        this.firstDeathAt = 0;
        this.lastBossAttack = 0;
        this.lastVolley = 0;
        this.lastWaterPillar = 0;
        this.lastBlizzard = 0;
        this.volleyActive = false;
        this.volleyGeneration = 0;
        this.convergenceWarningAt = 0;
        this.nextConvergenceScan = 0;
    }
}

let tides = new TwinTides();

function announce(connection, text) {
    const packet = new S2CChatRoomAnswerPacket(2, "Server", text);
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function bossGuardian() {
    return game.getGuardianBattleStates().stream()
        .filter(g => g.isBoss())
        .findFirst()
        .orElse(null);
}

function playersAlive() {
    return game.getPlayerBattleStates().stream()
        .filter(p => p != null && p.getPosition() < 4 && p.getCurrentHealth().get() > 0)
        .toArray();
}

function attendants() {
    return game.getGuardianBattleStates().stream()
        .filter(g => !g.isBoss())
        .toArray();
}

function aliveAttendants() {
    const alive = [];
    for (let add of attendants()) {
        if (add.getCurrentHealth().get() > 0) alive.push(add);
    }
    return alive;
}

function healthFraction(guardian) {
    return guardian.getCurrentHealth().get() / guardian.getMaxHealth();
}

function bothInExecuteRange() {
    const adds = attendants();
    if (adds.length !== 2) return false;
    for (let add of adds) {
        if (healthFraction(add) > AtlantisV2Rules.TWIN_TIDE_EXECUTE_HEALTH) return false;
    }
    return true;
}

function healthDelta() {
    const adds = aliveAttendants();
    if (adds.length !== 2 || bothInExecuteRange()) return 0;
    return Math.abs(healthFraction(adds[0]) - healthFraction(adds[1]));
}

function severityForDelta(delta) {
    if (delta >= AtlantisV2Rules.TWIN_TIDE_BLIZZARD_DELTA) return 3;
    if (delta >= AtlantisV2Rules.TWIN_TIDE_PILLAR_DELTA) return 2;
    if (delta >= AtlantisV2Rules.TWIN_TIDE_WAVE_DELTA) return 1;
    return 0;
}

function randomWaveX() {
    const min = AtlantisV2Rules.PHASE_ONE_WAVE_X_MIN;
    const max = AtlantisV2Rules.PHASE_ONE_WAVE_X_MAX;
    return Math.floor(min + Math.random() * (max - min + 1));
}

function randomWaveDepth() {
    const depths = AtlantisV2Rules.SEA_WAVE_DEPTHS;
    return depths[Math.floor(Math.random() * depths.length)];
}

function castSeaWave(connection, x, depth) {
    const packet = new S2CMatchplayUseSkill(
        4, 4, AtlantisV2Rules.SEA_WAVE_PACKET_ID,
        Math.floor(Math.random() * 127), x, 0, depth
    );
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function castBossSkill(connection, boss, skillId, targetAll) {
    const skill = serviceManager.getSkillService().findSkillById(skillId);
    if (!skill) return;
    if (targetAll) {
        for (let player of playersAlive()) {
            const targeted = new S2CMatchplayUseSkill(
                boss.getPosition(), player.getPosition(), skill.getId() - 1,
                Math.floor(Math.random() * 127), 0, 0, 0
            );
            gameManager.sendPacketToAllClientsInSameGameSession(targeted, connection);
        }
        return;
    }
    const packet = new S2CMatchplayUseSkill(
        boss.getPosition(), 4, skill.getId() - 1,
        Math.floor(Math.random() * 127), 0, 0, 0
    );
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function clusteredAliveCenters(connection) {
    const service = GuardianShieldPadService.getInstance();
    if (!service) return [];
    const client = connection ? connection.getClient() : null;
    const sessionId = client ? client.getGameSessionId() : null;
    if (sessionId == null) return [];

    return service.clusteredAlivePlayerCenters(
        sessionId, AtlantisV2Rules.TIDAL_CONVERGENCE_RADIUS);
}

function castConvergencePillars(connection, boss, centers) {
    const skill = serviceManager.getSkillService().findSkillById(AtlantisV2Rules.WATER_PILLAR_SKILL_ID);
    if (!skill) return false;
    for (let center of centers) {
        const packet = new S2CMatchplayUseSkill(
            boss.getPosition(), 4, skill.getId() - 1,
            Math.floor(Math.random() * 127), center.getX(), 0, center.getZ()
        );
        gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
    }
    return centers.length > 0;
}

function runTidalConvergence(connection, boss, now) {
    if (tides.convergenceWarningAt !== 0) {
        if (now - tides.convergenceWarningAt < AtlantisV2Rules.TIDAL_CONVERGENCE_WARNING_MS) return;
        tides.convergenceWarningAt = 0;
        tides.nextConvergenceScan = now + AtlantisV2Rules.TWIN_TIDE_CONVERGENCE_COOLDOWN_MS;
        const centers = clusteredAliveCenters(connection);
        if (centers.length === 0) {
            announce(connection, "The team spreads. Tidal Convergence is broken.");
            return;
        }
        if (castConvergencePillars(connection, boss, centers)) tides.lastWaterPillar = now;
        return;
    }

    if (now < tides.nextConvergenceScan) return;
    const centers = clusteredAliveCenters(connection);
    if (centers.length > 0) {
        tides.convergenceWarningAt = now;
        announce(connection, "Tidal Convergence gathers beneath grouped players — spread before the pillars rise!");
    } else {
        tides.nextConvergenceScan = now + AtlantisV2Rules.TIDAL_CONVERGENCE_SCAN_MS;
    }
}

function cancelVolley() {
    tides.volleyGeneration++;
    tides.volleyActive = false;
}

function startVolley(connection, count, now) {
    tides.volleyActive = true;
    tides.lastVolley = now;
    const generation = ++tides.volleyGeneration;
    const eventHandler = gameManager.getEventHandler();

    for (let i = 0; i < count; i++) {
        const x = randomWaveX();
        const depth = randomWaveDepth();
        const event = eventHandler.createRunnableEvent(function () {
            const session = connection.getClient() && connection.getClient().getActiveGameSession();
            if (!session || tides.finished || tides.volleyGeneration !== generation || tides.severity === 0) return;
            castSeaWave(connection, x, depth);
        }, (i + 1) * AtlantisV2Rules.PHASE_ONE_WAVE_GAP_MS);
        eventHandler.offerJS(event);
    }

    const complete = eventHandler.createRunnableEvent(function () {
        if (tides.finished || tides.volleyGeneration !== generation) return;
        tides.volleyActive = false;
    }, count * AtlantisV2Rules.PHASE_ONE_WAVE_GAP_MS);
    eventHandler.offerJS(complete);
}

function waveCountForSeverity(severity) {
    if (severity >= 3) return AtlantisV2Rules.TWIN_TIDE_BLIZZARD_WAVE_COUNT;
    if (severity >= 2) return AtlantisV2Rules.TWIN_TIDE_PILLAR_WAVE_COUNT;
    return AtlantisV2Rules.TWIN_TIDE_WAVE_COUNT;
}

function updateSeverity(connection, now) {
    const next = severityForDelta(healthDelta());
    if (next === tides.severity) return;

    tides.severity = next;
    cancelVolley();
    tides.lastVolley = 0;

    if (next === 0) {
        announce(connection, "The Twin Tides realign. The sea's punishment recedes.");
    } else if (next === 1) {
        announce(connection, "The Twin Tides drift apart — three SeaWaves gather.");
    } else if (next === 2) {
        announce(connection, "The bloodlines divide — five SeaWaves and Water Pillar answer.");
    } else {
        announce(connection, "The Twin Tides rupture — eight SeaWaves, Water Pillar, and Blizzard descend.");
    }
}

function runHazards(connection, boss, now) {
    if (now - tides.lastBossAttack >= AtlantisV2Rules.TWIN_TIDE_HOMING_BALL_MS) {
        castBossSkill(connection, boss, AtlantisV2Rules.HOMING_BALL_SKILL_ID, true);
        tides.lastBossAttack = now;
    }

    if (tides.severity >= 1 && !tides.volleyActive
            && now - tides.lastVolley >= AtlantisV2Rules.TWIN_TIDE_VOLLEY_INTERVAL_MS) {
        startVolley(connection, waveCountForSeverity(tides.severity), now);
    }
    if (tides.severity >= 2
            && now - tides.lastWaterPillar >= AtlantisV2Rules.TWIN_TIDE_WATER_PILLAR_MS) {
        castBossSkill(connection, boss, AtlantisV2Rules.WATER_PILLAR_SKILL_ID, false);
        tides.lastWaterPillar = now;
    }
    if (tides.severity >= 3
            && now - tides.lastBlizzard >= AtlantisV2Rules.TWIN_TIDE_BLIZZARD_MS) {
        castBossSkill(connection, boss, AtlantisV2Rules.BLIZZARD_SKILL_ID, false);
        tides.lastBlizzard = now;
    }
}

function broadcastRebirth(connection, boss, guardian) {
    const rebirth = serviceManager.getSkillService().findSkillById(AtlantisV2Rules.REBIRTH_SKILL_ID);
    if (!rebirth) return;
    const castPacket = new S2CMatchplayUseSkill(
        boss.getPosition(), guardian.getPosition(), rebirth.getId() - 1,
        Math.floor(Math.random() * 127), 0, 0, 0
    );
    gameManager.sendPacketToAllClientsInSameGameSession(castPacket, connection);
    const healthPacket = new S2CMatchplayDealDamage(
        guardian.getPosition(), guardian.getCurrentHealth().get(), 4,
        rebirth.getId(), 0.0, 0.0
    );
    gameManager.sendPacketToAllClientsInSameGameSession(healthPacket, connection);
}

function reviveAllForTwinTides() {
    for (let add of attendants()) {
        const revivedHealth = Math.max(1,
            Math.round(add.getMaxHealth() * AtlantisV2Rules.TWIN_TIDE_START_HEALTH));
        add.getCurrentHealth().set(revivedHealth);
    }
}

function reviveDeadAtThirtyPercent(connection, boss) {
    for (let add of attendants()) {
        if (add.getCurrentHealth().get() > 0) continue;
        const revivedHealth = Math.max(1,
            Math.round(add.getMaxHealth() * AtlantisV2Rules.TWIN_TIDE_REVIVE_HEALTH));
        add.getCurrentHealth().set(revivedHealth);
        broadcastRebirth(connection, boss, add);
    }
}

function protectedByLowerHealth(target) {
    const adds = aliveAttendants();
    if (adds.length !== 2 || bothInExecuteRange()
            || healthDelta() < AtlantisV2Rules.TWIN_TIDE_WAVE_DELTA) return false;
    let other = null;
    for (let add of adds) {
        if (add.getPosition() !== target.getPosition()) other = add;
    }
    return other && healthFraction(target) < healthFraction(other);
}

function preserveExecuteFloor(target, newHealth) {
    const other = aliveAttendants().find(g => g.getPosition() !== target.getPosition());
    const floor = Math.max(1,
        Math.round(target.getMaxHealth() * AtlantisV2Rules.TWIN_TIDE_EXECUTE_HEALTH));
    if (other && healthFraction(other) > AtlantisV2Rules.TWIN_TIDE_EXECUTE_HEALTH && newHealth < floor) {
        target.getCurrentHealth().set(floor);
        return floor;
    }
    return newHealth;
}

var phase = {
    getPhaseName: function () {
        return "Twin Tides";
    },
    getSeverity: function () {
        return tides.severity;
    },
    getFirstDeathAt: function () {
        return tides.firstDeathAt;
    },
    start: function () {
        const now = AtlantisV2Rules.now();
        tides.timeStarted = now;
        tides.phaseStarted = true;
        tides.initialRebirthPending = true;
        tides.lastBossAttack = now;
        tides.lastVolley = now;
        tides.lastWaterPillar = now;
        tides.lastBlizzard = now;
        tides.convergenceWarningAt = 0;
        tides.nextConvergenceScan = now + AtlantisV2Rules.TIDAL_CONVERGENCE_SCAN_MS;
        game.clearPlayerSupportExemptPosition();
        game.setPlayerHealSkillsDisabled(false);
        game.setPlayerShieldSkillsDisabled(false);
        reviveAllForTwinTides();
    },
    update: function (connection) {
        if (!tides.phaseStarted || this.hasEnded()) return PhaseUpdateResult.CONTINUE;
        try {
            const now = AtlantisV2Rules.now();
            const boss = bossGuardian();
            if (!boss) return PhaseUpdateResult.ERROR;
            if (tides.initialRebirthPending) {
                tides.initialRebirthPending = false;
                for (let add of attendants()) broadcastRebirth(connection, boss, add);
                announce(connection, "The severed bloodlines rise as Twin Tides. Keep their health within 10%, or the sea will punish the imbalance.");
            }
            const living = aliveAttendants().length;

            if (living === 0) {
                if (tides.firstDeathAt !== 0
                        && now - tides.firstDeathAt > AtlantisV2Rules.TWIN_TIDE_KILL_WINDOW_MS) {
                    reviveDeadAtThirtyPercent(connection, boss);
                    tides.firstDeathAt = 0;
                    announce(connection, "The execution was too slow. The Twin Tides rise again at 30% health.");
                    updateSeverity(connection, now);
                    return PhaseUpdateResult.CONTINUE;
                }
                cancelVolley();
                announce(connection, "The Twin Tides are severed. Their ward drowns with them — the Crown stands alone.");
                return PhaseUpdateResult.NEXT_PHASE;
            }

            if (living === 1) {
                if (tides.firstDeathAt === 0) {
                    tides.firstDeathAt = now;
                    cancelVolley();
                    tides.severity = 0;
                    announce(connection, "One tide falls. Sever its twin within 10 seconds, before the bloodline returns!");
                } else if (now - tides.firstDeathAt >= AtlantisV2Rules.TWIN_TIDE_KILL_WINDOW_MS) {
                    reviveDeadAtThirtyPercent(connection, boss);
                    tides.firstDeathAt = 0;
                    announce(connection, "The surviving tide restores its twin at 30% health.");
                }
            } else {
                tides.firstDeathAt = 0;
                if (bothInExecuteRange() && !tides.executeAnnounced) {
                    tides.executeAnnounced = true;
                    announce(connection, "Both tides are exposed. Execute the bloodlines together!");
                } else if (!bothInExecuteRange()) {
                    tides.executeAnnounced = false;
                }
                updateSeverity(connection, now);
            }

            runTidalConvergence(connection, boss, now);
            runHazards(connection, boss, now);
            return PhaseUpdateResult.CONTINUE;
        } catch (e) {
            log.error("Script error in 2_twin_tides.js:", e.message, e.stack || e);
            return PhaseUpdateResult.ERROR;
        }
    },
    end: function () {
        tides.finished = true;
        cancelVolley();
        game.setPlayerHealSkillsDisabled(false);
        game.setPlayerShieldSkillsDisabled(false);
    },
    phaseTime: function () {
        return AtlantisV2Rules.now() - tides.timeStarted;
    },
    playTime: function () {
        return 0;
    },
    hasEnded: function () {
        return tides.finished || (this.playTime() !== 0 && this.phaseTime() > this.playTime());
    },
    getGuardianAttackLoopTime: function () {
        return -1;
    },
    onHeal: function (target, healAmount, isGuardian) {
        if (isGuardian) {
            return game.getGuardianCombatSystem().heal(target, healAmount);
        }
        const reduced = Math.round(healAmount * AtlantisV2Rules.POST_REVIVE_HEAL_MULTIPLIER);
        return game.getPlayerCombatSystem().heal(target, reduced);
    },
    onDealDamage: function (attackingPlayer, targetGuardian, damage, hasAttackerDmgBuff, hasTargetDefBuff, skill) {
        const target = game.getGuardianBattleStateByPosition(targetGuardian);
        if (!target) return 0;
        if (target.isBoss() && aliveAttendants().length > 0) {
            return target.getCurrentHealth().get();
        }
        if (!target.isBoss() && protectedByLowerHealth(target)) {
            return target.getCurrentHealth().get();
        }
        const newHealth = game.getGuardianCombatSystem().dealDamage(
            attackingPlayer, targetGuardian, damage, hasAttackerDmgBuff, hasTargetDefBuff, skill);
        return target.isBoss() ? newHealth : preserveExecuteFloor(target, newHealth);
    },
    onDealDamageToPlayer: function (attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill) {
        return game.getGuardianCombatSystem().dealDamageToPlayer(
            attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill);
    },
    onDealDamageOnBallLoss: function (attackerPos, targetPos, hasAttackerWillBuff) {
        const target = game.getGuardianBattleStateByPosition(targetPos);
        if (!target) return 0;
        if (target.isBoss() && aliveAttendants().length > 0) {
            return target.getCurrentHealth().get();
        }
        if (!target.isBoss() && protectedByLowerHealth(target)) {
            return target.getCurrentHealth().get();
        }
        const newHealth = game.getGuardianCombatSystem().dealDamageOnBallLoss(attackerPos, targetPos, hasAttackerWillBuff);
        return target.isBoss() ? newHealth : preserveExecuteFloor(target, newHealth);
    },
    onDealDamageOnBallLossToPlayer: function (attackerPos, targetPos, hasAttackerWillBuff) {
        return game.getGuardianCombatSystem().dealDamageOnBallLossToPlayer(attackerPos, targetPos, hasAttackerWillBuff);
    }
}
