var S2CMatchplayUseSkill = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill");
var S2CChatRoomAnswerPacket = Java.type("com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket");
var PhaseUpdateResult = Java.type("com.jftse.emulator.server.core.matchplay.guardian.PhaseUpdateResult");

// Atlantis V2 act 1 — Thewind 18 Aug 01:53 PT draft, tuned so Testmon does not melt.
// 5 then 10 LTR SeaWaves, dummy attacker 4, xyz=(-200,0,0). Stand on green pads.
// Draft 5/10 counts kept; WAVE_GAP_MS is 2500 (draft did not specify intra-volley spacing).
var SEA_WAVE_PACKET_ID = 27;
var WAVE_X = -200;
var WAVE_Z = 0;
var WAVE_Y = 0;
var WAVE_GAP_MS = 2500;
var FIRST_VOLLEY = 5;
var SECOND_VOLLEY = 10;
var STRIP_GUARDIAN_MS = 30000;
var STRIP_PLAYER_MS = 35000;
var FIRST_VOLLEY_MS = 40000;
var SECOND_VOLLEY_MS = 55000;
var RESTORE_MS = 85000;

class GreenTide {
    constructor() {
        this.timeStarted = 0;
        this.finished = false;
        this.phaseStarted = false;
        this.isBossImmune = true;
        this.guardianSpellsOn = true;
        this.playerSupportOn = true;
        this.firstVolleyFired = false;
        this.secondVolleyFired = false;
        this.guardianSkillTimers = new Map();
        this.guardianAggroState = new Map();
        this.skillInterval = 10000;
        this.aggroSkillInterval = 5000;
    }
}

let tide = new GreenTide();

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
    announce(connection, label + " — " + count + " LTR waves. Stand in the green circles.");
    const eventHandler = gameManager.getEventHandler();
    for (let i = 0; i < count; i++) {
        const delay = 250 + i * WAVE_GAP_MS;
        const n = i + 1;
        const runnableEvent = eventHandler.createRunnableEvent(function () {
            const session = connection.getClient() && connection.getClient().getActiveGameSession();
            if (!session) {
                return;
            }
            announce(connection, label + " " + n + "/" + count);
            fireSeaWave(connection);
        }, delay);
        eventHandler.offerJS(runnableEvent);
    }
}

