var S2CMatchplayUseSkill = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill");
var S2CMatchplayDealDamage = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayDealDamage");
var S2CChatRoomAnswerPacket = Java.type("com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket");
var PhaseUpdateResult = Java.type("com.jftse.emulator.server.core.matchplay.guardian.PhaseUpdateResult");
var AtlantisV2Rules = Java.type("com.jftse.emulator.server.core.matchplay.guardian.AtlantisV2Rules");
var GuardianShieldPadService = Java.type("com.jftse.emulator.server.core.matchplay.guardian.GuardianShieldPadService");

var CALM = "CALM";
var BLOOD_TIDE = "BLOOD_TIDE";
var CROWNLESS = "CROWNLESS";
var FINAL_PROCESSION = "FINAL_PROCESSION";
var FINAL_POINT = "FINAL_POINT";

class DrownedCrown {
    constructor() {
        this.timeStarted = 0;
        this.stateStarted = 0;
        this.phaseStarted = false;
        this.finished = false;
        this.introPending = false;
        this.bloodTidePending = false;
        this.healthBroadcastPending = false;
        this.inheritedPowers = 0;
        this.deadSeen = new Map();
        this.nextAddAttack = new Map();
        this.lastHomingBall = 0;
        this.lastWaterPillar = 0;
        this.lastBlizzard = 0;
        this.lastStorm = 0;
        this.lastAnyAttack = 0;
        this.processionWaves = 0;
        this.nextProcessionWave = 0;
        this.convergenceWarningAt = 0;
        this.nextConvergenceScan = 0;
        this.victoryAnnounced = false;
        this.state = CALM;
    }
}

let crown = new DrownedCrown();

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

function attendants() {
    return game.getGuardianBattleStates().stream()
        .filter(g => !g.isBoss())
        .toArray();
}

function livingAttendants() {
    return game.getGuardianBattleStates().stream()
        .filter(g => !g.isBoss() && g.getCurrentHealth().get() > 0)
        .toArray();
}

function playersAlive() {
    return game.getPlayerBattleStates().stream()
        .filter(p => p != null && p.getPosition() < 4 && p.getCurrentHealth().get() > 0)
        .toArray();
}

