var S2CMatchplayUseSkill = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill");
var S2CChatRoomAnswerPacket = Java.type("com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket");
var PhaseUpdateResult = Java.type("com.jftse.emulator.server.core.matchplay.guardian.PhaseUpdateResult");
var AtlantisV2Rules = Java.type("com.jftse.emulator.server.core.matchplay.guardian.AtlantisV2Rules");
var GuardianShieldPadService = Java.type("com.jftse.emulator.server.core.matchplay.guardian.GuardianShieldPadService");

// Atlantis phase 3: tennis points alone control cumulative boss pressure.
// The phase starts at Maximum Tide. Losing a ball raises the tide; winning one
// against Royal Lizard lowers it.
class RisingTide {
    constructor() {
        this.timeStarted = 0;
        this.finished = false;
        this.phaseStarted = false;
        this.introPending = false;
        this.level = 0;
        this.announcedLevel = 0;
        this.lastHomingBall = 0;
        this.lastWaterPillar = 0;
        this.lastBlizzard = 0;
        this.lastStorm = 0;
        this.lastAnyAttack = 0;
        this.convergenceWarningAt = 0;
        this.nextConvergenceScan = 0;
    }
}

let tide = new RisingTide();

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

function castBossSkill(connection, boss, skillId, targetAll) {
    const skill = serviceManager.getSkillService().findSkillById(skillId);
    if (!skill) return false;

    if (targetAll) {
        const players = playersAlive();
        if (players.length === 0) return false;
        for (let player of players) {
            const packet = new S2CMatchplayUseSkill(
                boss.getPosition(), player.getPosition(), skill.getId() - 1,
                Math.floor(Math.random() * 127), 0, 0, 0
            );
            gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
        }
        return true;
    }

    const packet = new S2CMatchplayUseSkill(
        boss.getPosition(), 4, skill.getId() - 1,
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
    if (tide.convergenceWarningAt !== 0) {
        if (now - tide.convergenceWarningAt < AtlantisV2Rules.TIDAL_CONVERGENCE_WARNING_MS) return;
        tide.convergenceWarningAt = 0;
        tide.nextConvergenceScan = now + AtlantisV2Rules.RISING_TIDE_CONVERGENCE_COOLDOWN_MS;
        const centers = clusteredAliveCenters(connection);
        if (centers.length === 0) {
            announce(connection, "The team spreads. Tidal Convergence is broken.");
            return;
        }
        if (castConvergencePillars(connection, boss, centers)) {
            tide.lastWaterPillar = now;
            tide.lastAnyAttack = now;
        }
        return;
    }

    if (now < tide.nextConvergenceScan) return;
    const centers = clusteredAliveCenters(connection);
    if (centers.length > 0) {
        tide.convergenceWarningAt = now;
        announce(connection, "Tidal Convergence gathers beneath grouped players — spread before the pillars rise!");
    } else {
        tide.nextConvergenceScan = now + AtlantisV2Rules.TIDAL_CONVERGENCE_SCAN_MS;
    }
}

function setTideLevel(nextLevel, now) {
    const clamped = Math.max(0, Math.min(AtlantisV2Rules.RISING_TIDE_MAX_LEVEL, nextLevel));
    if (clamped === tide.level) return;

    const previous = tide.level;
    tide.level = clamped;

    // A newly unlocked spell receives its full first cooldown. This keeps a
    // ball loss from producing an immediate, stacked packet burst.
    if (previous < 1 && clamped >= 1) tide.lastWaterPillar = now;
    if (previous < 2 && clamped >= 2) tide.lastBlizzard = now;
    if (previous < 3 && clamped >= 3) tide.lastStorm = now;
}

function raiseTide() {
    setTideLevel(tide.level + 1, AtlantisV2Rules.now());
}

function lowerTide() {
    setTideLevel(tide.level - 1, AtlantisV2Rules.now());
}

function announceLevel(connection) {
    if (tide.announcedLevel === tide.level) return;
    tide.announcedLevel = tide.level;

    if (tide.level === 0) {
        announce(connection, "Low Tide. The sea loosens its grip.");
    } else if (tide.level === 1) {
        announce(connection, "Rising Tide. Water Pillar answers the Crown.");
    } else if (tide.level === 2) {
        announce(connection, "High Tide. Blizzard closes over the court.");
    } else {
        announce(connection, "Maximum Tide. Royal Lizard commands the Storm.");
    }
}

function runAttacks(connection, boss, now) {
    if (now - tide.lastAnyAttack < AtlantisV2Rules.RISING_TIDE_MIN_ATTACK_GAP_MS) return;

    // Cast at most one overdue spell per update. Higher-tide attacks take
    // priority when timers meet, and the global gap prevents simultaneous casts.
    if (tide.level >= 3 && now - tide.lastStorm >= AtlantisV2Rules.RISING_TIDE_STORM_MS) {
        if (castBossSkill(connection, boss, AtlantisV2Rules.STORM_SKILL_ID, false)) {
            tide.lastStorm = now;
            tide.lastAnyAttack = now;
        }
        return;
    }
    if (tide.level >= 2 && now - tide.lastBlizzard >= AtlantisV2Rules.RISING_TIDE_BLIZZARD_MS) {
        if (castBossSkill(connection, boss, AtlantisV2Rules.BLIZZARD_SKILL_ID, false)) {
            tide.lastBlizzard = now;
            tide.lastAnyAttack = now;
        }
        return;
    }
    if (tide.level >= 1 && now - tide.lastWaterPillar >= AtlantisV2Rules.RISING_TIDE_WATER_PILLAR_MS) {
        if (castBossSkill(connection, boss, AtlantisV2Rules.WATER_PILLAR_SKILL_ID, false)) {
            tide.lastWaterPillar = now;
            tide.lastAnyAttack = now;
        }
        return;
    }
    if (now - tide.lastHomingBall >= AtlantisV2Rules.RISING_TIDE_HOMING_BALL_MS) {
        if (castBossSkill(connection, boss, AtlantisV2Rules.HOMING_BALL_SKILL_ID, true)) {
            tide.lastHomingBall = now;
            tide.lastAnyAttack = now;
        }
    }
}

function clampBossForFinalPhase(target, newHealth) {
    if (!target.isBoss()) return newHealth;
    const floor = Math.max(1,
        Math.round(target.getMaxHealth() * AtlantisV2Rules.RISING_TIDE_END_HEALTH));
    if (newHealth < floor) {
        target.getCurrentHealth().set(floor);
        return floor;
    }
    return newHealth;
}

var phase = {
    getPhaseName: function () {
        return "Rising Tide";
    },
    getTideLevel: function () {
        return tide.level;
    },
    start: function () {
        const now = AtlantisV2Rules.now();
        const boss = bossGuardian();
        tide.timeStarted = now;
        tide.phaseStarted = true;
        tide.introPending = true;
        tide.level = AtlantisV2Rules.RISING_TIDE_MAX_LEVEL;
        tide.announcedLevel = AtlantisV2Rules.RISING_TIDE_MAX_LEVEL;
        tide.lastHomingBall = now;
        tide.lastWaterPillar = now;
        tide.lastBlizzard = now;
        tide.lastStorm = now;
        tide.lastAnyAttack = now;
        tide.convergenceWarningAt = 0;
        tide.nextConvergenceScan = now + AtlantisV2Rules.TIDAL_CONVERGENCE_SCAN_MS;
        game.clearPlayerSupportExemptPosition();
        game.setPlayerHealSkillsDisabled(false);
        game.setPlayerShieldSkillsDisabled(false);
    },
    update: function (connection) {
        if (!tide.phaseStarted || this.hasEnded()) return PhaseUpdateResult.CONTINUE;
        try {
            const now = AtlantisV2Rules.now();
            const boss = bossGuardian();
            if (!boss) return PhaseUpdateResult.ERROR;

            if (tide.introPending) {
                tide.introPending = false;
                announce(connection, "The wardless Crown begins at Maximum Tide. Win a ball and force the sea back; lose one and it rises again.");
            }

            announceLevel(connection);

            const phaseFloor = Math.max(1,
                Math.round(boss.getMaxHealth() * AtlantisV2Rules.RISING_TIDE_END_HEALTH));
            if (boss.getCurrentHealth().get() <= phaseFloor) {
                announce(connection, "Driven to the brink, Royal Lizard sinks beneath Atlantis. The Drowned Crown awakens.");
                return PhaseUpdateResult.NEXT_PHASE;
            }

            runTidalConvergence(connection, boss, now);
            runAttacks(connection, boss, now);
            return PhaseUpdateResult.CONTINUE;
        } catch (e) {
            log.error("Script error in 3_rising_tide.js:", e.message, e.stack || e);
            return PhaseUpdateResult.ERROR;
        }
    },
    end: function () {
        tide.finished = true;
    },
    phaseTime: function () {
        return AtlantisV2Rules.now() - tide.timeStarted;
    },
    playTime: function () {
        return 0;
    },
    hasEnded: function () {
        return tide.finished || (this.playTime() !== 0 && this.phaseTime() > this.playTime());
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
        const newHealth = game.getGuardianCombatSystem().dealDamage(
            attackingPlayer, targetGuardian, damage, hasAttackerDmgBuff, hasTargetDefBuff, skill);
        return clampBossForFinalPhase(target, newHealth);
    },
    onDealDamageToPlayer: function (attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill) {
        return game.getGuardianCombatSystem().dealDamageToPlayer(
            attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill);
    },
    onDealDamageOnBallLoss: function (attackerPos, targetPos, hasAttackerWillBuff) {
        const target = game.getGuardianBattleStateByPosition(targetPos);
        if (!target) return 0;
        const newHealth = game.getGuardianCombatSystem().dealDamageOnBallLoss(
            attackerPos, targetPos, hasAttackerWillBuff);
        if (target.isBoss()) lowerTide();
        return clampBossForFinalPhase(target, newHealth);
    },
    onDealDamageOnBallLossToPlayer: function (attackerPos, targetPos, hasAttackerWillBuff) {
        const newHealth = game.getGuardianCombatSystem().dealDamageOnBallLossToPlayer(
            attackerPos, targetPos, hasAttackerWillBuff);
        raiseTide();
        return newHealth;
    }
}