var phase = {
    getPhaseName: function () {
        return "Green Tide";
    },
    start: function () {
        tide.timeStarted = Date.now();
        tide.phaseStarted = true;
        game.setPlayerSupportSkillsDisabled(false);

        const guardians = game.getGuardianBattleStates();
        let delayOffset = 0;
        for (let g of guardians) {
            g.getSkills().clear();
            if (!g.isBoss()) {
                tide.guardianSkillTimers.set(g.getPosition(), Date.now() + delayOffset);
                delayOffset += 3750;
                tide.guardianAggroState.set(g.getPosition(), false);
            }
        }
    },
    update: function (connection) {
        if (!tide.phaseStarted || this.hasEnded()) return PhaseUpdateResult.CONTINUE;

        const now = Date.now();
        const elapsed = now - tide.timeStarted;
        const guardians = game.getGuardianBattleStates();
        const boss = guardians.stream().filter(g => g.isBoss()).findFirst().orElse(null);
        if (!boss) return PhaseUpdateResult.ERROR;

        if (tide.guardianSpellsOn && elapsed >= STRIP_GUARDIAN_MS) {
            tide.guardianSpellsOn = false;
            announce(connection, "The temple's voices fall silent.");
        }

        if (tide.playerSupportOn && elapsed >= STRIP_PLAYER_MS) {
            tide.playerSupportOn = false;
            game.setPlayerSupportSkillsDisabled(true);
            announce(connection, "Shields and heals will not answer. Get to the green pads.");
        }

        if (!tide.firstVolleyFired && elapsed >= FIRST_VOLLEY_MS) {
            tide.firstVolleyFired = true;
            fireVolley(connection, FIRST_VOLLEY, "Tide 1");
        }

        if (!tide.secondVolleyFired && elapsed >= SECOND_VOLLEY_MS) {
            tide.secondVolleyFired = true;
            fireVolley(connection, SECOND_VOLLEY, "Tide 2");
        }

        if (!tide.playerSupportOn && elapsed >= RESTORE_MS) {
            tide.playerSupportOn = true;
            tide.guardianSpellsOn = true;
            game.setPlayerSupportSkillsDisabled(false);
            announce(connection, "The current eases. Shields and heals return.");
        }

        if (tide.guardianSpellsOn) {
            for (let g of guardians) {
                if (g.isBoss() || g.getCurrentHealth().get() < 1) continue;
                const position = g.getPosition();
                const lastCast = tide.guardianSkillTimers.get(position) || 0;
                const isAggro = tide.guardianAggroState.get(position);
                const interval = isAggro ? tide.aggroSkillInterval : tide.skillInterval;
                if (now - lastCast >= interval) {
                    this.castGuardianSkill(g, isAggro, connection);
                    tide.guardianSkillTimers.set(position, now);
                }
            }

            const aliveGuards = guardians.stream()
                .filter(g => !g.isBoss() && g.getCurrentHealth().get() > 0)
                .toArray();
            if (aliveGuards.length === 1) {
                const soloPos = aliveGuards[0].getPosition();
                if (!tide.guardianAggroState.get(soloPos)) {
                    tide.guardianAggroState.set(soloPos, true);
                }
            }
        }

        const supportsDead = guardians.stream()
            .filter(g => !g.isBoss())
            .allMatch(g => g.getCurrentHealth().get() < 1);

        if (supportsDead && elapsed >= RESTORE_MS) {
            announce(connection, "Both attendants fall. The deep stirs...");
            return PhaseUpdateResult.NEXT_PHASE;
        }

        return PhaseUpdateResult.CONTINUE;
    },
    castGuardianSkill: function (guardian, isAggro, connection) {
        const pos = guardian.getPosition();
        const skillService = serviceManager.getSkillService();
        const players = game.getPlayerBattleStates().stream()
            .filter(p => p != null && p.getPosition() < 4 && p.getCurrentHealth().get() > 0)
            .toArray();
        if (players.length === 0) return;

        const useSilence = Math.random() < 0.4;
        const skill = skillService.findSkillById(useSilence ? 57 : 7);
        if (skill) {
            let target = players[Math.floor(Math.random() * players.length)];
            let packet = new S2CMatchplayUseSkill(
                pos, target.getPosition(), skill.getId() - 1,
                Math.floor(Math.random() * 127), 0, 0, 0
            );
            gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
        }

        if (isAggro) {
            const homingSkill = skillService.findSkillById(6);
            if (homingSkill) {
                let target = players[Math.floor(Math.random() * players.length)];
                let packet = new S2CMatchplayUseSkill(
                    pos, target.getPosition(), homingSkill.getId() - 1,
                    Math.floor(Math.random() * 127), 0, 0, 0
                );
                const event = eventHandler.createRunnableEvent(function () {
                    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
                }, 1250);
                eventHandler.offerJS(event);
            }
        }
    },
    end: function () {
        tide.finished = true;
        game.setPlayerSupportSkillsDisabled(false);
    },
    phaseTime: function () {
        return Date.now() - tide.timeStarted;
    },
    playTime: function () {
        return 0;
    },
    hasEnded: function () {
        return tide.finished || (this.playTime() !== 0 && this.phaseTime() > this.playTime());
    },
    getGuardianAttackLoopTime: function (guardian) {
        return -1;
    },
    onHeal: function (target, healAmount, isGuardian) {
        if (isGuardian) {
            return game.getGuardianCombatSystem().heal(target, healAmount);
        }
        if (!tide.playerSupportOn) {
            const current = game.getPlayerBattleStates().stream()
                .filter(p => p.getPosition() === target)
                .findFirst()
                .orElse(null);
            return current ? current.getCurrentHealth().get() : 0;
        }
        return game.getPlayerCombatSystem().heal(target, healAmount);
    },
    onDealDamage: function (attackingPlayer, targetGuardian, damage, hasAttackerDmgBuff, hasTargetDefBuff, skill) {
        const target = game.getGuardianBattleStateByPosition(targetGuardian);
        if (target?.isBoss() && tide.isBossImmune) {
            return target.getCurrentHealth().get();
        }
        return game.getGuardianCombatSystem().dealDamage(attackingPlayer, targetGuardian, damage, hasAttackerDmgBuff, hasTargetDefBuff, skill);
    },
    onDealDamageToPlayer: function (attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill) {
        return game.getGuardianCombatSystem().dealDamageToPlayer(attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill);
    },
    onDealDamageOnBallLoss: function (attackerPos, targetPos, hasAttackerWillBuff) {
        const target = game.getGuardianBattleStateByPosition(targetPos);
        if (target?.isBoss() && tide.isBossImmune) {
            return target.getCurrentHealth().get();
        }
        return game.getGuardianCombatSystem().dealDamageOnBallLoss(attackerPos, targetPos, hasAttackerWillBuff);
    },
    onDealDamageOnBallLossToPlayer: function (attackerPos, targetPos, hasAttackerWillBuff) {
        return game.getGuardianCombatSystem().dealDamageOnBallLossToPlayer(attackerPos, targetPos, hasAttackerWillBuff);
    }
}
