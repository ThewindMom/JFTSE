var S2CMatchplayUseSkill = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill");
var S2CMatchplayDealDamage = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayDealDamage");
var S2CChatRoomAnswerPacket = Java.type("com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket");
var PhaseUpdateResult = Java.type("com.jftse.emulator.server.core.matchplay.guardian.PhaseUpdateResult");
var AtlantisV2Rules = Java.type("com.jftse.emulator.server.core.matchplay.guardian.AtlantisV2Rules");

// Live Atlantis act 2. Numbers come from AtlantisV2Rules — do not retune here.
var SEA_WAVE_PACKET_ID = AtlantisV2Rules.SEA_WAVE_PACKET_ID;
var WAVE_X = AtlantisV2Rules.WAVE_X;
var WAVE_Z = AtlantisV2Rules.WAVE_Z;
var WAVE_Y = AtlantisV2Rules.WAVE_Y;
var WAVE_GAP_MS = AtlantisV2Rules.WAVE_GAP_MS;
var BLIZZARD_VOLLEY = AtlantisV2Rules.BLIZZARD_VOLLEY_COUNT;
var CRAB_WINDOW_MS = AtlantisV2Rules.CRAB_WINDOW_MS;
var WAVE_ONLY_MS = AtlantisV2Rules.WAVE_ONLY_MS;
var CRAB_BLIZZARD_MS = AtlantisV2Rules.CRAB_BLIZZARD_MS;
var CRAB_WAVE_MS = AtlantisV2Rules.CRAB_WAVE_MS;
var DUMMY_ATTACKER = AtlantisV2Rules.DUMMY_ATTACKER;
var BLIZZARD_SKILL_ID = AtlantisV2Rules.BLIZZARD_SKILL_ID;
var REBIRTH_SKILL_ID = AtlantisV2Rules.REBIRTH_SKILL_ID;

class CrabWindow {
    constructor() {
        this.timeStarted = 0;
        this.finished = false;
        this.phaseStarted = false;
        this.blizzardVolleyFired = false;
        this.crabAnnounced = false;
        this.revived = false;
        this.healingMultiplier = AtlantisV2Rules.POST_REVIVE_HEAL_MULTIPLIER;
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
    const packet = new S2CMatchplayUseSkill(DUMMY_ATTACKER, 4, SEA_WAVE_PACKET_ID, seed, WAVE_X, WAVE_Z, WAVE_Y);
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
    const rebirth = skillService.findSkillById(REBIRTH_SKILL_ID);
    const boss = game.getGuardianBattleStates().stream().filter(g => g.isBoss()).findFirst().orElse(null);
    const pos = boss ? boss.getPosition() : 4;
    for (let g of game.getGuardianBattleStates()) {
        if (g.isBoss()) continue;
        g.getCurrentHealth().set(g.getMaxHealth());
        if (rebirth) {
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
        crab.timeStarted = AtlantisV2Rules.now();
        crab.phaseStarted = true;
        game.setPlayerSupportSkillsDisabled(true);
        crab.lastBlizzard = AtlantisV2Rules.now();
        crab.lastWave = AtlantisV2Rules.now();
    },
    update: function (connection) {
        if (!crab.phaseStarted || this.hasEnded()) return PhaseUpdateResult.CONTINUE;
        try {
            const now = AtlantisV2Rules.now();
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
                if (now - crab.lastBlizzard >= CRAB_BLIZZARD_MS) {
                    const blizzard = skillService.findSkillById(BLIZZARD_SKILL_ID);
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

            if (crab.waveOnly && !crab.revived && now - crab.lastWave >= CRAB_WAVE_MS) {
                fireSeaWave(connection);
                crab.lastWave = now;
            }

            if (!crab.revived && elapsed >= WAVE_ONLY_MS + CRAB_WINDOW_MS) {
                crab.revived = true;
                reviveAddsFull(connection);
                game.setPlayerHealSkillsDisabled(false);
                game.setPlayerShieldSkillsDisabled(true);
                announce(connection, "The attendants return at full strength. Heals are 20%.");
            }

            if (crab.revived) {
                if (now - crab.lastWave >= CRAB_WAVE_MS) {
                    fireSeaWave(connection);
                    crab.lastWave = now;
                }
                const supportsDead = game.getGuardianBattleStates().stream()
                    .filter(g => !g.isBoss())
                    .allMatch(g => g.getCurrentHealth().get() < 1);
                if (supportsDead) {
                    game.setPlayerHealSkillsDisabled(false);
                    game.setPlayerShieldSkillsDisabled(true);
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
        game.setPlayerHealSkillsDisabled(false);
        game.setPlayerShieldSkillsDisabled(true);
    },
    phaseTime: function () {
        return AtlantisV2Rules.now() - crab.timeStarted;
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
        const reduced = Math.round(healAmount * crab.healingMultiplier);
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
