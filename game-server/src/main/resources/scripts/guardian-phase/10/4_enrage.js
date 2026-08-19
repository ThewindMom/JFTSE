var S2CMatchplayUseSkill = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill");
var S2CMatchplayDealDamage = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayDealDamage");
var S2CChatRoomAnswerPacket = Java.type("com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket");
var PhaseUpdateResult = Java.type("com.jftse.emulator.server.core.matchplay.guardian.PhaseUpdateResult");
var AtlantisV2Rules = Java.type("com.jftse.emulator.server.core.matchplay.guardian.AtlantisV2Rules");

// Live Atlantis act 4. Numbers come from AtlantisV2Rules — do not retune here.
var SEA_WAVE_PACKET_ID = AtlantisV2Rules.SEA_WAVE_PACKET_ID;
var WAVE_X = AtlantisV2Rules.WAVE_X;
var WAVE_Z = AtlantisV2Rules.WAVE_Z;
var WAVE_Y = AtlantisV2Rules.WAVE_Y;
var STUN_MS = AtlantisV2Rules.STUN_MS;
var ENRAGE_WAVE_MS = AtlantisV2Rules.ENRAGE_WAVE_MS;
var ENRAGE_BLIZZARD_MS = AtlantisV2Rules.ENRAGE_BLIZZARD_MS;
var DUMMY_ATTACKER = AtlantisV2Rules.DUMMY_ATTACKER;
var BLIZZARD_SKILL_ID = AtlantisV2Rules.BLIZZARD_SKILL_ID;
var REBIRTH_SKILL_ID = AtlantisV2Rules.REBIRTH_SKILL_ID;

class Enrage {
    constructor() {
        this.timeStarted = 0;
        this.finished = false;
        this.phaseStarted = false;
        this.revived = false;
        this.enraged = false;
        this.lastWave = 0;
        this.lastBlizzard = 0;
    }
}

let rage = new Enrage();

function announce(connection, text) {
    const packet = new S2CChatRoomAnswerPacket(2, "Server", text);
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function fireSeaWave(connection) {
    const seed = Math.floor(Math.random() * 127);
    const packet = new S2CMatchplayUseSkill(DUMMY_ATTACKER, 4, SEA_WAVE_PACKET_ID, seed, WAVE_X, WAVE_Z, WAVE_Y);
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
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

function addsDead() {
    return game.getGuardianBattleStates().stream()
        .filter(g => !g.isBoss())
        .allMatch(g => g.getCurrentHealth().get() < 1);
}

var phase = {
    getPhaseName: function () {
        return "Abyssal Enrage";
    },
    start: function () {
        rage.timeStarted = AtlantisV2Rules.now();
        rage.phaseStarted = true;
        game.setPlayerSupportSkillsDisabled(false);
    },
    update: function (connection) {
        if (!rage.phaseStarted || this.hasEnded()) return PhaseUpdateResult.CONTINUE;
        try {
            const now = AtlantisV2Rules.now();
            const elapsed = now - rage.timeStarted;
            const boss = game.getGuardianBattleStates().stream().filter(g => g.isBoss()).findFirst().orElse(null);
            if (!boss) return PhaseUpdateResult.ERROR;
            const skillService = serviceManager.getSkillService();

            if (elapsed < STUN_MS) {
                if (!rage.revived) {
                    rage.revived = true;
                    reviveAddsFull(connection);
                    announce(connection, "A brief calm. Shields and heals work. The boss is stunned.");
                }
                return PhaseUpdateResult.CONTINUE;
            }

            if (rage.revived && !rage.enraged && addsDead()) {
                rage.enraged = true;
                game.setPlayerSupportSkillsDisabled(true);
                rage.lastWave = 0;
                rage.lastBlizzard = now;
                announce(connection, "Enrage. Waves and blizzard, no shields, no heals. Kill the boss.");
            }

            if (rage.enraged) {
                if (now - rage.lastWave >= ENRAGE_WAVE_MS) {
                    fireSeaWave(connection);
                    rage.lastWave = now;
                }
                if (now - rage.lastBlizzard >= ENRAGE_BLIZZARD_MS) {
                    const blizzard = skillService.findSkillById(BLIZZARD_SKILL_ID);
                    if (blizzard) {
                        const packet = new S2CMatchplayUseSkill(boss.getPosition(), 4, blizzard.getId() - 1, Math.floor(Math.random() * 127), 0, 0, 0);
                        gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
                    }
                    rage.lastBlizzard = now;
                }
            }

            const allDead = game.getGuardianBattleStates().stream()
                .allMatch(g => g.getCurrentHealth().get() < 1);
            if (allDead) {
                announce(connection, "The deep is still.");
                return PhaseUpdateResult.NEXT_PHASE;
            }

            return PhaseUpdateResult.CONTINUE;
        } catch (e) {
            log.error("Script error in 4_enrage.js:", e.message, e.stack || e);
            return PhaseUpdateResult.ERROR;
        }
    },
    end: function () {
        rage.finished = true;
        game.setPlayerSupportSkillsDisabled(false);
    },
    phaseTime: function () {
        return AtlantisV2Rules.now() - rage.timeStarted;
    },
    playTime: function () {
        return 0;
    },
    hasEnded: function () {
        return rage.finished || (this.playTime() !== 0 && this.phaseTime() > this.playTime());
    },
    getGuardianAttackLoopTime: function (guardian) {
        return -1;
    },
    onHeal: function (target, healAmount, isGuardian) {
        if (isGuardian) {
            return game.getGuardianCombatSystem().heal(target, healAmount);
        }
        if (rage.enraged) {
            const current = game.getPlayerBattleStates().stream()
                .filter(p => p.getPosition() === target)
                .findFirst()
                .orElse(null);
            return current ? current.getCurrentHealth().get() : 0;
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
