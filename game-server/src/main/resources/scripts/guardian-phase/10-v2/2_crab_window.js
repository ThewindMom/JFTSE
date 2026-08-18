var S2CMatchplayUseSkill = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill");
var S2CMatchplayDealDamage = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayDealDamage");
var S2CChatRoomAnswerPacket = Java.type("com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket");
var PhaseUpdateResult = Java.type("com.jftse.emulator.server.core.matchplay.guardian.PhaseUpdateResult");

// Atlantis V2 act 2 — both adds already dead; boss immune until they were.
// Blizzard + 5 LTR waves, then waves-only, then 2 min crab window, then full-HP revive.
var SEA_WAVE_PACKET_ID = 27;
var WAVE_X = -200;
var WAVE_Z = 0;
var WAVE_Y = 0;
var WAVE_GAP_MS = 2500;
var BLIZZARD_VOLLEY = 5;
var CRAB_WINDOW_MS = 120000;
var WAVE_ONLY_MS = 30000;

class CrabWindow {
    constructor() {
        this.timeStarted = 0;
        this.finished = false;
        this.phaseStarted = false;
        this.blizzardVolleyFired = false;
        this.crabAnnounced = false;
        this.revived = false;
        this.healingMultiplier = 0.20;
        this.lastBlizzard = 0;
        this.lastWave = 0;
        this.waveOnly = false;
    }
}

let crab = new CrabWindow();

function announce(connection, text) {
    const packet = new S2CChatRoomAnswerPacket(2, "Server", text);
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function fireSeaWave(connection) {
    const seed = Math.floor(Math.random() * 127);
    const packet = new S2CMatchplayUseSkill(4, 4, SEA_WAVE_PACKET_ID, seed, WAVE_X, WAVE_Z, WAVE_Y);
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function fireVolley(connection, count, label) {
    announce(connection, label + " — " + count + " LTR waves. Green pads are safe.");
    const eventHandler = gameManager.getEventHandler();
    for (let i = 0; i < count; i++) {
        const delay = 250 + i * WAVE_GAP_MS;
        const n = i + 1;
        const runnableEvent = eventHandler.createRunnableEvent(function () {
            const session = connection.getClient() && connection.getClient().getActiveGameSession();
            if (!session) return;
            announce(connection, label + " " + n + "/" + count);
            fireSeaWave(connection);
        }, delay);
        eventHandler.offerJS(runnableEvent);
    }
}

function reviveAddsFull(connection) {
    const skillService = serviceManager.getSkillService();
    const rebirth = skillService.findSkillById(29);
    const boss = game.getGuardianBattleStates().stream().filter(g => g.isBoss()).findFirst().orElse(null);
    const pos = boss ? boss.getPosition() : 4;
    for (let g of game.getGuardianBattleStates()) {
        if (g.isBoss()) continue;
        g.getCurrentHealth().set(g.getMaxHealth());
        if (rebirth) {
            const packet = new S2CMatchplayUseSkill(pos, g.getPosition(), rebirth.getId() - 1, Math.floor(Math.random() * 127), 0, 0, 0);
            const dmgPacket = new S2CMatchplayDealDamage(g.getPosition(), g.getCurrentHealth().get(), 4, rebirth.getId(), 0.0, 0.0);
            gameManager.sendPacketToAllClientsInSameGameSession(dmgPacket, connection);
        }
    }
}

var phase = {
    getPhaseName: function () {
        return "Crab Window";
    },
    start: function () {
        crab.timeStarted = Date.now();
        crab.phaseStarted = true;
        game.setPlayerSupportSkillsDisabled(true);
        crab.lastBlizzard = Date.now();
        crab.lastWave = Date.now();
    },
    update: function (connection) {
        if (!crab.phaseStarted || this.hasEnded()) return PhaseUpdateResult.CONTINUE;
        try {
            const now = Date.now();
            const elapsed = now - crab.timeStarted;
            const boss = game.getGuardianBattleStates().stream().filter(g => g.isBoss()).findFirst().orElse(null);
            if (!boss) return PhaseUpdateResult.ERROR;
            const skillService = serviceManager.getSkillService();

            if (!crab.blizzardVolleyFired) {
                crab.blizzardVolleyFired = true;
                announce(connection, "Blizzard over the foam. Stay on green.");
                fireVolley(connection, BLIZZARD_VOLLEY, "Blizz tide");
            }

            if (!crab.waveOnly && elapsed < WAVE_ONLY_MS) {
                if (now - crab.lastBlizzard >= 18000) {
                    const blizzard = skillService.findSkillById(13);
                    if (blizzard) {
                        const packet = new S2CMatchplayUseSkill(boss.getPosition(), 4, blizzard.getId() - 1, Math.floor(Math.random() * 127), 0, 0, 0);
                        gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
                    }
                    crab.lastBlizzard = now;
                }
            }

            if (!crab.waveOnly && elapsed >= WAVE_ONLY_MS && !crab.crabAnnounced) {
                crab.waveOnly = true;
                crab.crabAnnounced = true;
                announce(connection, "Adds revive in 2 minutes. Plant crabs on their court.");
            }

            if (crab.waveOnly && !crab.revived && now - crab.lastWave >= 8000) {
                fireSeaWave(connection);
                crab.lastWave = now;
            }

            if (!crab.revived && elapsed >= WAVE_ONLY_MS + CRAB_WINDOW_MS) {
                crab.revived = true;
                reviveAddsFull(connection);
                announce(connection, "The attendants return at full strength. Heals are 20%.");
            }

            if (crab.revived) {
                if (now - crab.lastWave >= 8000) {
                    fireSeaWave(connection);
                    crab.lastWave = now;
                }
                const supportsDead = game.getGuardianBattleStates().stream()
                    .filter(g => !g.isBoss())
                    .allMatch(g => g.getCurrentHealth().get() < 1);
                if (supportsDead) {
                    announce(connection, "The attendants fall again.");
                    return PhaseUpdateResult.NEXT_PHASE;
                }
            }

            return PhaseUpdateResult.CONTINUE;
        } catch (e) {
            log.error("Script error in 2_crab_window.js:", e.message, e.stack || e);
            return PhaseUpdateResult.ERROR;
        }
    },
    end: function () {
        crab.finished = true;
    },
    phaseTime: function () {
        return Date.now() - crab.timeStarted;
    },
    playTime: function () {
        return 0;
    },
    hasEnded: function () {
        return crab.finished || (this.playTime() !== 0 && this.phaseTime() > this.playTime());
    },
    getGuardianAttackLoopTime: function (guardian) {
        return -1;
    },
    onHeal: function (target, healAmount, isGuardian) {
        if (isGuardian) {
            return game.getGuardianCombatSystem().heal(target, healAmount);
        }
        if (!crab.revived) {
            const current = game.getPlayerBattleStates().stream()
                .filter(p => p.getPosition() === target)
                .findFirst()
                .orElse(null);
            return current ? current.getCurrentHealth().get() : 0;
        }
        const reduced = Math.floor(healAmount * crab.healingMultiplier);
        return game.getPlayerCombatSystem().heal(target, reduced);
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
