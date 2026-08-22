var S2CMatchplayUseSkill = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill");
var S2CChatRoomAnswerPacket = Java.type("com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket");
var PhaseUpdateResult = Java.type("com.jftse.emulator.server.core.matchplay.guardian.PhaseUpdateResult");
var AtlantisV2Rules = Java.type("com.jftse.emulator.server.core.matchplay.guardian.AtlantisV2Rules");

// Atlantis phase 1. Numbers come from AtlantisV2Rules — do not retune here.
var OPENING = "OPENING";
var THREE_WAVE_RAGE = "THREE_WAVE_RAGE";
var FIVE_WAVE_RAGE = "FIVE_WAVE_RAGE";
var BOSS_RELEASED = "BOSS_RELEASED";
var BOSS_FINAL = "BOSS_FINAL";

var SEA_WAVE_PACKET_ID = AtlantisV2Rules.SEA_WAVE_PACKET_ID;
var HOMING_BALL_SKILL_ID = AtlantisV2Rules.HOMING_BALL_SKILL_ID;
var BLIZZARD_SKILL_ID = AtlantisV2Rules.BLIZZARD_SKILL_ID;

class GreenTide {
    constructor() {
        this.timeStarted = 0;
        this.finished = false;
        this.phaseStarted = false;
        this.introPending = false;
        this.state = OPENING;
        this.lastBossAttack = 0;
        this.nextAddAttack = new Map();
        this.volleyActive = false;
        this.volleyGeneration = 0;
        this.nextVolleyAt = 0;
        this.supportPosition = -1;
    }
}

let tide = new GreenTide();

