var S2CMatchplayUseSkill = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill");
var S2CChatRoomAnswerPacket = Java.type("com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket");
var PhaseUpdateResult = Java.type("com.jftse.emulator.server.core.matchplay.guardian.PhaseUpdateResult");

// Atlantis V2 act 3 — heals 100%, shields stay off.
// 30s Storm (id 62) + script Inferno (id 35) every 5s, then 50s charge, then 20 LTR SeaWaves.
// Charge animation is UNKNOWN / untested — we only wait and announce.
var SEA_WAVE_PACKET_ID = 27;
var WAVE_X = -200;
var WAVE_Z = 0;
var WAVE_Y = 0;
var WAVE_GAP_MS = 1200;
var MEGAWAVE_COUNT = 20;
var STORM_DWELL_MS = 30000;
var INFERNO_INTERVAL_MS = 5000;
var CHARGE_MS = 50000;

class StormCharge {
    constructor() {
        this.timeStarted = 0;
        this.finished = false;
        this.phaseStarted = false;
        this.lastInferno = 0;
        this.lastStorm = 0;
        this.chargeStarted = false;
        this.megawaveFired = false;
        this.megawaveDoneAt = 0;
    }
}

let storm = new StormCharge();

function announce(connection, text) {
    const packet = new S2CChatRoomAnswerPacket(2, "Server", text);
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function fireSeaWave(connection) {
    const seed = Math.floor(Math.random() * 127);
    const packet = new S2CMatchplayUseSkill(4, 4, SEA_WAVE_PACKET_ID, seed, WAVE_X, WAVE_Z, WAVE_Y);
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function fireVolley(connection, count, label, gapMs) {
    announce(connection, label + " — " + count + " LTR waves. Green pads are the only cover.");
    const eventHandler = gameManager.getEventHandler();
    for (let i = 0; i < count; i++) {
        const delay = 250 + i * gapMs;
        const n = i + 1;
        const runnableEvent = eventHandler.createRunnableEvent(function () {
            const session = connection.getClient() && connection.getClient().getActiveGameSession();
            if (!session) return;
            if (n === 1 || n === count) {
                announce(connection, label + " " + n + "/" + count);
            }
            fireSeaWave(connection);
        }, delay);
        eventHandler.offerJS(runnableEvent);
    }
}

var phase = {
    getPhaseName: function () {
        return "Storm Charge";
    },
    start: function () {
        storm.timeStarted = Date.now();
        storm.phaseStarted = true;
        game.setPlayerSupportSkillsDisabled(true);
        storm.lastInferno = Date.now();
        storm.lastStorm = 0;
    },
    update: function (connection) {
        if (!storm.phaseStarted || this.hasEnded()) return PhaseUpdateResult.CONTINUE;
        try {
            const now = Date.now();
            const elapsed = now - storm.timeStarted;
            const boss = game.getGuardianBattleStates().stream().filter(g => g.isBoss()).findFirst().orElse(null);
            if (!boss) return PhaseUpdateResult.ERROR;
            const skillService = serviceManager.getSkillService();
            const players = game.getPlayerBattleStates().stream()
                .filter(p => p != null && p.getPosition() < 4 && p.getCurrentHealth().get() > 0)
                .toArray();

            if (elapsed < STORM_DWELL_MS) {
                if (storm.lastStorm === 0) {
                    storm.lastStorm = now;
                    announce(connection, "Hold the green pads. Do not play the ball.");
                    const bigBlizz = skillService.findSkillById(62);
                    if (bigBlizz) {
                        const packet = new S2CMatchplayUseSkill(boss.getPosition(), 4, bigBlizz.getId() - 1, Math.floor(Math.random() * 127), 0, 0, 0);
                        gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
                    }
                }
                if (now - storm.lastInferno >= INFERNO_INTERVAL_MS) {
                    const inferno = skillService.findSkillById(35);
                    if (inferno) {
                        for (let player of players) {
                            const packet = new S2CMatchplayUseSkill(4, player.getPosition(), inferno.getId() - 1, Math.floor(Math.random() * 127), 0, 0, 0);
                            gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
                        }
                    }
                    storm.lastInferno = now;
                }
                return PhaseUpdateResult.CONTINUE;
            }

            if (!storm.chargeStarted) {
                storm.chargeStarted = true;
                announce(connection, "The boss charges. No known charge animation — wait it out and keep hitting.");
            }

            if (storm.chargeStarted && !storm.megawaveFired && elapsed >= STORM_DWELL_MS + CHARGE_MS) {
                storm.megawaveFired = true;
                storm.megawaveDoneAt = now + 250 + MEGAWAVE_COUNT * WAVE_GAP_MS;
                fireVolley(connection, MEGAWAVE_COUNT, "Megawave", WAVE_GAP_MS);
            }

            if (storm.megawaveFired && now >= storm.megawaveDoneAt) {
                announce(connection, "The charge breaks.");
                return PhaseUpdateResult.NEXT_PHASE;
            }

            return PhaseUpdateResult.CONTINUE;
        } catch (e) {
            log.error("Script error in 3_storm_charge.js:", e.message, e.stack || e);
            return PhaseUpdateResult.ERROR;
        }
    },
    end: function () {
        storm.finished = true;
    },
    phaseTime: function () {
        return Date.now() - storm.timeStarted;
    },
    playTime: function () {
        return 0;
    },
    hasEnded: function () {
        return storm.finished || (this.playTime() !== 0 && this.phaseTime() > this.playTime());
    },
    getGuardianAttackLoopTime: function (guardian) {
        return -1;
    },
    onHeal: function (target, healAmount, isGuardian) {
        if (isGuardian) {
            return game.getGuardianCombatSystem().heal(target, healAmount);
        }
        return game.getPlayerCombatSystem().heal(target, healAmount);
    },
    onDealDamage: function (attackingPlayer, targetGuardian, damage, hasAttackerDmgBuff, hasTargetDefBuff, skill) {
        return game.getGuardianCombatSystem().dealDamage(attackingPlayer, targetGuardian, damage, hasAttackerDmgBuff, hasTargetDefBuff, skill);
    },
    onDealDamageToPlayer: function (attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill) {
        return game.getGuardianCombatSystem().dealDamageToPlayer(attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill);
    },
    onDealDamageOnBallLoss: function (attackerPos, targetPos, hasAttackerWillBuff) {
        return game.getGuardianCombatSystem().dealDamageOnBallLoss(attackerPos, targetPos, hasAttackerWillBuff);
    },
    onDealDamageOnBallLossToPlayer: function (attackerPos, targetPos, hasAttackerWillBuff) {
        return game.getGuardianCombatSystem().dealDamageOnBallLossToPlayer(attackerPos, targetPos, hasAttackerWillBuff);
    }
}