function randomTarget(values) {
    return values[Math.floor(Math.random() * values.length)];
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

function randomAddInterval() {
    const min = AtlantisV2Rules.BLOOD_TIDE_ADD_ATTACK_MIN_MS;
    const max = AtlantisV2Rules.BLOOD_TIDE_ADD_ATTACK_MAX_MS;
    return min + Math.floor(Math.random() * (max - min + 1));
}

function broadcastHealth(connection, guardian, skillId) {
    const packet = new S2CMatchplayDealDamage(
        guardian.getPosition(), guardian.getCurrentHealth().get(), 4,
        skillId, 0.0, 0.0
    );
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function broadcastRebirth(connection, boss, guardian) {
    const rebirth = serviceManager.getSkillService().findSkillById(AtlantisV2Rules.REBIRTH_SKILL_ID);
    if (!rebirth) return;
    const castPacket = new S2CMatchplayUseSkill(
        boss.getPosition(), guardian.getPosition(), rebirth.getId() - 1,
        Math.floor(Math.random() * 127), 0, 0, 0
    );
    gameManager.sendPacketToAllClientsInSameGameSession(castPacket, connection);
    broadcastHealth(connection, guardian, rebirth.getId());
}

function castSeaWave(connection) {
    const packet = new S2CMatchplayUseSkill(
        4, 4, AtlantisV2Rules.SEA_WAVE_PACKET_ID,
        Math.floor(Math.random() * 127), randomWaveX(), 0, randomWaveDepth()
    );
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function castSkill(connection, guardian, skillId, targetAll) {
    const skill = serviceManager.getSkillService().findSkillById(skillId);
    if (!skill) return false;

    const players = playersAlive();
    if (players.length === 0) return false;
    if (targetAll) {
        for (let player of players) {
            const packet = new S2CMatchplayUseSkill(
                guardian.getPosition(), player.getPosition(), skill.getId() - 1,
                Math.floor(Math.random() * 127), 0, 0, 0
            );
            gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
        }
        return true;
    }

    const target = randomTarget(players);
    const packet = new S2CMatchplayUseSkill(
        guardian.getPosition(), target.getPosition(), skill.getId() - 1,
        Math.floor(Math.random() * 127), 0, 0, 0
    );
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
    return true;
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
    if (crown.convergenceWarningAt !== 0) {
        if (now - crown.convergenceWarningAt < AtlantisV2Rules.TIDAL_CONVERGENCE_WARNING_MS) return;
        crown.convergenceWarningAt = 0;
        crown.nextConvergenceScan = now + AtlantisV2Rules.DROWNED_CROWN_CONVERGENCE_COOLDOWN_MS;
        const centers = clusteredAliveCenters(connection);
        if (centers.length === 0) {
            announce(connection, "The team spreads. Tidal Convergence is broken.");
            return;
        }
        if (castConvergencePillars(connection, boss, centers)) {
            crown.lastWaterPillar = now;
            crown.lastAnyAttack = now;
        }
        return;
    }

    if (now < crown.nextConvergenceScan) return;
    const centers = clusteredAliveCenters(connection);
    if (centers.length > 0) {
        crown.convergenceWarningAt = now;
        announce(connection, "Tidal Convergence gathers beneath grouped players — spread before the pillars rise!");
    } else {
        crown.nextConvergenceScan = now + AtlantisV2Rules.TIDAL_CONVERGENCE_SCAN_MS;
    }
}

function resetBossTimers(now) {
    crown.lastHomingBall = now;
    crown.lastWaterPillar = now;
    crown.lastBlizzard = now;
    crown.lastStorm = now;
    crown.lastAnyAttack = now;
}

function runBossAttacks(connection, boss, now, finalPoint) {
    if (now - crown.lastAnyAttack < AtlantisV2Rules.BLOOD_TIDE_MIN_ATTACK_GAP_MS) return;

    const stormMs = finalPoint
        ? AtlantisV2Rules.FINAL_POINT_STORM_MS
        : AtlantisV2Rules.BLOOD_TIDE_STORM_MS;
    const blizzardMs = finalPoint
        ? AtlantisV2Rules.FINAL_POINT_BLIZZARD_MS
        : AtlantisV2Rules.BLOOD_TIDE_BLIZZARD_MS;
    const pillarMs = finalPoint
        ? AtlantisV2Rules.FINAL_POINT_WATER_PILLAR_MS
        : AtlantisV2Rules.BLOOD_TIDE_WATER_PILLAR_MS;
    const homingMs = finalPoint
        ? AtlantisV2Rules.FINAL_POINT_HOMING_BALL_MS
        : AtlantisV2Rules.BLOOD_TIDE_HOMING_BALL_MS;

    if ((finalPoint || crown.inheritedPowers >= 2)
            && now - crown.lastStorm >= stormMs) {
        if (castSkill(connection, boss, AtlantisV2Rules.STORM_SKILL_ID, false)) {
            crown.lastStorm = now;
            crown.lastAnyAttack = now;
        }
        return;
    }
    if ((finalPoint || crown.inheritedPowers >= 1)
            && now - crown.lastBlizzard >= blizzardMs) {
        if (castSkill(connection, boss, AtlantisV2Rules.BLIZZARD_SKILL_ID, false)) {
            crown.lastBlizzard = now;
            crown.lastAnyAttack = now;
        }
        return;
    }
    if (now - crown.lastWaterPillar >= pillarMs) {
        if (castSkill(connection, boss, AtlantisV2Rules.WATER_PILLAR_SKILL_ID, false)) {
            crown.lastWaterPillar = now;
            crown.lastAnyAttack = now;
        }
        return;
    }
    if (now - crown.lastHomingBall >= homingMs) {
        if (castSkill(connection, boss, AtlantisV2Rules.HOMING_BALL_SKILL_ID, true)) {
            crown.lastHomingBall = now;
            crown.lastAnyAttack = now;
        }
    }
}

function runAddAttacks(connection, now) {
    for (let add of livingAttendants()) {
        const position = add.getPosition();
        const due = crown.nextAddAttack.get(position) || now;
        if (now < due) continue;

        if (Math.random() < 0.5) {
            castSeaWave(connection);
        } else {
            castSkill(connection, add, AtlantisV2Rules.BLIZZARD_SKILL_ID, false);
        }
        crown.nextAddAttack.set(position, now + randomAddInterval());
    }
}

function healByFraction(guardian, fraction, capFraction) {
    if (!guardian || guardian.getCurrentHealth().get() < 1) return false;
    const cap = Math.max(1, Math.round(guardian.getMaxHealth() * capFraction));
    const amount = Math.max(1, Math.round(guardian.getMaxHealth() * fraction));
    const next = Math.min(cap, guardian.getCurrentHealth().get() + amount);
    if (next === guardian.getCurrentHealth().get()) return false;
    guardian.getCurrentHealth().set(next);
    return true;
}

function applyBloodTideHealing() {
    const boss = bossGuardian();
    let healed = healByFraction(
        boss, AtlantisV2Rules.BLOOD_TIDE_BOSS_HEAL,
        AtlantisV2Rules.DROWNED_CROWN_START_HEALTH
    );
    for (let add of livingAttendants()) {
        healed = healByFraction(add, AtlantisV2Rules.BLOOD_TIDE_ADD_HEAL, 1.0) || healed;
    }
    if (healed) crown.healthBroadcastPending = true;
    crown.bloodTidePending = true;
}

function flushBloodTideHealing(connection) {
    if (!crown.bloodTidePending) return;
    crown.bloodTidePending = false;
    announce(connection, "Blood Tide — the Crown and its risen bloodline recover.");
    if (!crown.healthBroadcastPending) return;

    crown.healthBroadcastPending = false;
    const boss = bossGuardian();
    if (boss && boss.getCurrentHealth().get() > 0) {
        broadcastHealth(connection, boss, AtlantisV2Rules.BLOOD_TIDE_HEAL_SKILL_ID);
    }
    for (let add of livingAttendants()) {
        broadcastHealth(connection, add, AtlantisV2Rules.BLOOD_TIDE_HEAL_SKILL_ID);
    }
}

function registerAttendantDeaths(connection, now) {
    for (let add of attendants()) {
        const position = add.getPosition();
        if (add.getCurrentHealth().get() > 0 || crown.deadSeen.get(position)) continue;

        crown.deadSeen.set(position, true);
        crown.inheritedPowers++;
        if (crown.inheritedPowers === 1) {
            crown.lastBlizzard = now;
            announce(connection, "One bloodline is severed forever. Its frost passes to the Crown.");
        } else {
            crown.lastStorm = now;
            announce(connection, "The final bloodline is severed forever. Its storm passes to the Crown.");
        }
    }
}

function enterCrownless(connection, now) {
    if (crown.state === CROWNLESS) return;
    crown.state = CROWNLESS;
    crown.stateStarted = now;
    resetBossTimers(now);
    announce(connection, "No bloodline remains. Behold the Crownless King.");
}

function consumeLivingAttendants(connection, boss, now) {
    const living = livingAttendants();
    if (living.length === 0) {
        enterCrownless(connection, now);
        return;
    }

    for (let add of living) {
        add.getCurrentHealth().set(0);
        crown.deadSeen.set(add.getPosition(), true);
        crown.inheritedPowers++;
        broadcastHealth(connection, add, 0);
    }
    healByFraction(
        boss,
        AtlantisV2Rules.BLOOD_TIDE_CONSUME_HEAL * living.length,
        AtlantisV2Rules.DROWNED_CROWN_START_HEALTH
    );
    broadcastHealth(connection, boss, AtlantisV2Rules.BLOOD_TIDE_HEAL_SKILL_ID);
    announce(connection, "Cornered, Royal Lizard devours the living bloodline and rises renewed.");
    enterCrownless(connection, now);
}

function enterFinalProcession(connection, now) {
    crown.state = FINAL_PROCESSION;
    crown.stateStarted = now;
    crown.processionWaves = 0;
    crown.nextProcessionWave = now + AtlantisV2Rules.FINAL_POINT_SILENCE_MS;
    crown.convergenceWarningAt = 0;
    crown.nextConvergenceScan = now + AtlantisV2Rules.DROWNED_CROWN_CONVERGENCE_COOLDOWN_MS;
    announce(connection, "The Drowned Crown shatters. The Blood Tide is broken.");
    announce(connection, "No ward. No resurrection. One final point.");
}

function runFinalProcession(connection, now) {
    if (now < crown.nextProcessionWave) return;
    if (crown.processionWaves === 0) {
        announce(connection, "The Last Tide rises — endure it, then end the Crown.");
    }

    castSeaWave(connection);
    crown.processionWaves++;
    crown.nextProcessionWave = now + AtlantisV2Rules.PHASE_ONE_WAVE_GAP_MS;
    if (crown.processionWaves >= AtlantisV2Rules.FINAL_POINT_WAVE_COUNT) {
        crown.state = FINAL_POINT;
        crown.stateStarted = now;
        resetBossTimers(now);
    }
}

function finalPointFloor(target) {
    return Math.max(1, Math.round(target.getMaxHealth() * AtlantisV2Rules.FINAL_POINT_HEALTH));
}

function clampBossBeforeFinalPoint(target, newHealth) {
    if (!target.isBoss()) return newHealth;
    if (crown.state === CALM || crown.state === FINAL_PROCESSION) {
        return target.getCurrentHealth().get();
    }
    if (crown.state !== BLOOD_TIDE && crown.state !== CROWNLESS) return newHealth;

    const floor = finalPointFloor(target);
    if (newHealth < floor) {
        target.getCurrentHealth().set(floor);
        return floor;
    }
    return newHealth;
}

var phase = {
    getPhaseName: function () {
        return "The Drowned Crown";
    },
    getState: function () {
        return crown.state;
    },
    getInheritedPowers: function () {
        return crown.inheritedPowers;
    },
    getProcessionWaves: function () {
        return crown.processionWaves;
    },
    start: function () {
        const now = AtlantisV2Rules.now();
        const boss = bossGuardian();
        crown.timeStarted = now;
        crown.stateStarted = now;
        crown.phaseStarted = true;
        crown.state = CALM;
        crown.introPending = true;
        crown.inheritedPowers = 0;
        crown.deadSeen.clear();
        crown.nextAddAttack.clear();
        crown.convergenceWarningAt = 0;
        crown.nextConvergenceScan = now + AtlantisV2Rules.TIDAL_CONVERGENCE_SCAN_MS;
        game.clearPlayerSupportExemptPosition();
        game.setPlayerHealSkillsDisabled(false);
        game.setPlayerShieldSkillsDisabled(false);

        if (boss) {
            boss.getCurrentHealth().set(Math.max(1,
                Math.round(boss.getMaxHealth() * AtlantisV2Rules.DROWNED_CROWN_START_HEALTH)));
        }
        for (let add of attendants()) {
            add.getCurrentHealth().set(Math.max(1,
                Math.round(add.getMaxHealth() * AtlantisV2Rules.DROWNED_CROWN_ADD_HEALTH)));
            crown.deadSeen.set(add.getPosition(), false);
            crown.nextAddAttack.set(add.getPosition(), now + randomAddInterval());
        }
        resetBossTimers(now);
    },
    update: function (connection) {
        if (!crown.phaseStarted || this.hasEnded()) return PhaseUpdateResult.CONTINUE;
        try {
            const now = AtlantisV2Rules.now();
            const boss = bossGuardian();
            if (!boss) return PhaseUpdateResult.ERROR;

            if (crown.introPending) {
                crown.introPending = false;
                broadcastHealth(connection, boss, AtlantisV2Rules.BLOOD_TIDE_HEAL_SKILL_ID);
                for (let add of attendants()) {
                    broadcastRebirth(connection, boss, add);
                }
                announce(connection, "The court falls silent. Royal Lizard sinks beneath the waves.");
                announce(connection, "Then the Drowned Crown rises with its severed bloodline — the Blood Tide returns.");
            }
            flushBloodTideHealing(connection);

            if (crown.state === CALM) {
                if (now - crown.stateStarted < AtlantisV2Rules.DROWNED_CROWN_CALM_MS) {
                    return PhaseUpdateResult.CONTINUE;
                }
                crown.state = BLOOD_TIDE;
                crown.stateStarted = now;
                resetBossTimers(now);
                announce(connection, "The Blood Tide begins. Every lost ball restores the Crown and its bloodline.");
                return PhaseUpdateResult.CONTINUE;
            }

            if (crown.state === BLOOD_TIDE) {
                registerAttendantDeaths(connection, now);
                const floor = finalPointFloor(boss);
                if (boss.getCurrentHealth().get() <= floor && livingAttendants().length > 0) {
                    consumeLivingAttendants(connection, boss, now);
                } else if (livingAttendants().length === 0) {
                    enterCrownless(connection, now);
                }

                if (crown.state === BLOOD_TIDE) {
                    runTidalConvergence(connection, boss, now);
                    runBossAttacks(connection, boss, now, false);
                    runAddAttacks(connection, now);
                    return PhaseUpdateResult.CONTINUE;
                }
            }

            if (crown.state === CROWNLESS) {
                if (boss.getCurrentHealth().get() <= finalPointFloor(boss)) {
                    enterFinalProcession(connection, now);
                    return PhaseUpdateResult.CONTINUE;
                }
                runTidalConvergence(connection, boss, now);
                runBossAttacks(connection, boss, now, false);
                return PhaseUpdateResult.CONTINUE;
            }

            if (crown.state === FINAL_PROCESSION) {
                runFinalProcession(connection, now);
                return PhaseUpdateResult.CONTINUE;
            }

            if (crown.state === FINAL_POINT) {
                if (boss.getCurrentHealth().get() < 1) {
                    if (!crown.victoryAnnounced) {
                        crown.victoryAnnounced = true;
                        announce(connection, "The Last Tide recedes. The Drowned Crown is no more.");
                    }
                    return PhaseUpdateResult.NEXT_PHASE;
                }
                runTidalConvergence(connection, boss, now);
                runBossAttacks(connection, boss, now, true);
            }

            return PhaseUpdateResult.CONTINUE;
        } catch (e) {
            log.error("Script error in 4_enrage.js:", e.message, e.stack || e);
            return PhaseUpdateResult.ERROR;
        }
    },
    end: function () {
        crown.finished = true;
        game.setPlayerSupportSkillsDisabled(false);
        game.clearPlayerSupportExemptPosition();
    },
    phaseTime: function () {
        return AtlantisV2Rules.now() - crown.timeStarted;
    },
    playTime: function () {
        return 0;
    },
    hasEnded: function () {
        return crown.finished || (this.playTime() !== 0 && this.phaseTime() > this.playTime());
    },
    getGuardianAttackLoopTime: function () {
        return -1;
    },
    onHeal: function (target, healAmount, isGuardian) {
        if (isGuardian) {
            return game.getGuardianCombatSystem().heal(target, healAmount);
        }
        const amount = crown.state === CALM
            ? healAmount
            : Math.round(healAmount * AtlantisV2Rules.POST_REVIVE_HEAL_MULTIPLIER);
        return game.getPlayerCombatSystem().heal(target, amount);
    },
    onDealDamage: function (attackingPlayer, targetGuardian, damage, hasAttackerDmgBuff, hasTargetDefBuff, skill) {
        const target = game.getGuardianBattleStateByPosition(targetGuardian);
        if (!target) return 0;
        if (crown.state === CALM || crown.state === FINAL_PROCESSION) {
            return target.getCurrentHealth().get();
        }
        const newHealth = game.getGuardianCombatSystem().dealDamage(
            attackingPlayer, targetGuardian, damage, hasAttackerDmgBuff, hasTargetDefBuff, skill);
        return clampBossBeforeFinalPoint(target, newHealth);
    },
    onDealDamageToPlayer: function (attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill) {
        return game.getGuardianCombatSystem().dealDamageToPlayer(
            attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill);
    },
    onDealDamageOnBallLoss: function (attackerPos, targetPos, hasAttackerWillBuff) {
        const target = game.getGuardianBattleStateByPosition(targetPos);
        if (!target) return 0;
        if (crown.state === CALM || crown.state === FINAL_PROCESSION) {
            return target.getCurrentHealth().get();
        }
        const newHealth = game.getGuardianCombatSystem().dealDamageOnBallLoss(
            attackerPos, targetPos, hasAttackerWillBuff);
        return clampBossBeforeFinalPoint(target, newHealth);
    },
    onDealDamageOnBallLossToPlayer: function (attackerPos, targetPos, hasAttackerWillBuff) {
        const newHealth = game.getGuardianCombatSystem().dealDamageOnBallLossToPlayer(
            attackerPos, targetPos, hasAttackerWillBuff);
        if (crown.state === BLOOD_TIDE || crown.state === CROWNLESS) {
            applyBloodTideHealing();
        }
        return newHealth;
    }
}