function announce(connection, text) {
    const packet = new S2CChatRoomAnswerPacket(2, "Server", text);
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function playersAlive() {
    return game.getPlayerBattleStates().stream()
        .filter(p => p != null && p.getPosition() < 4 && p.getCurrentHealth().get() > 0)
        .toArray();
}

function addsAlive() {
    return game.getGuardianBattleStates().stream()
        .filter(g => !g.isBoss() && g.getCurrentHealth().get() > 0)
        .toArray();
}

function bossGuardian() {
    return game.getGuardianBattleStates().stream()
        .filter(g => g.isBoss())
        .findFirst()
        .orElse(null);
}

function randomTarget(players) {
    return players[Math.floor(Math.random() * players.length)];
}

function randomAddInterval() {
    const spread = AtlantisV2Rules.ADD_ATTACK_MAX_MS - AtlantisV2Rules.ADD_ATTACK_MIN_MS + 1;
    return AtlantisV2Rules.ADD_ATTACK_MIN_MS + Math.floor(Math.random() * spread);
}

function castNormalSeaWave(connection, x, depth) {
    const packet = new S2CMatchplayUseSkill(
        4, 4, SEA_WAVE_PACKET_ID,
        Math.floor(Math.random() * 127),
        x,
        0,
        depth
    );
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function castGuardianSkill(connection, guardian, skillId, targetAll) {
    if (skillId === SEA_WAVE_PACKET_ID || skillId === AtlantisV2Rules.SEA_WAVE_SKILL_ID) {
        castNormalSeaWave(connection, randomWaveX(), randomWaveDepth());
        return;
    }

    const skill = serviceManager.getSkillService().findSkillById(skillId);
    const players = playersAlive();
    if (!skill || players.length === 0) return;

    if (targetAll) {
        for (let player of players) {
            const packet = new S2CMatchplayUseSkill(
                guardian.getPosition(), player.getPosition(), skill.getId() - 1,
                Math.floor(Math.random() * 127), 0, 0, 0
            );
            gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
        }
        return;
    }

    const target = randomTarget(players);
    const packet = new S2CMatchplayUseSkill(
        guardian.getPosition(), target.getPosition(), skill.getId() - 1,
        Math.floor(Math.random() * 127), 0, 0, 0
    );
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function castRandomBossSkill(connection, boss, finalSet) {
    const skillIds = finalSet
        ? [HOMING_BALL_SKILL_ID]
        : [AtlantisV2Rules.SEA_WAVE_SKILL_ID, HOMING_BALL_SKILL_ID, BLIZZARD_SKILL_ID];
    const skillId = skillIds[Math.floor(Math.random() * skillIds.length)];
    castGuardianSkill(connection, boss, skillId, skillId === HOMING_BALL_SKILL_ID);
}

function runBossAttackLoop(connection, boss, now, finalSet) {
    if (now - tide.lastBossAttack < AtlantisV2Rules.BOSS_ATTACK_INTERVAL_MS) return;
    castRandomBossSkill(connection, boss, finalSet);
    tide.lastBossAttack = now;
}

function runAddAttackLoops(connection, now) {
    for (let add of addsAlive()) {
        const position = add.getPosition();
        const due = tide.nextAddAttack.get(position) || now;
        if (now < due) continue;

        const skillId = Math.random() < 0.5
            ? AtlantisV2Rules.SEA_WAVE_SKILL_ID
            : BLIZZARD_SKILL_ID;
        castGuardianSkill(connection, add, skillId, false);
        tide.nextAddAttack.set(position, now + randomAddInterval());
    }
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

function randomVolleyRest() {
    const min = AtlantisV2Rules.PHASE_ONE_VOLLEY_REST_MIN_MS;
    const max = AtlantisV2Rules.PHASE_ONE_VOLLEY_REST_MAX_MS;
    return Math.floor(min + Math.random() * (max - min + 1));
}

function cancelVolley() {
    tide.volleyGeneration++;
    tide.volleyActive = false;
    tide.nextVolleyAt = 0;
}

function disablePlayerSupport() {
    game.setPlayerSupportSkillsDisabled(true);
    for (let player of game.getPlayerBattleStates()) {
        player.setShieldActive(false);
    }
}

function clearSupportPlayer() {
    game.clearPlayerSupportExemptPosition();
    tide.supportPosition = -1;
}

function selectSupportPlayer(connection, section) {
    const players = playersAlive();
    clearSupportPlayer();
    if (players.length === 0) return;

    const chosen = randomTarget(players);
    game.setPlayerSupportExemptPosition(chosen.getPosition());
    tide.supportPosition = chosen.getPosition();
    const chosenConnection = gameManager.getConnectionByPlayerId(chosen.getId());
    const chosenClient = chosenConnection ? chosenConnection.getClient() : null;
    const chosenPlayer = chosenClient ? chosenClient.getPlayer() : null;
    const chosenName = chosenPlayer ? chosenPlayer.getName() : "Player " + chosen.getPosition();
    announce(connection, chosenName + " bears the Tide's Grace during " + section
        + ". Only they retain healing and shields.");
}

function startVolley(connection, count) {
    tide.volleyActive = true;
    const state = tide.state;
    const generation = ++tide.volleyGeneration;
    const eventHandler = gameManager.getEventHandler();

    for (let i = 0; i < count; i++) {
        const x = randomWaveX();
        const depth = randomWaveDepth();
        const delay = (i + 1) * AtlantisV2Rules.PHASE_ONE_WAVE_GAP_MS;
        const waveEvent = eventHandler.createRunnableEvent(function () {
            const session = connection.getClient() && connection.getClient().getActiveGameSession();
            if (!session || tide.finished || tide.state !== state || tide.volleyGeneration !== generation) return;
            castNormalSeaWave(connection, x, depth);
        }, delay);
        eventHandler.offerJS(waveEvent);
    }

    // Mark the volley schedulable one wave-gap before the randomized rest ends:
    // startVolley delays its first wave by that gap, so the next first wave lands
    // four to five seconds after this volley's final wave.
    const rest = randomVolleyRest();
    const completeDelay = count * AtlantisV2Rules.PHASE_ONE_WAVE_GAP_MS
        + rest
        - AtlantisV2Rules.PHASE_ONE_WAVE_GAP_MS;
    const completeEvent = eventHandler.createRunnableEvent(function () {
        if (tide.finished || tide.state !== state || tide.volleyGeneration !== generation) return;
        tide.volleyActive = false;
        tide.nextVolleyAt = AtlantisV2Rules.now();
    }, completeDelay);
    eventHandler.offerJS(completeEvent);
}

function maybeStartVolley(connection, now, count) {
    if (!tide.volleyActive && now >= tide.nextVolleyAt) {
        startVolley(connection, count);
    }
}

function enterThreeWaveRage(connection, now) {
    tide.state = THREE_WAVE_RAGE;
    cancelVolley();
    tide.nextVolleyAt = now;
    disablePlayerSupport();
    clearSupportPlayer();
    announce(connection, "The First Tide turns. Guardian spells, healing, and shields fall silent — endure the three-wave volleys.");
}

function enterFiveWaveRage(connection, now) {
    tide.state = FIVE_WAVE_RAGE;
    cancelVolley();
    tide.nextVolleyAt = now;
    announce(connection, "One bloodline is severed. Royal Lizard rages — five waves now answer the Crown.");
    disablePlayerSupport();
    selectSupportPlayer(connection, "the Five-Wave Rage");
}

function enterBossReleased(connection, now) {
    tide.state = BOSS_RELEASED;
    cancelVolley();
    tide.lastBossAttack = now;
    disablePlayerSupport();
    announce(connection, "The final bloodline falls. The Crown's ward is broken — Royal Lizard can bleed.");
    selectSupportPlayer(connection, "the Crown's first reckoning");
}

function enterBossFinal(connection, now) {
    tide.state = BOSS_FINAL;
    cancelVolley();
    tide.nextVolleyAt = now;
    tide.lastBossAttack = now;
    disablePlayerSupport();
    announce(connection, "At 90% health, the wounded Crown calls back the five-wave tide. The Tide's Grace remains.");
}

function addsTriggeredPhase() {
    return game.getGuardianBattleStates().stream()
        .filter(g => !g.isBoss())
        .anyMatch(g => g.getCurrentHealth().get() <= g.getMaxHealth() * AtlantisV2Rules.ADD_PHASE_TRIGGER_HEALTH);
}

function bossHealthAtOrBelow(boss, fraction) {
    return boss.getCurrentHealth().get() <= boss.getMaxHealth() * fraction;
}

var phase = {
    getPhaseName: function () {
        return "Green Tide";
    },
    getState: function () {
        return tide.state;
    },
    start: function () {
        const now = AtlantisV2Rules.now();
        tide.timeStarted = now;
        tide.phaseStarted = true;
        tide.introPending = true;
        tide.state = OPENING;
        tide.lastBossAttack = now;
        game.setPlayerSupportSkillsDisabled(false);
        clearSupportPlayer();

        for (let guardian of game.getGuardianBattleStates()) {
            guardian.getSkills().clear();
            if (!guardian.isBoss()) {
                tide.nextAddAttack.set(guardian.getPosition(), now + randomAddInterval());
            }
        }
    },
    update: function (connection) {
        if (!tide.phaseStarted || this.hasEnded()) return PhaseUpdateResult.CONTINUE;

        try {
            const now = AtlantisV2Rules.now();
            const boss = bossGuardian();
            if (!boss) return PhaseUpdateResult.ERROR;
            const livingAdds = addsAlive().length;

            if (tide.introPending) {
                tide.introPending = false;
                announce(connection, "Royal Lizard stands behind two living wards. While either attendant survives, the Crown cannot be harmed.");
            }

            if (tide.state === OPENING) {
                if (addsTriggeredPhase()) {
                    enterThreeWaveRage(connection, now);
                } else {
                    runAddAttackLoops(connection, now);
                    return PhaseUpdateResult.CONTINUE;
                }
            }

            if (tide.state === THREE_WAVE_RAGE) {
                if (livingAdds === 0) {
                    enterBossReleased(connection, now);
                } else if (livingAdds === 1) {
                    enterFiveWaveRage(connection, now);
                } else {
                    maybeStartVolley(connection, now, AtlantisV2Rules.FIRST_VOLLEY_COUNT);
                    return PhaseUpdateResult.CONTINUE;
                }
            }

            if (tide.state === FIVE_WAVE_RAGE) {
                if (livingAdds === 0) {
                    enterBossReleased(connection, now);
                } else {
                    maybeStartVolley(connection, now, AtlantisV2Rules.SECOND_VOLLEY_COUNT);
                    return PhaseUpdateResult.CONTINUE;
                }
            }

            if (tide.state === BOSS_RELEASED) {
                if (bossHealthAtOrBelow(boss, AtlantisV2Rules.BOSS_FINAL_PHASE_HEALTH)) {
                    enterBossFinal(connection, now);
                } else {
                    runBossAttackLoop(connection, boss, now, false);
                    return PhaseUpdateResult.CONTINUE;
                }
            }

            if (tide.state === BOSS_FINAL) {
                if (bossHealthAtOrBelow(boss, AtlantisV2Rules.BOSS_NEXT_PHASE_HEALTH)) {
                    cancelVolley();
                    announce(connection, "Royal Lizard sinks into the drowned court. Behind it, the severed bloodlines stir.");
                    return PhaseUpdateResult.NEXT_PHASE;
                }
                runBossAttackLoop(connection, boss, now, true);
                maybeStartVolley(connection, now, AtlantisV2Rules.BOSS_FINAL_VOLLEY_COUNT);
            }

            return PhaseUpdateResult.CONTINUE;
        } catch (e) {
            log.error("Script error in 1_green_tide.js:", e.message, e.stack || e);
            return PhaseUpdateResult.ERROR;
        }
    },
    end: function () {
        tide.finished = true;
        cancelVolley();
        clearSupportPlayer();
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
        // Non-exempt support packets are rejected before reaching the phase hook.
        return game.getPlayerCombatSystem().heal(target, healAmount);
    },
    onDealDamage: function (attackingPlayer, targetGuardian, damage, hasAttackerDmgBuff, hasTargetDefBuff, skill) {
        const target = game.getGuardianBattleStateByPosition(targetGuardian);
        if (target?.isBoss() && addsAlive().length > 0) {
            return target.getCurrentHealth().get();
        }
        return game.getGuardianCombatSystem().dealDamage(attackingPlayer, targetGuardian, damage, hasAttackerDmgBuff, hasTargetDefBuff, skill);
    },
    onDealDamageToPlayer: function (attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill) {
        return game.getGuardianCombatSystem().dealDamageToPlayer(attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill);
    },
    onDealDamageOnBallLoss: function (attackerPos, targetPos, hasAttackerWillBuff) {
        const target = game.getGuardianBattleStateByPosition(targetPos);
        if (target?.isBoss() && addsAlive().length > 0) {
            return target.getCurrentHealth().get();
        }
        return game.getGuardianCombatSystem().dealDamageOnBallLoss(attackerPos, targetPos, hasAttackerWillBuff);
    },
    onDealDamageOnBallLossToPlayer: function (attackerPos, targetPos, hasAttackerWillBuff) {
        return game.getGuardianCombatSystem().dealDamageOnBallLossToPlayer(attackerPos, targetPos, hasAttackerWillBuff);
    }
}
